package com.moyz.adi.common.config;

import com.moyz.adi.common.config.props.Agent;
import com.moyz.adi.common.config.props.Acl;
import com.moyz.adi.common.config.props.Auth;
import com.moyz.adi.common.config.props.Chat;
import com.moyz.adi.common.config.props.Clean;
import com.moyz.adi.common.config.props.Credential;
import com.moyz.adi.common.config.props.EmailCode;
import com.moyz.adi.common.config.props.Encrypt;
import com.moyz.adi.common.config.props.Ext;
import com.moyz.adi.common.config.props.Ingest;
import com.moyz.adi.common.config.props.Kb;
import com.moyz.adi.common.config.props.Llm;
import com.moyz.adi.common.config.props.Mail;
import com.moyz.adi.common.config.props.Memory;
import com.moyz.adi.common.config.props.Ocr;
import com.moyz.adi.common.config.props.Proxy;
import com.moyz.adi.common.config.props.Rag;
import com.moyz.adi.common.config.props.Register;
import com.moyz.adi.common.config.props.Skill;
import com.moyz.adi.common.config.props.Wf;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * 启动期配置校验（ARCH-01，2026-10-16）。
 * 此前 40+ 字段无校验，写错（如 embeddingBatchSize 超平台上限、cooldown 为 0）
 * 要等到运行期才出现诡异行为。此处只校验「写错必然出错」的数值字段，
 * 不约束可空字符串（mock/dev 场景留空是合法的），避免过度约束。
 * <p>
 * OPT-17 拆分（2026-10-26）：原 870 行巨类中的 21 个嵌套 @Data 配置类全部提为
 * {@code com.moyz.adi.common.config.props} 包下的顶层类（Wf/Chat/Skill/Llm/Agent/
 * Credential/Ext/Kb/Acl/Register/EmailCode/Proxy/Encrypt/Ocr/
 * Ingest/Clean/Memory/Mail/Rag）。Spring @ConfigurationProperties 按属性名绑定
 * {@code adi.*}，嵌套类提为顶层类不影响任何绑定键；Lombok getter/setter 亦不变。
 * 各配置类的字段与双语注释见 props 包对应文件。
 */
@Configuration
@Validated
@ConfigurationProperties("adi")
@Data
public class AdiProperties {

    private String host;

    private String frontendUrl;

    private String backendUrl;

    /**
     * S2-05：部署角色 api | worker | all（默认 all=单实例全功能）。
     * 条件装配读取走 Environment（bean 装配期 AdiProperties 尚未就绪），
     * 见 {@code TaskQueueConsumer.RoleCondition} / {@code WorkerRoleHttpGuardFilter}；
     * 此字段仅供文档化与运行期读取。
     */
    private String role = "all";

    private Proxy proxy;

    private String embeddingModel;

    /**
     * 单次 embedAll 的文本条数上限 | Max texts sent to the embedding API per request.
     * <p>
     * 各平台硬上限不同：qianwen(compatible-mode) 单次最多 20、DashScope text-embedding 系列最多 10、
     * OpenAI 可到 2048。超限会直接 400（{@code batch size is invalid, it should not be larger than 20}），
     * 整个索引任务失败。默认 16 留了余量，切到别的平台时按需调小即可。
     */
    @Min(value = 1, message = "adi.embedding-batch-size 必须 >= 1")
    @Max(value = 2048, message = "adi.embedding-batch-size 超过 OpenAI 平台上限 2048")
    private int embeddingBatchSize = 16;

    private String vectorDatabase;

    private String graphDatabase;

    private Encrypt encrypt;

    /**
     * S1-06（2026-10-27）：鉴权配置（TokenFilter 匿名白名单等）。
     */
    @Valid
    private Auth auth = new Auth();

    /**
     * 长期记忆相关配置 | Long-term memory settings.
     */
    @Valid
    private Memory memory = new Memory();

    /**
     * 扫描件 OCR 相关配置 | Scanned-document OCR settings.
     */
    @Valid
    private Ocr ocr = new Ocr();

    /**
     * 入库（解析 / 清洗 / 切分）相关配置 | Ingest (parse / clean / split) settings.
     */
    @Valid
    private Ingest ingest = new Ingest();

    /**
     * 邮件发送相关配置 | Mail sending settings.
     */
    private Mail mail = new Mail();

    /**
     * 二开新增（T1-05，2026-10-16）：知识库 ACL 配置（铁律 10：行为改动必须带开关，可热回滚）。
     */
    @Valid
    private Kb kb = new Kb();

    /**
     * 二开新增（T1-09，2026-10-19）：External API（/ext/v1/** 与 /mcp/kb/**）限流与配额。
     */
    @Valid
    private Ext ext = new Ext();

    /**
     * 二开新增（T7-01，2026-10-20）：RAG 质量看板与失败闭环配置。
     */
    @Valid
    private Rag rag = new Rag();

    /**
     * 二开新增（T2-07，2026-10-19）：凭据集中管理配置（铁律 10：行为改动必须带开关，可热回滚）。
     */
    @Valid
    private Credential credential = new Credential();

    /**
     * 二开新增（T3-10/T3-13，2026-10-19）：工作流行为开关（铁律 10：行为改动必须带开关，可热回滚）。
     */
    @Valid
    private Wf wf = new Wf();

    /**
     * 二开新增（T6-05/T6-06，2026-10-20）：聊天/Agent 行为配置（铁律 10：行为改动必须带开关，可热回滚）。
     */
    @Valid
    private Chat chat = new Chat();

    /**
     * 二开新增（T6-07，2026-10-20）：技能注入配置。
     */
    @Valid
    private Skill skill = new Skill();

    /**
     * 二开新增（T6-08/T6-09，2026-10-20）：LLM 容错与降级路由配置。
     */
    @Valid
    private Llm llm = new Llm();

    /**
     * 二开新增（T6-10，2026-10-20）：Agent/工具循环预算配置。
     */
    @Valid
    private Agent agent = new Agent();

    /**
     * 注册相关配置 | Registration settings.
     */
    @Valid
    private Register register = new Register();
}
