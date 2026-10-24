import com.moyz.adi.common.rag.DocumentSplitterFactory;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.TokenCountEstimator;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

public class OvProbe3 {
    public static void main(String[] a) throws Exception {
        TokenCountEstimator est = com.moyz.adi.common.rag.TokenEstimatorFactory
                .create(com.moyz.adi.common.constant.AdiConstant.TokenEstimator.OPENAI);
        Path root = Path.of("e:/reg-project/aideepin/docs/cn");
        List<Path> docs;
        try (Stream<Path> s = Files.walk(root)) {
            docs = s.filter(p -> p.toString().endsWith(".md")).sorted().collect(Collectors.toList());
        }
        System.out.println("语料文档数: " + docs.size());
        for (String strategy : new String[]{"sentence_cn", "recursive"}) {
            System.out.println("--- strategy=" + strategy + " size=512 ---");
            for (int ov : new int[]{0, 51, 102, 128}) {
                DocumentSplitter sp = DocumentSplitterFactory.create(strategy, 512, ov, "", est);
                int segs = 0; long chars = 0, tokens = 0; int changed = 0;
                String prevLast = null;
                for (Path d : docs) {
                    String text = Files.readString(d);
                    List<TextSegment> ss = sp.split(Document.from(text));
                    segs += ss.size();
                    for (TextSegment s : ss) { chars += s.text().length(); tokens += est.estimateTokenCountInText(s.text()); }
                    if (!ss.isEmpty()) {
                        String last = ss.get(ss.size()-1).text();
                        if (prevLast != null && last.equals(prevLast)) changed++;
                        prevLast = last;
                    }
                }
                System.out.printf("overlap=%-4d segs=%-6d avgChar=%-8.1f avgToken=%-8.1f totalChars=%-9d totalToken=%d%n",
                        ov, segs, chars / (double) Math.max(segs,1), tokens / (double) Math.max(segs,1), chars, tokens);
            }
        }
    }
}
