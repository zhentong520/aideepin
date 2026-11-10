package com.moyz.adi.common.service.parse;

import com.moyz.adi.common.config.AdiProperties;
import com.moyz.adi.common.constant.AdiConstant;
import com.moyz.adi.common.entity.AdiFile;
import com.moyz.adi.common.entity.User;
import com.moyz.adi.common.file.FileOperatorContext;
import com.moyz.adi.common.helper.LLMContext;
import com.moyz.adi.common.languagemodel.AbstractLLMService;
import com.moyz.adi.common.vo.ChatModelBuilderProperties;
import com.moyz.adi.common.vo.ChatModelRequest;
import com.moyz.adi.common.vo.SseAskParam;
import com.moyz.adi.common.util.UuidUtil;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 图片 caption（二开新增，2026-11-05，调研报告 P2 的 caption-and-index 范式）。
 * <p>
 * 对解析出的文档内嵌图片调用 vision LLM 生成「结论式描述」（如「营收环比降 14%」，
 * 而非「一张柱状图」），写入 markdown 的 alt 文本——图文一起被向量化，检索命中图片
 * 所在段落时原句携带 {@code ![描述](/file/uuid.ext)} 链接，前端可展示原图。
 * <p>
 * 约束：
 * <ul>
 *   <li>开关 {@code adi.parse.image-caption-enabled}，默认关——不上传流程多一次 LLM 调用；</li>
 *   <li>best-effort：任何失败返回 null，调用方退回通用 alt，绝不让 caption 失败毁掉上传；</li>
 *   <li>调用 {@link AbstractLLMService#chat}（阻塞式），模型类型限定 vision。</li>
 * </ul>
 */
@Slf4j
@Service
public class ImageCaptionService {

    private static final String CAPTION_PROMPT = """
            请用一到两句简体中文概括这张图片承载的关键信息与结论（包含关键数字、趋势、对比结果等），\
            让人不需要看图也能理解它在文档中的作用。直接输出描述本身，不要任何前缀或解释。""";

    private static final int MAX_CAPTION_LENGTH = 300;

    private final AdiProperties adiProperties;

    public ImageCaptionService(AdiProperties adiProperties) {
        this.adiProperties = adiProperties;
    }

    /**
     * @return 结论式描述；开关关闭 / 无可用 vision 模型 / 调用失败时返回 null
     */
    public String caption(User user, AdiFile imageFile) {
        if (!adiProperties.getParse().isImageCaptionEnabled() || null == imageFile) {
            return null;
        }
        AbstractLLMService visionService = resolveVisionService();
        if (null == visionService) {
            log.info("Image caption skipped: no enabled vision model");
            return null;
        }
        try {
            String imageUrl = FileOperatorContext.getFileUrl(imageFile);
            SseAskParam param = SseAskParam.builder()
                    .user(user)
                    .uuid(UuidUtil.createShort())
                    .modelProperties(ChatModelBuilderProperties.builder().temperature(0.1).build())
                    .httpRequestParams(ChatModelRequest.builder()
                            .systemMessage("你是文档图片理解助手。")
                            .userMessage(CAPTION_PROMPT)
                            .imageUrls(List.of(imageUrl))
                            .build())
                    .build();
            ChatResponse response = visionService.chat(param);
            String caption = StringUtils.trimToNull(response.aiMessage().text());
            if (null == caption) {
                return null;
            }
            // markdown alt 文本里不能出现换行与右括号，否则链接语法被破坏
            caption = caption.replaceAll("[\\r\\n\\]]", " ").trim();
            return StringUtils.substring(caption, 0, MAX_CAPTION_LENGTH);
        } catch (Exception e) {
            log.warn("Image caption failed for file {}: {}", imageFile.getUuid(), e.getMessage());
            return null;
        }
    }

    /**
     * 找 caption 用的 vision 模型：配置了名字按名字取，否则取第一个启用的 vision 类型服务。
     * LLM 服务是启动期注册的静态上下文，这里只读、不抛出。
     */
    private AbstractLLMService resolveVisionService() {
        String preferred = adiProperties.getParse().getImageCaptionModel();
        AbstractLLMService firstVision = null;
        for (AbstractLLMService service : LLMContext.getAllServices()) {
            if (null == service.getAiModel() || !service.isEnabled()) {
                continue;
            }
            if (!AdiConstant.ModelType.VISION.equals(service.getAiModel().getType())) {
                continue;
            }
            if (StringUtils.isNotBlank(preferred)) {
                if (preferred.equals(service.getAiModel().getName())) {
                    return service;
                }
            } else if (null == firstVision) {
                firstVision = service;
            }
        }
        return firstVision;
    }
}
