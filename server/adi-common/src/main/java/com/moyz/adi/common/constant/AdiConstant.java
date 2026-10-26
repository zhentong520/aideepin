package com.moyz.adi.common.constant;

/**
 * 兼容门面（OPT-17 拆分，2026-10-26）：常量本体已按域拆到同包的 8 个接口，
 * 本类实现它们以保持全仓 {@code AdiConstant.X} / {@code AdiConstant.嵌套类.X} /
 * static import / 静态星号导入引用零改动。
 * <p>
 * 域归属：
 * <ul>
 *   <li>{@link AuthConstant}：认证/账号（AUTH_*、PASSWORD_*、USER_TOKEN_EXPIRE 等）</li>
 *   <li>{@link GeneralConstant}：通用杂项（DEFAULT_PAGE_SIZE、WEB_RESOURCES、
 *       CharacterConstant、GenerateImage、存储/URL/列名等）</li>
 *   <li>{@link PromptConstant}：提示词模板（PROMPT_*）</li>
 *   <li>{@link ModelConstant}：模型平台（LLM_*、RESPONSE_FORMAT_*、ModelPlatform、
 *       ModelType、SearchEngineName、TokenEstimator、EmbeddingModel、McpConstant、
 *       TtsConstant、CustomChatRequestParameterKeys、W_FAILED）</li>
 *   <li>{@link KbRagConstant}：知识库/RAG（RAG_*、SplitStrategy、ChildChunkSize、
 *       SegmentSource、DOC_INDEX_TYPE_*、*_DOC_TYPES、DocSourceType、DocSourceLimit、
 *       GRAPH_*、EMPTY_PARSE/OCR_EMPTY_FAIL_REASON）</li>
 *   <li>{@link MetadataConstant}：向量库 metadata/记忆（MetadataKey、Sensitivity、
 *       MemoryEvent、RetrieveContentFrom、MAX_METADATA_VALUE_LENGTH 等）</li>
 *   <li>{@link SseConstant}：SSE 推流（SSE_TIMEOUT、SSEEventName、SSEEventData）</li>
 *   <li>{@link SysConfigConstant}：系统配置键（SysConfigKey）</li>
 * </ul>
 * <p>
 * 已随拆分删除的零引用成员（全仓 grep 验证）：SECRET_KEY_TYPE_SYSTEM/CUSTOM、
 * OPENAI_MESSAGE_DONE_FLAG、DEFAULT_MODEL、isSupportedDocType、
 * SysConfigKey 的 DEEPSEEK/OPENAI/DASHSCOPE/OLLAMA/SILICONFLOW_SETTING、
 * RAG_TYPE_KB/RAG_TYPE_SEARCH、
 * GRAPH_METADATA_IDENTIFY_COLUMNS/GRAPH_METADATA_APPEND_COLUMNS。
 * 注意：RAG_RETRIEVE_NUMBER_DEFAULT 虽在主代码仅一处使用，但经
 * {@code import static AdiConstant.*} 星号导入生效，已保留在 KbRagConstant。
 * 静态星号导入的成员删除前必须先检查星号导入方，普通 grep 引用统计会漏掉它们。
 * <p>
 * 新代码请直接引用域接口（如 {@code KbRagConstant.RAG_MIN_SCORE}），
 * 不要再新增对 AdiConstant 的依赖。
 */
public final class AdiConstant implements AuthConstant, GeneralConstant, PromptConstant,
        ModelConstant, KbRagConstant, MetadataConstant, SseConstant, SysConfigConstant {
    private AdiConstant() {
    }
}
