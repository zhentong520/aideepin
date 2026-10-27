package com.moyz.adi.common.exception;

import com.moyz.adi.common.util.SafeUrlChecker;
import lombok.Getter;

/**
 * {@link SafeUrlChecker} 的校验失败异常：携带拒绝原因与主机名，由调用方翻译为各自的业务错误码。
 */
@Getter
public class UnsafeUrlException extends RuntimeException {

    public enum Reason {
        /** URL 为空 */
        BLANK,
        /** URL 无法解析 */
        MALFORMED,
        /** 协议不在 http/https 白名单内 */
        SCHEME_NOT_ALLOWED,
        /** 缺少主机名 */
        HOST_MISSING,
        /** 域名解析失败 */
        UNRESOLVABLE,
        /** 解析结果指向内网/本机/链路本地/云元数据 */
        PRIVATE_ADDRESS
    }

    private final Reason reason;

    /** 被拒绝的主机名（BLANK/MALFORMED/SCHEME_NOT_ALLOWED/HOST_MISSING 时为 null） */
    private final String host;

    public UnsafeUrlException(Reason reason, String host, String message) {
        super(message);
        this.reason = reason;
        this.host = host;
    }
}
