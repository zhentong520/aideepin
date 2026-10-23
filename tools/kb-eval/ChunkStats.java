import com.moyz.adi.common.cosntant.AdiConstant;
import com.moyz.adi.common.rag.DocumentSplitterFactory;
import com.moyz.adi.common.rag.TokenEstimatorFactory;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.TokenCountEstimator;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Chunk statistics only, no embeddings: is `sentence_cn` safe on a NON-Chinese corpus?
 * Runs the same corpus through recursive and sentence_cn and compares chunk counts and sizes,
 * so switching the default cannot silently break English / code documentation.
 *
 * Usage: ChunkStats <corpusDir> <strategy> <size> <overlap>
 */
public class ChunkStats {
    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args[0]);
        String strategy = args[1];
        int size = Integer.parseInt(args[2]), overlap = Integer.parseInt(args[3]);
        TokenCountEstimator est = TokenEstimatorFactory.create(AdiConstant.TokenEstimator.OPENAI);
        DocumentSplitter sp = DocumentSplitterFactory.create(strategy, size, overlap, "", est);

        List<Path> files;
        try (Stream<Path> s = Files.walk(dir)) {
            files = s.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".md"))
                    .sorted().collect(Collectors.toList());
        }
        int chunks = 0, over = 0, empty = 0, endsAtBoundary = 0;
        long chars = 0, tokens = 0;
        for (Path f : files) {
            String text = Files.readString(f, StandardCharsets.UTF_8);
            for (TextSegment seg : sp.split(Document.from(text))) {
                chunks++;
                String t = seg.text();
                chars += t.length();
                int tk = est.estimateTokenCountInText(t);
                tokens += tk;
                if (tk > size) over++;
                if (t.isBlank()) empty++;
                String trimmed = t.strip();
                if (!trimmed.isEmpty()) {
                    char last = trimmed.charAt(trimmed.length() - 1);
                    if ("。！？；.!?".indexOf(last) >= 0) endsAtBoundary++;
                }
            }
        }
        System.out.printf("%-12s size=%-5d overlap=%-4d docs=%d chunks=%d avgChars=%d avgTokens=%d overLimit=%d empty=%d endsAtSentenceEnd=%.1f%%%n",
                strategy, size, overlap, files.size(), chunks, chunks == 0 ? 0 : chars / chunks,
                chunks == 0 ? 0 : tokens / chunks, over, empty, chunks == 0 ? 0 : 100.0 * endsAtBoundary / chunks);
    }
}
