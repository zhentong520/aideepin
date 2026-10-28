package com.moyz.adi.common.constant;

public class RedisKeyConstant {

    private RedisKeyConstant() {
    }

    /**
     * 账号激活码的key
     */
    public static final String AUTH_ACTIVE_CODE = "auth:activeCode:{0}";

    /**
     * 注册时使用的验证码
     * 参数：验证码id
     * 值：验证码
     */
    public static final String AUTH_REGISTER_CAPTCHA_ID = "auth:register:captcha:{0}";

    /**
     * 登录时使用的验证码id缓存
     * 参数：验证码id
     * 值：验证码
     */
    public static final String AUTH_LOGIN_CAPTCHA_ID = "auth:login:captcha:{0}";

    /**
     * 注册验证码缓存
     * 参数：验证码
     * 值：1
     */
    public static final String AUTH_CAPTCHA = "auth:register:captcha:{0}";

    /**
     * 注册邮箱验证码（二开 2026-09-28）
     * 参数：邮箱
     * 值：验证码
     * <p>
     * Registration email verification code. Parameter: email. Value: code.
     */
    public static final String AUTH_REGISTER_EMAIL_CODE = "auth:register:email-code:{0}";

    /**
     * 注册邮箱验证码的重发间隔标记（二开 2026-09-28）
     * 参数：邮箱
     * 值：1
     * <p>
     * Resend-interval marker for the registration email code. Parameter: email. Value: 1.
     */
    public static final String AUTH_REGISTER_EMAIL_CODE_COOLDOWN = "auth:register:email-code:cooldown:{0}";


    /**
     * 登录token
     * {0}:用户token
     * 值：json.format(user)
     */
    public static final String USER_TOKEN = "user:token:{0}";

    /**
     * 参数：游客的uuid
     * 值：json.format(guest)
     */
    public static final String GUEST_UUID = "guest:uuid:{0}";

    /**
     * 登录失败次数
     * 参数：用户邮箱
     * 值: 失效次数
     */
    public static final String LOGIN_FAIL_COUNT = "user:login:fail:{0}";

    /**
     * 用户是否请求ai中（已废弃，由 USER_ACTIVE_SSE_COUNT 替代）
     */
    @Deprecated
    public static final String USER_ASKING = "user:asking:{0}";

    /**
     * 用户活跃 SSE 连接计数（替代 USER_ASKING 的 0/1 锁）
     * 参数：用户id
     * 值: 当前并发 SSE 数量
     * <p>
     * Active SSE connection counter per user (replaces USER_ASKING 0/1 lock).
     * Parameter: user id. Value: current concurrent SSE count.
     * </p>
     */
    public static final String USER_ACTIVE_SSE_COUNT = "user:active-sse:{0}";

    /**
     * 用户是否画画中
     * 参数：用户id
     * 值: 1或者0
     */
    public static final String USER_DRAWING = "user:drawing:{0}";

    /**
     * 用户提问限流计数
     * 参数：用户id
     * 值: 当前时间窗口访问量
     */
    public static final String USER_REQUEST_TEXT_TIMES = "user:request-text:times:{0}";

    public static final String USER_REQUEST_IMAGE_TIMES = "user:request-image:times:{0}";

    /**
     * 用户信息缓存
     * 参数：用户id
     * 值: user object
     */
    public static final String USER_INFO = "user:info:";
    /** S2-04：短期记忆缓存（值为 langchain4j 序列化消息窗口，写穿透 TTL 30min）| short-term chat memory cache */
    public static final String CHAT_MEMORY = "chat:memory:";
    /** S2-06：SSE 跨实例分发 pub/sub 频道（值为 SsePushMessage JSON，广播制、实例本地按持有连接过滤）| SSE cross-instance fan-out channel */
    public static final String SSE_PUSH_CHANNEL = "sse:push:channel";

    /**
     * 找回密码的请求绑在
     * 参数：随机数
     * 值: 用户id，用于校验后续流程中的重置密码使用
     */
    public static final String FIND_MY_PASSWORD = "user:find:password:{0}";

    /**
     * 找回密码发送冷却（S-08：防邮件轰炸，同一邮箱冷却期内只能发一次）
     * 参数：邮箱
     * 值：占位符 "1"
     */
    public static final String FIND_MY_PASSWORD_COOLDOWN = "user:find:password:cooldown:{0}";

    /**
     * 某用户全部有效登录 token 的集合（S-08③：改密/重置后可按 userId 踢掉所有会话）
     * 参数：用户id
     * 值：token 集合（Set），成员过期依赖 USER_TOKEN_EXPIRE，写入时同步刷新集合 TTL
     */
    public static final String USER_TOKEN_SET = "user:tokens:{0}";

    /**
     * qa提问次数（每天）
     * 参数：用户id:日期yyyyMMdd
     * 值：提问数量
     */
    public static final String AQ_ASK_TIMES = "qa:ask:limit:{0}:{1}";

    /**
     * 知识库知识点生成数量
     * 值: 用户id
     */
    public static final String QA_ITEM_CREATE_LIMIT = "aq:item:create:{0}";

    /**
     * 信号(重新生成知识库统计数据)
     * 值:知识库uuid
     */
    public static final String KB_STATISTIC_RECALCULATE_SIGNAL = "kb:statistic:recalculate:signal";

    public static final String STATISTIC = "statistic";
    public static final String STATISTIC_USER = "user";
    public static final String STATISTIC_KNOWLEDGE_BASE = "kb";
    public static final String STATISTIC_TOKEN_COST = "token-cost";
    public static final String STATISTIC_CHARACTER = "character";
    public static final String STATISTIC_IMAGE_COST = "image-cost";

    public static final String TOKEN_USAGE_KEY = "token:usage:{0}";

    /**
     * 用户正在对文档进行索引
     * 值：用户id
     */
    public static final String USER_INDEXING = "user:indexing:{0}";

    /**
     * 用户评论并发限制
     * 值：用户id
     */
    public static final String DRAW_COMMENT_LIMIT_KEY = "user:draw:comment-submitting:{0}";

    public static final String WORKFLOW_KEY = "workflow";

    public static final String WORKFLOW_COMPONENTS = "workflow:components";

    public static final String WORKFLOW_COMPONENT_START_KEY = "workflow:component:start";

    public static final String WORKFLOW_COMPONENT_KEY = "workflow:component";

    public static final String WORKFLOW_COPY_DOING = "workflow:copy:doing:{0}";

    // ===== 二开新增（T9-01，2026-10-20）：工作流自动触发 =====

    /**
     * 定时触发派发防重锁（多实例/多轮询防重复派发）
     * 参数：triggerId；值：clientId；TTL 60s
     */
    public static final String WF_TRIGGER_FIRE_LOCK = "workflow:trigger:fire:{0}";

    /**
     * webhook 匿名限流（60 秒固定窗口）
     * 参数：token 摘要、epochMinute；值：窗口内请求数
     */
    public static final String WF_WEBHOOK_RATE = "workflow:webhook:rl:{0}:{1}";

    // ===== 二开新增（T1-09，2026-10-19）：External API 限流与配额 =====

    /**
     * ext key QPS 限流（1 秒固定窗口）
     * 参数：keyHash（SHA-256 前 32 位）、epochSecond
     * 值：窗口内请求数
     */
    public static final String EXT_RATE_LIMIT_QPS = "ext:rate:qps:{0}:{1}";

    /**
     * ext key 日调用次数配额
     * 参数：keyHash、日期 yyyyMMdd
     * 值：当日累计调用次数
     */
    public static final String EXT_DAILY_REQUEST_COUNT = "ext:quota:req:{0}:{1}";

    /**
     * ext key 日 token 消耗累计（prompt + completion）
     * 参数：keyHash、日期 yyyyMMdd
     * 值：当日累计 token 数
     */
    public static final String EXT_DAILY_TOKEN_COUNT = "ext:quota:token:{0}:{1}";
}
