package com.moyz.adi.common.memory.longterm;

import java.util.regex.Pattern;

/**
 * 二开新增（T7-07，2026-10-20）：敏感（PII）记忆检测器。
 * <p>
 * 命中以下任一特征的抽取记忆被视为敏感，写入前必须经用户确认
 * （{@code adi.memory.pii-confirm-enabled=true} 时进入 adi_memory_pending）：
 * <ul>
 *   <li>联系方式：中国大陆手机号、邮箱；</li>
 *   <li>证件：18/15 位身份证号；</li>
 *   <li>账号密码：文本自述含「密码/账号/账户/user name/password/token/密钥/secret」
 *       等关键词（LLM 抽取的事实里"我密码是xxx"这类自述必须拦下）。</li>
 * </ul>
 * 检测是纯函数（无 IO、无状态），刻意保守：宁可多拦（多一次用户确认）也不漏拦。
 * 规则仅覆盖抽取事实文本这一入口，不用于日志脱敏（那是 T6-12 的职责）。
 */
public final class SensitiveMemoryDetector {

    private SensitiveMemoryDetector() {
    }

    /** 中国大陆手机号（宽容匹配 11 位 1 开头，允许文本中带 - 或空格分隔）。 */
    private static final Pattern CN_MOBILE = Pattern.compile("(?<!\\d)1[3-9]\\d[ -]?\\d{4}[ -]?\\d{4}(?!\\d)");

    /** 电子邮箱。 */
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    /** 18 位（含校验位 X）或 15 位身份证号。 */
    private static final Pattern ID_CARD = Pattern.compile("(?<!\\d)(\\d{17}[0-9Xx]|\\d{15})(?!\\d)");

    /** 自述式账号密码关键词：事实文本里出现即视为携带凭据信息。 */
    private static final Pattern CREDENTIAL_KEYWORD = Pattern.compile(
            "(?i)(密码|口令|账号|帐号|账户|验证码|password|passwd|pwd|token|secret|api[ -]?key|密钥|凭据)");

    public static boolean isSensitive(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        return CN_MOBILE.matcher(content).find()
                || EMAIL.matcher(content).find()
                || ID_CARD.matcher(content).find()
                || CREDENTIAL_KEYWORD.matcher(content).find();
    }
}
