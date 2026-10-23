import com.moyz.adi.common.cosntant.AdiConstant;
import com.moyz.adi.common.rag.CjkSentenceSplitter;
import com.moyz.adi.common.rag.TokenEstimatorFactory;
import dev.langchain4j.model.TokenCountEstimator;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * How often is a single sentence (the smallest unit this splitter can cut at) larger than the
 * configured segment size? Those are the cases where the inherited fallback sub-splitter has to cut
 * mid-sentence, which is what the splitter is supposed to avoid.
 */
public class Diag4 {
    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args[0]);
        TokenCountEstimator est = TokenEstimatorFactory.create(AdiConstant.TokenEstimator.OPENAI);
        CjkSentenceSplitter probe = new CjkSentenceSplitter(512, 0, est);

        List<Path> files;
        try (Stream<Path> s = Files.walk(dir)) {
            files = s.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".md"))
                    .sorted().collect(Collectors.toList());
        }
        int total = 0, maxTokens = 0;
        String worst = "";
        int[] thresholds = {256, 512, 1000};
        int[] over = new int[thresholds.length];
        for (Path f : files) {
            String text = Files.readString(f, StandardCharsets.UTF_8);
            for (String piece : probe.split(text)) {
                total++;
                int t = est.estimateTokenCountInText(piece);
                if (t > maxTokens) { maxTokens = t; worst = piece; }
                for (int i = 0; i < thresholds.length; i++) if (t > thresholds[i]) over[i]++;
            }
        }
        System.out.println("primary pieces (sentences/lines)=" + total + " maxTokens=" + maxTokens);
        for (int i = 0; i < thresholds.length; i++) {
            System.out.printf("  pieces over %-5d tokens: %d (%.2f%%)%n",
                    thresholds[i], over[i], 100.0 * over[i] / total);
        }
        System.out.println("longest piece: " + (worst.length() > 90 ? worst.substring(0, 90) + "…" : worst));
    }
}
