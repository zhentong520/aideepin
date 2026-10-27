package com.moyz.adi.common.util;

import com.moyz.adi.common.constant.AdiConstant;
import com.moyz.adi.common.file.FileOperatorContext;
import com.moyz.adi.common.vo.TtsSetting;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.function.Consumer;

@Slf4j
public class TtsUtil {

    private static final int STREAM_BUFFER_SIZE = 2048;
    private static final int MIN_CALLBACK_BUFFER_SIZE = 16 * 1024;

    /**
     * S1-09（2026-10-27）：共享带超时的 HTTP 客户端。
     * <p>
     * 历史上这里每次调用 {@code new RestTemplate()}（默认无任何超时），TTS 端挂起会让
     * SSE 回调线程无限阻塞、逐次占满线程池。改为进程内共享单例：
     * connect/read 均 60s（read timeout 是"两次读之间"的间隔上限，长音频流式下载不受总时长限制）。
     * TTS 服务类（OpenAiTtsService/SiliconflowTtsService）由工厂创建、非容器管理，
     * 无法注入 BeanConfig#restTemplate，故此处自持一份等价配置；若未来统一，可改由调用方注入。
     */
    private static final RestTemplate REST_TEMPLATE = createRestTemplate();

    private static RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(60_000);
        requestFactory.setReadTimeout(60_000);
        return new RestTemplate(requestFactory);
    }

    public static boolean needTts(TtsSetting ttsSetting, int answerContentType) {
        return AdiConstant.TtsConstant.SYNTHESIZER_SERVER.equals(ttsSetting.getSynthesizerSide()) && answerContentType == AdiConstant.CharacterConstant.ANSWER_CONTENT_TYPE_AUDIO;
    }

    /**
     * Execute TTS HTTP request, stream the audio response, buffer and callback chunks, then save to file.
     *
     * @param url             request URL
     * @param headers         HTTP headers
     * @param requestBody     JSON request body bytes
     * @param modelName       model name used for audio file naming (should be pre-sanitized)
     * @param processCallback callback to send audio chunks
     * @param completeCallback callback with saved file path on completion
     */
    public static void executeAndSave(String url, HttpHeaders headers, byte[] requestBody,
                                      String modelName, Consumer<ByteBuffer> processCallback,
                                      Consumer<String> completeCallback) {
        ResponseExtractor<Void> responseExtractor = response -> {
            String datetime = LocalDateTimeUtil.format(LocalDateTime.now(), LocalDateTimeUtil.PATTERN_YYYYMMDDMMHHSS);
            String fileName = modelName + "-" + datetime + "-" + UuidUtil.createShort().substring(0, 6) + ".mp3";
            Path tempFile = Files.createTempFile("tmp-tts-" + datetime, ".mp3");
            try {
                try (InputStream inputStream = response.getBody();
                     OutputStream outputStream = Files.newOutputStream(tempFile);
                     BufferedOutputStream bufferedOutput = new BufferedOutputStream(outputStream)) {
                    ByteArrayOutputStream sendBuffer = new ByteArrayOutputStream();
                    byte[] buffer = new byte[STREAM_BUFFER_SIZE];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        bufferedOutput.write(buffer, 0, bytesRead);
                        sendBuffer.write(buffer, 0, bytesRead);
                        if (sendBuffer.size() >= MIN_CALLBACK_BUFFER_SIZE) {
                            processCallback.accept(ByteBuffer.wrap(sendBuffer.toByteArray()));
                            sendBuffer.reset();
                        }
                    }
                    if (sendBuffer.size() > 0) {
                        processCallback.accept(ByteBuffer.wrap(sendBuffer.toByteArray()));
                    }
                }
                Pair<String, String> pair = new FileOperatorContext().save(Files.readAllBytes(tempFile), false, fileName);
                log.info("File saved successfully, path: {}", pair.getLeft());
                completeCallback.accept(pair.getLeft());
            } finally {
                Files.deleteIfExists(tempFile);
            }
            return null;
        };
        REST_TEMPLATE.execute(url, HttpMethod.POST, requestCallback -> {
            requestCallback.getHeaders().putAll(headers);
            requestCallback.getBody().write(requestBody);
        }, responseExtractor);
    }
}
