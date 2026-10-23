import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;

import java.util.Arrays;

/**
 * Probe: verify the embedding endpoint actually works from this classpath,
 * before the eval harness depends on it.
 * args: baseUrl apiKey model dimensions
 */
public class EmbProbe {

    static double cosine(float[] x, float[] y) {
        double dot = 0, nx = 0, ny = 0;
        for (int i = 0; i < x.length; i++) { dot += x[i] * y[i]; nx += x[i] * x[i]; ny += y[i] * y[i]; }
        return dot / (Math.sqrt(nx) * Math.sqrt(ny));
    }

    public static void main(String[] args) {
        String baseUrl = args[0], apiKey = args[1], model = args[2];
        int dim = Integer.parseInt(args[3]);
        EmbeddingModel m = OpenAiEmbeddingModel.builder()
                .baseUrl(baseUrl).apiKey(apiKey).modelName(model).dimensions(dim).build();

        float[] q = m.embed("知识库修改切段参数会有什么后果").content().vector();
        System.out.println("[DIM] " + q.length + " first5=" + Arrays.toString(Arrays.copyOf(q, 5)));

        String[] cands = {
                "切段参数变更将自动重建该知识库全部文档的向量索引",
                "上传的单个文件大小不能超过 10MB",
                "今天股市大盘收盘上涨了百分之一"
        };
        for (String c : cands) {
            System.out.printf("  %.4f  <- %s%n", cosine(q, m.embed(c).content().vector()), c);
        }
        System.out.println("PROBE_OK");
    }
}
