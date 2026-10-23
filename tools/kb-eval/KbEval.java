import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moyz.adi.common.cosntant.AdiConstant;
import com.moyz.adi.common.rag.DocumentSplitterFactory;
import com.moyz.adi.common.rag.TokenEstimatorFactory;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.output.Response;

import java.io.BufferedWriter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * RAG chunking / retrieval baseline harness.
 *
 * Splitting uses the production DocumentSplitterFactory + TokenEstimatorFactory, so a config here
 * means exactly what the same values mean in a knowledge base row. Retrieval is exact cosine over
 * the embeddings of every chunk of the whole corpus (36 docs = one knowledge base) -- that is what
 * pgvector does for a corpus this size. Hybrid search and rerank are deliberately out of scope:
 * this harness measures chunking, not the rerank model.
 *
 * Offsets are tracked on whitespace-stripped text, because langchain4j's paragraph/line/sentence
 * splitters rebuild chunks without their original newlines -- a chunk is not a literal substring of
 * the source, but it is a contiguous substring of the whitespace-stripped source. Coverage is then
 * range intersection, so it stays exact instead of degrading to guessing.
 *
 * Metrics
 *   intact        : some chunk can hold the whole gold span -> the model can ever see it complete
 *   recall@K      : some chunk in the top K covers >= 80% of the gold span
 *   recall@K s.   : some chunk in the top K covers the gold span completely
 *   mrr@10        : reciprocal rank of the first chunk covering >= 80% of the gold span
 *   overLimit     : chunks whose estimated tokens exceed the configured segment size
 *
 *   answered@K    : the context handed to the LLM contains every fact the question needs.
 *   needCov@K     : mean fraction of those facts present (partial credit).
 * recall@K only asks whether the chunk holding the answer reached the model. When a question needs
 * two facts that live in two blocks -- "what happens if I re-split a parent segment" needs both the
 * table row and the warning below it -- a single span can be covered while the answer is still
 * incomplete. Each golden entry may carry `need`: a list of groups, each group a list of
 * alternative literal spans, all groups required. Entries without `need` fall back to `[[gold]]`,
 * so answered@K degenerates to span coverage for single-fact questions and stays comparable.
 * Every alternative is validated against the corpus, so a typo fails loudly.
 *
 * Neighbour-window mode (optional "w=0,1,2" token, see usage) models what the retriever now does
 * after ranking: every kept hit is widened by +-w segments of the same document, and the widened
 * spans are de-duplicated. recall@K / mrr@10 keep their definition but are measured on the widened
 * hit (so w=0 reproduces the historical numbers exactly); the ctx* pair describes the de-duplicated
 * union, which is literally the text the LLM receives.
 *   ctxRecall@K       : some context handed to the LLM covers >= 80% of the gold span
 *   ctxChars@5 / xCost: characters of that context (top-5) and its ratio to the five raw hits
 *
 * Usage:
 *   KbEval <corpusDir> <goldenFile> <outPrefix> <baseUrl> <apiKey> <model> <dims> [w=..] [q=..] <cfg...>
 *   cfg = name:strategy:size:overlap:separator   (separator may be empty)
 *   Both modifier tokens are optional and order-independent:
 *     w=0,1,2   neighbour-window radii; omitting it keeps the pre-window behaviour (w=0 only)
 *     q=off,mq,hyde,hyde+q   query-side rewrite modes (needs QW_MODEL / QW_BASE_URL / QW_API_KEY)
 *   Env: NEED_DIAG=1 per-chunk need coverage · SELFTEST=1 metric self-check
 *        QW_MODEL / QW_N / QW_TEMP / QW_PROMPT_V / QW_BASE_URL / QW_API_KEY shape the rewriting
 *        (QW_PROMPT_V must be bumped whenever the prompt changes, or the cache lies)
 */
public class KbEval {

    static final ObjectMapper M = new ObjectMapper();
    static final Map<String, float[]> CACHE = new HashMap<>();
    static Path cacheFile;
    static final int TOPK = 10;
    /** Reciprocal Rank Fusion constant; 60 is the value the literature and the hybrid path use */
    static final int RRF_K = 60;
    static final double HIT = 0.80;
    static final double MAX_UNLOCATABLE = 0.02;
    /** neighbour-window radii to evaluate; {0} keeps the historical single-chunk behaviour */
    static int[] WINDOWS = {0};
    /** NEED_DIAG=1: for every multi-fact question, show how many required facts each retrieved
     *  hit holds on its own. Distinguishes "one chunk covers everything" from "the union saves it",
     *  and proves whether answered@K can diverge from recall@K at all for this corpus. */
    static final boolean NEED_DIAG = "1".equals(System.getenv("NEED_DIAG"));
    /**
     * RANKDIAG=1: audit the ranking itself -- where the answer actually sits in cosine order.
     * <p>
     * Every "should we add a ranker / a rewrite / a bigger pool" decision is bounded by two numbers
     * this printout supplies and a recall@5 alone hides:
     * <ul>
     *   <li><b>the pool ceiling</b>: rerank can only reorder what was retrieved, so with pool P the
     *       best recall@5 any perfect reranker can reach is recall@P -- if the answer is ranked 8th
     *       and the knowledge base retrieves 3, no ranker on earth helps.</li>
     *   <li><b>the min-score bite</b>: production drops hits below {@code retrieve_min_score}
     *       (default 0.6). A gold whose best hit scores under that threshold is unreachable
     *       regardless of pool size, and that is a retrieval-threshold bug, not a ranking one.</li>
     * </ul>
     * Measured on the plain cosine ranking (mode=off, w=0) so the numbers describe the retriever
     * rather than any experimental add-on.
     */
    static final boolean RANKDIAG = "1".equals(System.getenv("RANKDIAG"));

    // ---- query-side rewriting ----------------------------------------------------------------
    // Retrieval today embeds the user's question verbatim, so the ranking is only as good as the
    // lexical luck of that one phrasing. Two classic remedies, both measured here before any of
    // them is built into the product:
    //   mq      ask an LLM for N paraphrases, retrieve for each, keep each chunk's best score
    //   hyde    ask an LLM for a hypothetical answer passage and match documents against that
    //   hyde+q  HyDE, but the original query keeps its vote
    // Rewrites are cached on disk, so a rerun costs nothing; "off" reproduces the historical ranking.

    /** modes to sweep; comma-separated in the q= arg, "off" first for a same-run baseline */
    static String[] QW_MODES = {"off"};
    static int QW_N = Integer.parseInt(System.getenv().getOrDefault("QW_N", "3"));
    static final String QW_MODEL = System.getenv().getOrDefault("QW_MODEL", "doubao-seed-2.0-lite");
    /**
     * Prompt versions, folded into the cache key per prompt family. Without a version, editing a
     * prompt silently reuses the rewrites the *old* prompt produced -- the run looks healthy and
     * measures nothing. Versioning per family (not globally) keeps a paraphrase-prompt fix from
     * throwing away the HyDE rewrites as well. Bump the matching constant on every prompt edit.
     */
    static final String MQ_PV = "2";    // v1 asked for "same meaning, other wording" -> synonyms only
    static final String HYDE_PV = "1";
    static ChatModel CHAT;
    static Path rewriteCacheFile;
    static final Map<String, List<String>> RCACHE = new HashMap<>();
    static int llmNewCalls = 0, llmCacheHits = 0;
    static long llmPromptChars = 0, llmOutChars = 0;

    record Cfg(String name, String strategy, int size, int overlap, String sep) {
        String desc() {
            return strategy + "(size=" + size + ",overlap=" + overlap
                    + (sep == null || sep.isEmpty() ? "" : ",sep=\"" + sep + "\"") + ")";
        }
    }

    static class Chunk {
        int gid;          // position in the flat corpus list, used as identity when de-duplicating windows
        int idx, start, end;
        String doc, text, norm;
        float[] vec;
    }

    static class Gold {
        String id, doc, query, span;
        int start, end, normLen;
        /** required facts; each group is satisfied by any of its alternatives (all normalised) */
        List<List<String>> need;
        /**
         * Docs that actually contain this gold's span (and every {@code need} alternative). A RAG
         * answer living in ANY of these docs is a valid hit -- the corpus repeats the same fact in
         * several guides, so pinning the metric to one doc would under-count retrieval that returns
         * the identical text from a sibling doc. Null means "unresolved" and falls back to the
         * single {@code doc} (keeps the synthetic self-test data behaving as before).
         */
        Set<String> goldDocs;
    }

    /** True when {@code c} lives in a doc that legitimately contains this gold's answer. */
    static boolean sameGoldDoc(Gold g, Chunk c) {
        return g.goldDocs != null ? g.goldDocs.contains(c.doc) : c.doc.equals(g.doc);
    }

    /** Collect every doc (from the full corpus) whose normalised text contains the given span. */
    static Set<String> collectGoldDocs(Gold g, List<String> docNames, List<String> docNorm) {
        Set<String> docs = new HashSet<>();
        addSpanDocs(docs, g.span, docNames, docNorm);
        for (List<String> grp : g.need) for (String alt : grp) addSpanDocs(docs, alt, docNames, docNorm);
        return docs;
    }

    static void addSpanDocs(Set<String> docs, String span, List<String> docNames, List<String> docNorm) {
        String key = norm(span);
        if (key.isEmpty()) return;
        for (int i = 0; i < docNorm.size(); i++) {
            if (docNorm.get(i).contains(key)) docs.add(docNames.get(i));
        }
    }

    public static void main(String[] args) throws Exception {
        if ("1".equals(System.getenv("SELFTEST"))) { selfTest(); return; }
        Path corpusDir = Path.of(args[0]);
        String outPrefix = args[2];
        cacheFile = Path.of(outPrefix + "-emb-cache.tsv");
        loadCache();
        EmbeddingModel model = OpenAiEmbeddingModel.builder()
                .baseUrl(args[3]).apiKey(args[4]).modelName(args[5])
                .dimensions(Integer.parseInt(args[6])).build();

        List<Path> files;
        try (Stream<Path> s = Files.walk(corpusDir)) {
            files = s.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".md"))
                    .sorted().collect(Collectors.toList());
        }
        if (files.isEmpty()) throw new IllegalStateException("corpus is empty: " + corpusDir);

        List<String> docNames = new ArrayList<>();
        List<String> docNorm = new ArrayList<>();
        for (Path f : files) {
            docNames.add(corpusDir.relativize(f).toString().replace('\\', '/'));
            docNorm.add(norm(Files.readString(f, StandardCharsets.UTF_8)));
        }
        System.out.println("[corpus] docs=" + docNames.size() + " chars="
                + docNorm.stream().mapToInt(String::length).sum() + " (whitespace stripped)");

        // ---- golden set: every span must be locatable exactly once, otherwise the benchmark lies ----
        List<Gold> golds = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        for (String line : Files.readAllLines(Path.of(args[1]), StandardCharsets.UTF_8)) {
            if (line.isBlank()) continue;
            JsonNode n = M.readTree(line);
            Gold g = new Gold();
            g.id = n.get("id").asText();
            g.doc = n.get("doc").asText();
            g.query = n.get("query").asText();
            g.span = n.get("gold").asText();
            int di = docNames.indexOf(g.doc);
            if (di < 0) { problems.add(g.id + ": unknown doc " + g.doc); continue; }
            String key = norm(g.span);
            String text = docNorm.get(di);
            int first = text.indexOf(key);
            if (first < 0) { problems.add(g.id + ": span NOT FOUND in " + g.doc); continue; }
            if (text.indexOf(key, first + 1) >= 0) {
                problems.add(g.id + ": span is ambiguous (appears " + count(text, key) + "x) in " + g.doc);
                continue;
            }
            g.start = first;
            g.end = first + key.length();
            g.normLen = key.length();

            // `need`: the facts the question actually requires. Absent -> the gold span alone, so a
            // single-fact entry keeps its historical behaviour. Alternatives are validated against
            // the document, because an unlocatable one would silently mark a question unanswerable.
            g.need = new ArrayList<>();
            JsonNode needNode = n.get("need");
            boolean needOk = true;
            if (needNode == null || !needNode.isArray() || needNode.isEmpty()) {
                g.need.add(List.of(key));
            } else {
                for (JsonNode group : needNode) {
                    List<String> alts = new ArrayList<>();
                    for (JsonNode alt : group) {
                        String a = norm(alt.asText());
                        if (a.isEmpty()) {
                            problems.add(g.id + ": empty need alternative");
                            needOk = false;
                        } else if (!text.contains(a)) {
                            problems.add(g.id + ": need alternative not in " + g.doc + " -> " + alt.asText());
                            needOk = false;
                        } else {
                            alts.add(a);
                        }
                    }
                    if (alts.isEmpty()) {
                        problems.add(g.id + ": need group left with no usable alternative");
                        needOk = false;
                    }
                    g.need.add(alts);
                }
            }
            if (!needOk) continue;
            g.goldDocs = collectGoldDocs(g, docNames, docNorm);
            golds.add(g);
        }
        if (!problems.isEmpty()) {
            System.out.println("== GOLDEN SET INVALID (fix before trusting any number) ==");
            problems.forEach(p -> System.out.println("   " + p));
            throw new IllegalStateException(problems.size() + " golden entries are broken");
        }
        System.out.println("[golden] queries=" + golds.size() + " all spans located uniquely"
                + " (multi-fact=" + golds.stream().filter(g -> g.need.size() > 1).count() + ")");

        List<Cfg> cfgs = new ArrayList<>();
        int firstCfg = 7;
        while (firstCfg < args.length
                && (args[firstCfg].startsWith("w=") || args[firstCfg].startsWith("q="))) {
            if (args[firstCfg].startsWith("w=")) {
                WINDOWS = Arrays.stream(args[firstCfg].substring(2).split(","))
                        .mapToInt(s -> Integer.parseInt(s.trim())).toArray();
            } else {
                QW_MODES = Arrays.stream(args[firstCfg].substring(2).split(","))
                        .map(String::trim).filter(s -> !s.isEmpty()).toArray(String[]::new);
            }
            firstCfg++;
        }
        for (int i = firstCfg; i < args.length; i++) {
            String[] p = args[i].split(":", 5);
            cfgs.add(new Cfg(p[0], p[1], Integer.parseInt(p[2]), Integer.parseInt(p[3]),
                    p.length > 4 ? p[4] : ""));
        }
        TokenCountEstimator estimator = TokenEstimatorFactory.create(AdiConstant.TokenEstimator.OPENAI);

        boolean anyRewrite = Arrays.stream(QW_MODES).anyMatch(m -> !"off".equals(m));
        if (anyRewrite) {
            String qwBase = System.getenv().getOrDefault("QW_BASE_URL", args[3]);
            String qwKey = System.getenv().getOrDefault("QW_API_KEY", args[4]);
            CHAT = OpenAiChatModel.builder()
                    .baseUrl(qwBase).apiKey(qwKey).modelName(QW_MODEL)
                    .temperature(Double.parseDouble(System.getenv().getOrDefault("QW_TEMP", "0.2")))
                    .build();
            rewriteCacheFile = Path.of(outPrefix + "-rewrite-cache.tsv");
            loadRewriteCache();
            System.out.println("[rewrite] modes=" + String.join(",", QW_MODES)
                    + " model=" + QW_MODEL + " n=" + QW_N);
        }

        List<String> summary = new ArrayList<>();
        for (Cfg cfg : cfgs) {
            summary.addAll(runCfg(cfg, docNames, docNorm, golds, estimator, model, outPrefix));
        }
        saveCache();
        System.out.println("[embed-cache] entries=" + CACHE.size() + " -> " + cacheFile.toAbsolutePath());
        if (anyRewrite) {
            saveRewriteCache();
            System.out.println("[rewrite] llm calls: new=" + llmNewCalls + " cached=" + llmCacheHits
                    + " promptChars=" + llmPromptChars + " outChars=" + llmOutChars);
        }

        System.out.println();
        System.out.println("== SUMMARY ==");
        System.out.printf("%-22s %6s %8s %9s %9s %8s %9s %10s %8s %8s %9s %11s %10s %10s %10s %6s%n",
                "config", "chunks", "avgChar", "avgToken", "overLimit", "intact",
                "recall@5", "recall@10", "mrr@10", "ans@5", "ans@10", "needCov@5",
                "ctxRec@5", "ctxRec@10", "ctxChars@5", "xCost");
        summary.forEach(System.out::println);
    }

    static List<String> runCfg(Cfg cfg, List<String> docNames, List<String> docNorm, List<Gold> golds,
                               TokenCountEstimator estimator, EmbeddingModel model, String outPrefix) throws Exception {
        DocumentSplitter splitter = DocumentSplitterFactory.create(
                cfg.strategy(), cfg.size(), cfg.overlap(), cfg.sep(), estimator);

        List<Chunk> chunks = new ArrayList<>();
        int unlocatable = 0, overLimit = 0;
        long chars = 0, tokens = 0;
        for (int d = 0; d < docNorm.size(); d++) {
            String text = docNorm.get(d);
            List<TextSegment> segs = splitter.split(Document.from(text));
            int cursor = 0, idx = 0;
            for (TextSegment seg : segs) {
                Chunk c = new Chunk();
                c.doc = docNames.get(d);
                c.idx = idx++;
                c.text = seg.text();
                c.norm = norm(c.text);
                chars += c.norm.length();
                int tk = estimator.estimateTokenCountInText(c.text);
                tokens += tk;
                if (tk > cfg.size()) overLimit++;
                int found = text.indexOf(c.norm, cursor);
                if (found < 0) found = text.indexOf(c.norm);
                if (found < 0) {
                    unlocatable++;
                    c.start = -1;
                    c.end = -1;
                } else {
                    c.start = found;
                    c.end = found + c.norm.length();
                    cursor = c.start + 1;
                }
                c.gid = chunks.size();
                chunks.add(c);
            }
        }
        if (unlocatable > Math.max(1, (int) (chunks.size() * MAX_UNLOCATABLE))) {
            throw new IllegalStateException(cfg.name() + ": " + unlocatable + "/" + chunks.size()
                    + " chunks could not be located in the source; coverage would be unreliable");
        }
        embedAll(model, chunks);

        int n = golds.size();
        Map<String, List<Chunk>> byDoc = new HashMap<>();
        for (Chunk c : chunks) byDoc.computeIfAbsent(c.doc, k -> new ArrayList<>()).add(c);
        for (List<Chunk> l : byDoc.values()) l.sort(Comparator.comparingInt(c -> c.idx));

        List<String> summary = new ArrayList<>();
        for (String mode : QW_MODES) {
        for (int win : WINDOWS) {
            String tag = cfg.name() + (win == 0 ? "" : "|w" + win)
                    + ("off".equals(mode) ? "" : "|" + mode);
            int intact = 0, r5 = 0, r10 = 0, r10s = 0, u5n = 0, u10n = 0, ans5 = 0, ans10 = 0;
            double mrr = 0, ctxChars5 = 0, rawChars5 = 0, needCov5 = 0;
            List<Gold> cut = new ArrayList<>();
            List<String> noAns = new ArrayList<>();
            List<String> crossDoc = new ArrayList<>();
            List<ObjectNode> rows = new ArrayList<>();

            for (Gold g : golds) {
                // holdable whole by one widened hit: the cross-boundary span becomes readable
                boolean isIntact = chunks.stream().anyMatch(c -> unionCoverage(g, windowOf(c, byDoc, win)) >= 1.0);
                if (isIntact) intact++; else cut.add(g);

                float[] qv = embed(model, g.query);
                List<Chunk> ranked = rankFor(mode, g.query, qv, chunks, model);

                // (a) per-hit view: best coverage any single widened hit achieves.
                // At w=0 this is byte-for-byte the historical metric, so old numbers stay comparable.
                double cov5 = 0, cov10 = 0;
                int rank = -1;
                for (int i = 0; i < ranked.size(); i++) {
                    double cov = unionCoverage(g, windowOf(ranked.get(i), byDoc, win));
                    if (i < 5) cov5 = Math.max(cov5, cov);
                    cov10 = Math.max(cov10, cov);
                    if (rank < 0 && cov >= HIT) rank = i + 1;
                }
                // (b) context view: coverage of the de-duplicated union -- what the LLM really receives.
                List<Chunk> ctx5 = widen(ranked, byDoc, win, 5);
                List<Chunk> ctx10 = widen(ranked, byDoc, win, 10);
                double u5 = unionCoverage(g, ctx5);
                double u10 = unionCoverage(g, ctx10);

                // (c) answer view: every fact the question needs, present in the handed-over text.
                String ctx5Text = merged(ctx5);
                String ctx10Text = merged(ctx10);
                double n5 = needCoverage(g, ctx5Text);
                double n10 = needCoverage(g, ctx10Text);
                String src5 = needSources(g, ctx5);
                if (src5.contains("O")) crossDoc.add(g.id);
                if (n5 >= 1.0) ans5++;
                if (n10 >= 1.0) ans10++;
                needCov5 += n5;
                if (n5 < 1.0) {
                    noAns.add(g.id + " [" + g.doc + "] missing#" + missingGroups(g, ctx5Text)
                            + "  " + brief(g.query));
                }
                if (NEED_DIAG && g.need.size() > 1) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < ranked.size(); i++) {
                        double per = needCoverage(g, merged(windowOf(ranked.get(i), byDoc, win)));
                        if (per > 0) sb.append(" r").append(i + 1).append('=').append(fmt(per));
                    }
                    System.out.println("  DIAG " + tag + " " + g.id
                            + " groups=" + g.need.size() + " union@5=" + fmt(n5)
                            + " union@10=" + fmt(n10) + " per-hit:" + sb);
                }

                if (cov5 >= HIT) r5++;
                if (cov10 >= HIT) r10++;
                if (cov10 >= 1.0) r10s++;
                if (u5 >= HIT) u5n++;
                if (u10 >= HIT) u10n++;
                if (rank > 0) mrr += 1.0 / rank;

                ctxChars5 += ctx5.stream().mapToInt(c -> c.norm.length()).sum();
                rawChars5 += ranked.stream().limit(5).mapToInt(c -> c.norm.length()).sum();

                ObjectNode row = M.createObjectNode();
                row.put("cfg", tag);
                row.put("qmode", mode);
                row.put("w", win);
                row.put("id", g.id);
                row.put("query", g.query);
                row.put("gold_doc", g.doc);
                row.put("intact", isIntact);
                row.put("rank", rank);
                row.put("best_cov@10", round(cov10));
                row.put("ctx_cov@5", round(u5));
                row.put("ctx_cov@10", round(u10));
                row.put("ctx_chars@5", ctx5.stream().mapToInt(c -> c.norm.length()).sum());
                row.put("need_groups", g.need.size());
                row.put("answered@5", n5 >= 1.0);
                row.put("need_cov@5", round(n5));
                row.put("need_missing@5", missingGroups(g, ctx5Text));
                row.put("need_src@5", src5);
                row.put("top1_doc", ranked.isEmpty() ? "" : ranked.get(0).doc);
                row.put("top1_cov", ranked.isEmpty() ? 0 : round(coverage(g, ranked.get(0))));
                row.put("top3_docs", ranked.stream().limit(3).map(c -> c.doc).collect(Collectors.joining(" | ")));
                rows.add(row);
            }

            Path out = Path.of(outPrefix + "-" + tag.replace('|', '-') + ".jsonl");
            try (BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
                for (ObjectNode r : rows) w.write(M.writeValueAsString(r) + "\n");
            }

            System.out.println();
            System.out.println("== " + tag + " = " + cfg.desc()
                    + (win == 0 ? "" : " +- " + win + " neighbour")
                    + ("off".equals(mode) ? "" : " + " + mode) + " ==");
            System.out.println("  chunks=" + chunks.size()
                    + " avgChars=" + (chunks.isEmpty() ? 0 : chars / chunks.size())
                    + " avgTokens=" + (chunks.isEmpty() ? 0 : tokens / chunks.size())
                    + " overLimit=" + overLimit
                    + " unlocatable=" + unlocatable);
            System.out.println("  intact=" + intact + "/" + n
                    + "  recall@5=" + r5 + "/" + n
                    + "  recall@10=" + r10 + "/" + n
                    + "  recall@10(strict)=" + r10s + "/" + n
                    + "  mrr@10=" + fmt(mrr / n));
            System.out.println("  ctxRecall@5=" + u5n + "/" + n
                    + "  ctxRecall@10=" + u10n + "/" + n
                    + "  ctxChars@5=" + (n == 0 ? 0 : (long) (ctxChars5 / n))
                    + " (raw " + (n == 0 ? 0 : (long) (rawChars5 / n))
                    + ", x" + fmt(rawChars5 == 0 ? 0 : ctxChars5 / rawChars5) + ")");
            System.out.println("  answered@5=" + ans5 + "/" + n
                    + "  answered@10=" + ans10 + "/" + n
                    + "  needCov@5=" + fmt(needCov5 / n));
            if (!noAns.isEmpty()) {
                System.out.println("  context lacks a required fact (" + noAns.size() + "):");
                for (String s : noAns) System.out.println("    " + s);
            }
            if (!crossDoc.isEmpty()) {
                System.out.println("  fact supplied by another document, not the question's own ("
                        + crossDoc.size() + "): " + String.join(" ", crossDoc));
            }
            if (!cut.isEmpty()) {
                System.out.println("  gold spans no single widened hit can hold whole (" + cut.size() + "):");
                for (Gold g : cut) System.out.println("    " + g.id + " [" + g.doc + "] " + brief(g.span));
            }
            System.out.println("  -> " + out.toAbsolutePath());

            summary.add(String.format("%-22s %6d %8d %9d %9d %7.1f%% %7.1f%% %8.1f%% %8.3f %7.1f%% %8.1f%% %10.1f%% %9.1f%% %9.1f%% %10d %6.2f",
                    tag, chunks.size(),
                    chunks.isEmpty() ? 0 : chars / chunks.size(),
                    chunks.isEmpty() ? 0 : tokens / chunks.size(), overLimit,
                    100.0 * intact / n, 100.0 * r5 / n, 100.0 * r10 / n, mrr / n,
                    100.0 * ans5 / n, 100.0 * ans10 / n, 100.0 * needCov5 / n,
                    100.0 * u5n / n, 100.0 * u10n / n,
                    (long) (ctxChars5 / n), rawChars5 == 0 ? 0 : ctxChars5 / rawChars5));
        }
        }
        if (RANKDIAG) {
            rankDiagPass(cfg, chunks, byDoc, golds, model);
        }
        return summary;
    }

    /**
     * One ranking audit per neighbour-window radius: where the answer sits in the plain cosine order,
     * what pool size that implies, and what the prompt would have to carry. Pure in-memory cosine over
     * cached vectors, so it adds no embedding call. See {@link #RANKDIAG}.
     */
    static void rankDiagPass(Cfg cfg, List<Chunk> chunks, Map<String, List<Chunk>> byDoc,
                             List<Gold> golds, EmbeddingModel model) throws Exception {
        for (int win : WINDOWS) {
            List<Integer> ranks = new ArrayList<>();
            List<Double> goldScores = new ArrayList<>();
            List<Double> topScores = new ArrayList<>();
            List<String> labels = new ArrayList<>();
            List<Integer> c3 = new ArrayList<>(), c5 = new ArrayList<>(), c10 = new ArrayList<>();
            for (Gold g : golds) {
                float[] qv = embed(model, g.query);
                List<Chunk> ranked = topK(chunks, qv);   // topK caps at TOPK=10, so pool<=10 here
                int rank = -1;
                for (int i = 0; i < ranked.size(); i++) {
                    if (unionCoverage(g, windowOf(ranked.get(i), byDoc, win)) >= HIT) { rank = i + 1; break; }
                }
                ranks.add(rank);
                goldScores.add(goldScore(g, chunks, qv));
                topScores.add(ranked.isEmpty() ? -1 : cosine(qv, ranked.get(0).vec));
                labels.add(g.id + " [" + g.doc + "] " + brief(g.query));
                c3.add(widen(ranked, byDoc, win, 3).stream().mapToInt(c -> c.norm.length()).sum());
                c5.add(widen(ranked, byDoc, win, 5).stream().mapToInt(c -> c.norm.length()).sum());
                c10.add(widen(ranked, byDoc, win, 10).stream().mapToInt(c -> c.norm.length()).sum());
            }
            printRankDiag(cfg, win, ranks, goldScores, topScores, labels, c3, c5, c10);
        }
    }

    /**
     * Prints the ranking audit for one config: where the answer sits in the plain cosine order, what
     * pool size that implies, and whether the min-score threshold removes anything before a ranker
     * ever runs. See {@link #RANKDIAG} for why these two bounds, and not recall@5, decide whether a
     * reranker is worth building.
     */
    static void printRankDiag(Cfg cfg, int win, List<Integer> ranks, List<Double> goldScores,
                              List<Double> topScores, List<String> labels,
                              List<Integer> chars3, List<Integer> chars5, List<Integer> chars10) {
        int n = ranks.size();
        System.out.println();
        System.out.println("  -- RANKDIAG " + cfg.name() + " -- plain cosine, no rewrite, neighbour=" + win
                + ", n=" + n);

        int r1 = 0, r2 = 0, r3 = 0, r45 = 0, r610 = 0, rDeep = 0, never = 0;
        for (int r : ranks) {
            if (r == 1) r1++;
            else if (r == 2) r2++;
            else if (r == 3) r3++;
            else if (r >= 4 && r <= 5) r45++;
            else if (r >= 6 && r <= 10) r610++;
            else if (r > 10) rDeep++;
            else never++;
        }
        System.out.println("    answer rank (>=80% coverage in one hit): r1=" + r1 + " r2=" + r2 + " r3=" + r3
                + " r4-5=" + r45 + " r6-10=" + r610 + " r>10=" + rDeep + " notRetrieved=" + never);

        StringBuilder sweep = new StringBuilder("    recall@K sweep:");
        for (int k : new int[]{1, 2, 3, 5, 10, 20, 30, 50}) {
            sweep.append("  @").append(k).append('=').append(fmt(100.0 * countWithin(ranks, k) / n)).append('%');
        }
        System.out.println(sweep);

        // xCost is expressed against the top-5 context, which is the shape the neighbour-window work
        // was measured on, so both comparisons stay in the same unit.
        double baseAvg = n == 0 ? 0 : (double) sum(chars5) / n;
        System.out.println("    pool trade-off -- a ranker can only reorder what was pooled, so the best"
                + " recall@K any PERFECT reranker reaches equals recall@pool:");
        for (int pool : new int[]{3, 5, 10}) {
            List<Integer> chars = pool == 3 ? chars3 : pool == 5 ? chars5 : chars10;
            double avgChars = chars.isEmpty() ? 0 : (double) sum(chars) / n;
            System.out.println("      pool=" + pool
                    + "  ceiling recall@5=" + fmt(100.0 * countWithin(ranks, pool) / n) + "%"
                    + "   avgCtxChars=" + (long) avgChars
                    + "   xCost=" + fmt(baseAvg == 0 ? 0 : avgChars / baseAvg)
                    + (pool < 5 ? "   (pool < 5 cannot fill 5 slots by reordering alone)" : ""));
        }
        List<Double> gs = goldScores.stream().filter(v -> v >= 0).collect(Collectors.toList());
        List<Double> ts = topScores.stream().filter(v -> v >= 0).collect(Collectors.toList());
        double minScore = Double.parseDouble(System.getenv().getOrDefault("MINSCORE", "0.6"));
        System.out.println("    store score of the best hit that holds the answer (=(1+cos)/2, the scale"
                + " min_score is compared on):"
                + " min=" + fmt(gs.isEmpty() ? -1 : storeScore(Collections.min(gs)))
                + " median=" + fmt(median(gs) < 0 ? -1 : storeScore(median(gs)))
                + " max=" + fmt(gs.isEmpty() ? -1 : storeScore(Collections.max(gs))));
        System.out.println("    store score of the top-1 hit (any document):                                  "
                + " min=" + fmt(ts.isEmpty() ? -1 : storeScore(Collections.min(ts)))
                + " median=" + fmt(median(ts) < 0 ? -1 : storeScore(median(ts)))
                + " max=" + fmt(ts.isEmpty() ? -1 : storeScore(Collections.max(ts))));
        List<String> under = new ArrayList<>();
        List<String> noHold = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            double s = goldScores.get(i);
            if (s < 0) noHold.add(labels.get(i));
            else if (storeScore(s) < minScore) under.add(labels.get(i) + " storeScore=" + fmt(storeScore(s)));
        }
        // A gold whose own best hit scores under the threshold is unreachable in production whatever
        // the ranker does -- that is a threshold bug. A gold with no holding chunk at all is a
        // chunking defect. Neither is fixable by ranking, so both are reported separately.
        System.out.println("    min_score bite (production default " + fmt(minScore) + "): "
                + under.size() + " gold(s) fall below it (unreachable before ranking)");
        for (String s : under) System.out.println("      " + s);
        if (!noHold.isEmpty()) {
            System.out.println("    no single hit holds the answer at all (" + noHold.size()
                    + ") -- chunking defect, not a ranking one:");
            for (String s : noHold) System.out.println("      " + s);
        }

        System.out.println("    answers ranked outside the top 5 (what a reranker would have to fix):");
        int shown = 0;
        for (int i = 0; i < n; i++) {
            int r = ranks.get(i);
            if (r >= 1 && r <= 5) continue;
            System.out.println("      rank=" + (r <= 0 ? "notRetrieved" : r) + "  " + labels.get(i));
            shown++;
        }
        if (shown == 0) System.out.println("      none");
    }

    static int sum(List<Integer> values) {
        int s = 0;
        for (int v : values) s += v;
        return s;
    }

    /** How many golds the answer is reachable within {@code k} places of the plain cosine order. */
    static int countWithin(List<Integer> ranks, int k) {
        int hit = 0;
        for (int r : ranks) if (r > 0 && r <= k) hit++;
        return hit;
    }

    /**
     * The segments a hit brings along: the hit plus {@code w} neighbours on each side of the same
     * document, in reading order. Mirrors what SegmentExpandProcessor does at query time.
     */
    static List<Chunk> windowOf(Chunk hit, Map<String, List<Chunk>> byDoc, int w) {
        List<Chunk> list = byDoc.get(hit.doc);
        if (list == null) return List.of(hit);
        int from = Math.max(0, hit.idx - w);
        int to = Math.min(list.size() - 1, hit.idx + w);
        return list.subList(from, to + 1);
    }

    /**
     * Widened, de-duplicated context of the top {@code top} hits -- the production output shape:
     * one entry per distinct segment, so a segment shared by two hits is charged once.
     */
    static List<Chunk> widen(List<Chunk> ranked, Map<String, List<Chunk>> byDoc, int w, int top) {
        Map<Integer, Chunk> uniq = new LinkedHashMap<>();
        for (int i = 0; i < Math.min(top, ranked.size()); i++) {
            for (Chunk c : windowOf(ranked.get(i), byDoc, w)) uniq.putIfAbsent(c.gid, c);
        }
        return new ArrayList<>(uniq.values());
    }

    /**
     * Fraction of the gold span covered by the union of the given segments. Counted per character
     * rather than as one min..max interval: after de-duplication two hits of the same document can
     * sit far apart, and an interval would credit text that was never returned.
     */
    static double unionCoverage(Gold g, List<Chunk> segments) {
        boolean[] hit = new boolean[g.normLen];
        for (Chunk c : segments) {
            if (c.start < 0) {
                if (c.norm.contains(norm(g.span))) return 1.0;
                continue;
            }
            if (!sameGoldDoc(g, c)) continue;
            int lo = Math.max(g.start, c.start), hi = Math.min(g.end, c.end);
            for (int i = lo; i < hi; i++) hit[i - g.start] = true;
        }
        int covered = 0;
        for (boolean b : hit) if (b) covered++;
        return covered / (double) g.normLen;
    }

    static double coverage(Gold g, Chunk c) {
        if (c.start < 0) return c.norm.contains(norm(g.span)) ? 1.0 : 0.0;
        if (!sameGoldDoc(g, c)) return 0.0;
        int lo = Math.max(g.start, c.start), hi = Math.min(g.end, c.end);
        return hi <= lo ? 0.0 : Math.min(1.0, (hi - lo) / (double) g.normLen);
    }

    /**
     * Highest cosine score among the gold document's own chunks that hold the span well enough to
     * count as a hit. This is the quantity {@code retrieve_min_score} is compared against in
     * production -- but note the scale, see {@link #storeScore}: the store does not compare cosine
     * directly. Returns -1 when no chunk of that document covers the span (chunking defect).
     */
    static double goldScore(Gold g, List<Chunk> chunks, float[] qv) {
        double best = -1;
        for (Chunk c : chunks) {
            if (!sameGoldDoc(g, c) || coverage(g, c) < HIT) continue;
            best = Math.max(best, cosine(qv, c.vec));
        }
        return best;
    }

    /**
     * Map a raw cosine similarity to the score langchain4j's PgVectorEmbeddingStore reports, which is
     * what {@code retrieve_min_score} is compared against:
     * <pre>
     *   SELECT (2 - (embedding &lt;=&gt; ?)) / 2 AS score FROM ...
     *   WHERE  (embedding &lt;=&gt; ?) &lt;= 2 - 2 * minScore
     * </pre>
     * {@code <=>} is cosine <em>distance</em>, so score = (1 + cosine) / 2 -- a [0,1] rescaling, not
     * the cosine itself. Comparing a raw cosine against the default {@code min_score = 0.6} reads as
     * "nearly every chunk is filtered out", which is wrong by construction: 0.6 here means
     * cosine &gt;= 0.2. Getting this backwards invents a threshold bug that does not exist.
     */
    static double storeScore(double cosine) {
        return (1.0 + cosine) / 2.0;
    }

    /** Median of a score list; -1 for an empty list. Used for the score-range summary. */
    static double median(List<Double> values) {
        if (values.isEmpty()) return -1;
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int n = sorted.size();
        return n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
    }

    /**
     * The text handed to the LLM: every segment, whitespace-stripped, joined by a separator that
     * cannot appear in content -- so a required fact can never be "found" straddling two segments.
     */
    static String merged(List<Chunk> segments) {
        StringBuilder sb = new StringBuilder();
        for (Chunk c : segments) sb.append(c.norm).append('\u0000');
        return sb.toString();
    }

    /** Fraction of the question's required facts that the context contains. */
    static double needCoverage(Gold g, String ctx) {
        int ok = 0;
        for (List<String> group : g.need) {
            for (String alt : group) if (ctx.contains(alt)) { ok++; break; }
        }
        return ok / (double) g.need.size();
    }

    /** 1-based indices of the need groups the context does not satisfy (for drill-down). */
    static String missingGroups(Gold g, String ctx) {
        List<String> miss = new ArrayList<>();
        for (int i = 0; i < g.need.size(); i++) {
            boolean ok = false;
            for (String alt : g.need.get(i)) if (ctx.contains(alt)) { ok = true; break; }
            if (!ok) miss.add(String.valueOf(i + 1));
        }
        return String.join(",", miss);
    }

    /**
     * Per group, where the fact came from: "S" when the question's own document supplies it, "O"
     * when a different document does, "-" when it is absent.
     *
     * This matters because the two metrics are deliberately scoped differently. recall@K is
     * document-scoped -- did the chunk holding the answer come back. answered@K is corpus-wide --
     * is the fact anywhere in the text the LLM receives, which is what actually determines whether
     * a question can be answered. This corpus cross-quotes whole tables between guides (the same
     * 严格/宽松 prompt text lives in three files), so "O" is not an error, and the answer metric can
     * legitimately land above the recall metric. The two must not be subtracted from each other.
     */
    static String needSources(Gold g, List<Chunk> segments) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < g.need.size(); i++) {
            if (i > 0) sb.append(',');
            String src = "-";
            for (Chunk c : segments) {
                boolean hit = false;
                for (String alt : g.need.get(i)) if (c.norm.contains(alt)) { hit = true; break; }
                if (!hit) continue;
                if (sameGoldDoc(g, c)) { src = "S"; break; }
                if ("-".equals(src)) src = "O";
            }
            sb.append(src);
        }
        return sb.toString();
    }

    /**
     * Checks of the answer-view helpers that need no corpus and no embedding calls. The golden-set
     * loader keeps its own guards, but these three functions decide every answered@K number, so
     * they get exercised on their own -- a metric that is wrong is worse than no metric.
     * Run: SELFTEST=1 java ... KbEval
     */
    static void selfTest() {
        Gold g = new Gold();
        g.doc = "d";
        g.need = List.of(List.of("alpha", "ALPHA"), List.of("beta"));

        check(needCoverage(g, "xxalpha\u0000yy") == 0.5, "one of two required groups present");
        check(needCoverage(g, "zzALPHA\u0000beta") == 1.0, "either alternative satisfies its group");
        check(needCoverage(g, "") == 0.0, "empty context satisfies nothing");
        check(missingGroups(g, "beta only").equals("1"), "missing group index is 1-based");
        check(missingGroups(g, "beta only").isEmpty() == false, "missing list is not empty");
        check(missingGroups(g, "alpha beta").isEmpty(), "nothing missing");
        check(needCoverage(g, "beta") == 0.5, "second group alone is half");

        // a required fact must never match across two segments
        Chunk a = new Chunk(); a.norm = "xy";
        Chunk b = new Chunk(); b.norm = "z";
        String mergedText = merged(List.of(a, b));
        check(!mergedText.contains("xyz"), "separator blocks a cross-segment match");
        check(mergedText.contains("xy"), "segments are still present");

        // paraphrase output parsing: numbering, bullets, blanks and duplicates must not become
        // extra queries -- each extra variant is an embedding call and a chance to outvote the user
        List<String> p = parseLines("1. 甲问题？\n- 乙问题？\n• 甲问题？\n\n  丙问题？  \n丁问题？", 3);
        check(p.size() == 3, "paraphrases capped at the requested count");
        check(p.get(0).equals("甲问题？"), "numeric list marker stripped");
        check(p.get(1).equals("乙问题？"), "bullet marker stripped");
        check(p.get(2).equals("丙问题？"), "blank lines skipped, whitespace trimmed, duplicate dropped");
        check(parseLines("", 3).isEmpty(), "empty rewrite yields no variants");

        // score scale: min_score is compared against (1+cos)/2, not cosine. Reading a raw cosine
        // against the default 0.6 makes a healthy corpus look like it is being filtered to nothing.
        check(Math.abs(storeScore(1.0) - 1.0) < 1e-9, "identical vectors score 1.0");
        check(Math.abs(storeScore(0.0) - 0.5) < 1e-9, "orthogonal vectors score 0.5");
        check(Math.abs(storeScore(-1.0)) < 1e-9, "opposite vectors score 0.0");
        check(Math.abs(storeScore(0.2) - 0.6) < 1e-9, "min_score 0.6 is exactly cosine 0.2");

        System.out.println("SELFTEST OK");
    }

    static void check(boolean cond, String what) {
        if (!cond) throw new IllegalStateException("selftest failed: " + what);
    }

    // ---- query-side rewriting: prompts, cache, and the ranking they produce -------------------

    /** Top-K by cosine alone -- the production ranking, and what "off" must reproduce exactly. */
    static List<Chunk> topK(List<Chunk> chunks, float[] qv) {
        return chunks.stream()
                .sorted(Comparator.comparingDouble((Chunk c) -> -cosine(qv, c.vec)))
                .limit(TOPK).collect(Collectors.toList());
    }

    /**
     * Top-K under a rewrite mode. Two fusions are available, and the difference matters:
     *
     *   max-cosine (default)  each variant votes for a chunk, the chunk keeps its best vote. One
     *                         over-eager paraphrase can therefore outrank the true answer -- this
     *                         demotes correct #1 hits when the user's phrasing was already good.
     *   "*-rrf" (Reciprocal   each variant produces its own ranking and the ranks are fused, so a
     *   Rank Fusion)         chunk must be ranked high by several variants to climb. Same idea as
     *                         the hybrid-search fusion already used in production.
     *
     * "hyde" is the exception to including the query: there the hypothetical passage replaces it.
     */
    static List<Chunk> rankFor(String mode, String query, float[] qv, List<Chunk> chunks,
                               EmbeddingModel model) {
        List<String> variants = variantsFor(mode, query);
        if (variants.isEmpty()) return topK(chunks, qv);
        List<float[]> extra = new ArrayList<>();
        for (String v : variants) extra.add(embed(model, v));
        boolean withQuery = !"hyde".equals(mode);

        if (mode.endsWith("-rrf")) {
            List<float[]> votes = new ArrayList<>();
            if (withQuery) votes.add(qv);
            votes.addAll(extra);
            Map<Integer, Double> fused = new HashMap<>();
            for (float[] v : votes) {
                List<Chunk> r = rankAll(chunks, v);
                for (int i = 0; i < r.size(); i++) {
                    fused.merge(r.get(i).gid, 1.0 / (RRF_K + i + 1), Double::sum);
                }
            }
            return chunks.stream().sorted((a, b) ->
                            Double.compare(fused.getOrDefault(b.gid, 0.0), fused.getOrDefault(a.gid, 0.0)))
                    .limit(TOPK).collect(Collectors.toList());
        }

        Map<Integer, Double> best = new HashMap<>();
        for (Chunk c : chunks) {
            double s = withQuery ? cosine(qv, c.vec) : Double.NEGATIVE_INFINITY;
            for (float[] v : extra) s = Math.max(s, cosine(v, c.vec));
            best.put(c.gid, s);
        }
        return chunks.stream().sorted((a, b) -> Double.compare(best.get(b.gid), best.get(a.gid)))
                .limit(TOPK).collect(Collectors.toList());
    }

    /** Whole corpus in score order -- RRF needs each variant's full ranking, not just its top-K. */
    static List<Chunk> rankAll(List<Chunk> chunks, float[] v) {
        return chunks.stream().sorted(Comparator.comparingDouble((Chunk c) -> -cosine(v, c.vec)))
                .collect(Collectors.toList());
    }

    /**
     * The rewritten queries for one question, from the on-disk cache when possible. One LLM call
     * per question is the steady-state cost of every non-off mode; caching keeps reruns free.
     */
    static List<String> variantsFor(String mode, String query) {
        if ("off".equals(mode)) return List.of();
        String key = promptKind(mode) + '|' + promptVersion(promptKind(mode)) + '|' + QW_N
                + '|' + QW_MODEL + '|' + query;
        List<String> hit = RCACHE.get(key);
        if (hit != null) { llmCacheHits++; return hit; }

        String prompt = qwPrompt(mode, query);
        String out = withRetry(() -> CHAT.chat(prompt));
        llmNewCalls++;
        llmPromptChars += prompt.length();
        llmOutChars += out == null ? 0 : out.length();
        // flush as we go: a long rewrite sweep that dies halfway must not throw away the calls
        // it already paid for (that has bitten this project before).
        if (llmNewCalls % 10 == 0) {
            try {
                saveRewriteCache();
            } catch (Exception e) {
                System.out.println("   (rewrite cache flush failed: " + e + ")");
            }
        }
        List<String> v = mode.startsWith("mq") ? parseLines(out, QW_N) : firstNonBlank(out);
        if (v.isEmpty()) {
            throw new IllegalStateException("rewrite produced nothing for " + mode + ": " + brief(query));
        }
        RCACHE.put(key, v);
        return v;
    }

    /** Which prompt a mode needs; mq and mq-rrf share one, as do hyde, hyde+q and hyde-rrf. */
    static String promptKind(String mode) {
        return mode.startsWith("mq") ? "mq" : "hyde";
    }

    /** Cache-key version of the prompt for that family; QW_PROMPT_V overrides both for a one-off. */
    static String promptVersion(String kind) {
        String override = System.getenv("QW_PROMPT_V");
        if (override != null && !override.isBlank()) return override;
        return "mq".equals(kind) ? MQ_PV : HYDE_PV;
    }

    static String qwPrompt(String mode, String query) {
        if (mode.startsWith("mq")) {
            // "same meaning, different wording" is NOT enough: left at that, the model emits
            // near-identical reorderings whose embeddings sit on top of the original, so the fusion
            // has nothing new to work with. What retrieval needs is different angles and different
            // vocabulary -- the phrasings a user might actually type that the document would match.
            return "你是检索查询改写助手。用户的问题要用向量检索去命中中文产品文档，"
                    + "而文档里的用词往往和用户不一样。\n"
                    + "请生成 " + QW_N + " 条改写查询来提高召回：每条换一个切入角度或换一套术语，"
                    + "覆盖用户可能使用的不同说法（口语问法 / 书面问法 / 文档里更可能出现的专业表述）。\n"
                    + "严禁只替换同义词、只调整语序或只改标点——那样的改写没有价值。\n"
                    + "要求：每条一行；不要编号、不要引号、不要解释；"
                    + "保留原问题中的专有名词、型号与数字；只输出这些查询。\n问题：" + query;
        }
        return "请直接写一段 100~200 字的中文产品文档正文，内容要足以回答下面的问题。\n"
                + "要求：用陈述句、像正式文档一样平实；不要提问、不要写「根据…」这类开场、"
                + "不要标题、不要分点；只输出正文。\n问题：" + query;
    }

    /** One query per line, bullets and numbering stripped, duplicates dropped, capped at limit. */
    static List<String> parseLines(String out, int limit) {
        List<String> v = new ArrayList<>();
        if (out == null) return v;
        for (String raw : out.split("\\r?\\n")) {
            String s = raw.replaceFirst("^\\s*(?:[-*·•]|\\d+\\s*[.)、])\\s*", "").trim();
            if (s.isEmpty() || v.contains(s)) continue;
            v.add(s);
            if (v.size() >= limit) break;
        }
        return v;
    }

    static List<String> firstNonBlank(String out) {
        for (String raw : out == null ? new String[0] : out.split("\\r?\\n")) {
            String s = raw.trim();
            if (!s.isEmpty()) return List.of(s);
        }
        return List.of();
    }

    static void loadRewriteCache() throws Exception {
        if (!Files.isRegularFile(rewriteCacheFile)) return;
        for (String line : Files.readAllLines(rewriteCacheFile, StandardCharsets.UTF_8)) {
            if (line.isBlank()) continue;
            int tab = line.indexOf('\t');
            if (tab < 0) continue;
            String json = new String(Base64.getDecoder().decode(line.substring(0, tab)), StandardCharsets.UTF_8);
            List<String> v = new ArrayList<>();
            for (JsonNode n : M.readTree(json)) v.add(n.asText());
            RCACHE.put(line.substring(tab + 1), v);
        }
        System.out.println("[rewrite-cache] loaded " + RCACHE.size() + " entries from " + rewriteCacheFile);
    }

    static void saveRewriteCache() throws Exception {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<String>> e : RCACHE.entrySet()) {
            var arr = M.createArrayNode();
            e.getValue().forEach(arr::add);
            sb.append(Base64.getEncoder().encodeToString(
                            M.writeValueAsString(arr).getBytes(StandardCharsets.UTF_8)))
                    .append('\t').append(e.getKey()).append('\n');
        }
        Files.writeString(rewriteCacheFile, sb.toString(), StandardCharsets.UTF_8);
    }

    static void embedAll(EmbeddingModel model, List<Chunk> chunks) {
        List<Chunk> todo = chunks.stream().filter(c -> !CACHE.containsKey(key(c.norm))).collect(Collectors.toList());
        int i = 0, batch = 0;
        while (i < todo.size()) {
            List<Chunk> group = new ArrayList<>();
            int chars = 0;
            while (i < todo.size() && group.size() < 8 && chars < 6000) {
                Chunk c = todo.get(i++);
                group.add(c);
                chars += c.norm.length();
            }
            List<TextSegment> segs = group.stream().map(c -> TextSegment.from(c.norm)).collect(Collectors.toList());
            Response<List<Embedding>> resp = withRetry(() -> model.embedAll(segs));
            List<Embedding> embs = resp.content();
            if (embs.size() != group.size()) {
                throw new IllegalStateException("embedding count mismatch: " + embs.size() + " != " + group.size());
            }
            for (int k = 0; k < group.size(); k++) {
                group.get(k).vec = embs.get(k).vector();
                CACHE.put(key(group.get(k).norm), embs.get(k).vector());
            }
            if (++batch % 20 == 0) System.out.println("   embedded " + i + "/" + todo.size());
            sleep(120);
        }
        for (Chunk c : chunks) {
            c.vec = CACHE.get(key(c.norm));
            if (c.vec == null) throw new IllegalStateException("chunk left without embedding");
        }
    }

    static float[] embed(EmbeddingModel model, String text) {
        String k = key(norm(text));
        float[] v = CACHE.get(k);
        if (v == null) {
            v = withRetry(() -> model.embed(norm(text))).content().vector();
            CACHE.put(k, v);
        }
        return v;
    }

    // ---- embedding cache on disk: repeated A/B runs must not pay for the same vectors again ----

    static String key(String normText) {
        try {
            byte[] h = MessageDigest.getInstance("MD5").digest(normText.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static void loadCache() throws Exception {
        if (!Files.isRegularFile(cacheFile)) return;
        for (String line : Files.readAllLines(cacheFile, StandardCharsets.UTF_8)) {
            if (line.isBlank()) continue;
            int tab = line.indexOf('\t');
            if (tab < 0) continue;
            byte[] raw = Base64.getDecoder().decode(line.substring(0, tab));
            ByteBuffer bb = ByteBuffer.wrap(raw);
            float[] v = new float[raw.length / 4];
            for (int i = 0; i < v.length; i++) v[i] = bb.getFloat();
            CACHE.put(line.substring(tab + 1), v);
        }
        System.out.println("[embed-cache] loaded " + CACHE.size() + " vectors from " + cacheFile);
    }

    static void saveCache() throws Exception {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, float[]> e : CACHE.entrySet()) {
            ByteBuffer bb = ByteBuffer.allocate(e.getValue().length * 4);
            for (float f : e.getValue()) bb.putFloat(f);
            sb.append(Base64.getEncoder().encodeToString(bb.array())).append('\t').append(e.getKey()).append('\n');
        }
        Files.writeString(cacheFile, sb.toString(), StandardCharsets.UTF_8);
    }

    interface Call<T> { T get(); }

    static <T> T withRetry(Call<T> call) {
        RuntimeException last = null;
        for (int i = 0; i < 4; i++) {
            try {
                return call.get();
            } catch (RuntimeException e) {
                last = e;
                sleep(2000L * (i + 1));
            }
        }
        throw last;
    }

    static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    static String norm(String s) { return s.replaceAll("\\s+", ""); }

    static int count(String hay, String needle) {
        int c = 0, i = hay.indexOf(needle);
        while (i >= 0) { c++; i = hay.indexOf(needle, i + 1); }
        return c;
    }

    static double cosine(float[] a, float[] b) {
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i]; }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    static double round(double v) { return Math.round(v * 1000.0) / 1000.0; }
    static String fmt(double v) { return String.format("%.3f", v); }
    static String brief(String s) { return s.length() <= 40 ? s : s.substring(0, 40) + "…"; }
}
