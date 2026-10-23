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

/** Does `overlap` actually do anything once chunks are cut at sentence level? */
public class Diag3 {
    public static void main(String[] args) throws Exception {
        String raw = Files.readString(Path.of(args[0]), StandardCharsets.UTF_8);
        TokenCountEstimator est = TokenEstimatorFactory.create(AdiConstant.TokenEstimator.OPENAI);

        for (int overlap : new int[]{0, 64, 160}) {
            DocumentSplitter sp = DocumentSplitterFactory.create("sentence_cn", 512, overlap, "", est);
            List<TextSegment> segs = sp.split(Document.from(raw));
            int shared = 0, maxTok = 0, over = 0;
            StringBuilder detail = new StringBuilder();
            for (int i = 0; i < segs.size(); i++) {
                int tok = est.estimateTokenCountInText(segs.get(i).text());
                maxTok = Math.max(maxTok, tok);
                if (tok > 512) over++;
                if (i > 0) {
                    int s = sharedPrefixLen(segs.get(i - 1).text(), segs.get(i).text());
                    shared = Math.max(shared, s);
                    if (i <= 3) {
                        detail.append("\n        chunk[").append(i).append("] startsWithPrevTail=").append(s)
                              .append(" head=").append(head(segs.get(i).text(), 34));
                    }
                }
            }
            System.out.println("### sentence_cn size=512 overlap=" + overlap
                    + " chunks=" + segs.size() + " maxTokens=" + maxTok + " overLimit=" + over
                    + " maxSharedBoundary=" + shared + detail);
        }

        // for contrast: what the old default did on the same text
        for (int overlap : new int[]{0, 64}) {
            DocumentSplitter sp = DocumentSplitterFactory.create("recursive", 512, overlap, "", est);
            List<TextSegment> segs = sp.split(Document.from(raw));
            int shared = 0;
            for (int i = 1; i < segs.size(); i++) {
                shared = Math.max(shared, sharedPrefixLen(segs.get(i - 1).text(), segs.get(i).text()));
            }
            System.out.println("### recursive   size=512 overlap=" + overlap
                    + " chunks=" + segs.size() + " maxSharedBoundary=" + shared);
        }
    }

    /** Length of the longest suffix of `a` that is a prefix of `b`. */
    static int sharedPrefixLen(String a, String b) {
        int max = Math.min(a.length(), b.length());
        for (int len = max; len > 0; len--) {
            if (a.regionMatches(a.length() - len, b, 0, len)) return len;
        }
        return 0;
    }

    static String head(String s, int n) {
        String t = s.replace("\n", "\\n");
        return t.length() <= n ? t : t.substring(0, n) + "…";
    }
}
