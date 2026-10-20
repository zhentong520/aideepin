package com.moyz.adi.common.memory;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 敏感记忆（PII）识别（T7-07）：抽取出的记忆文本在落库前先过本检测器，
 * 命中即进入 pending 确认态，用户确认后才持久化。
 * <p>
 * 覆盖任务书点名的三类：
 * <ul>
 *   <li><b>联系方式</b>：手机号、邮箱、QQ/微信号（{@link #TYPE_CONTACT}）；</li>
 *   <li><b>证件</b>：身份证号（15/18 位）、银行卡号（{@link #TYPE_ID_CARD} / {@link #TYPE_ACCOUNT}）；</li>
 *   <li><b>账号密码</b>：密码类关键词（{@link #TYPE_ACCOUNT}）。</li>
 * </ul>
 * <b>设计取向：宁可误报，不可漏报。</b>误报的代价是用户多点一次确认；
 * 漏报的代价是 PII 未经确认写入向量库、之后随检索进入 LLM 上下文——不可逆。
 * 关键词类（账号密码/联系方式）因此按「出现即命中」处理，不做上下文消歧。
 */
public final class MemoryPiiDetector {

    private MemoryPiiDetector() {
    }

    /** 联系方式：手机号/邮箱/QQ/微信 |
     * Contact info: mobile / email / QQ / WeChat. */
    public static final String TYPE_CONTACT = "CONTACT";

    /** 证件：身份证号 | Identity documents. */
    public static final String TYPE_ID_CARD = "ID_CARD";

    /** 账号密码：密码关键词/银行卡号 | Account secrets: password keywords / bank card numbers. */
    public static final String TYPE_ACCOUNT = "ACCOUNT";

    /** 中国大陆手机号：1 开头 11 位 | Mainland China mobile number. */
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");

    /** 邮箱 | Email address. */
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    /** 身份证号：18 位（末位可 X）或 15 位纯数字 | Chinese ID card number. */
    private static final Pattern ID_CARD = Pattern.compile("(?<![0-9Xx])\\d{17}[0-9Xx](?![0-9Xx])|(?<!\\d)\\d{15}(?!\\d)");

    /** 银行卡号：16-19 位连续数字（不做 Luhn 校验：保守方向是多拦，漏校验只可能少拦） |
     * Bank card: 16-19 consecutive digits (no Luhn check — erring toward over-matching). */
    private static final Pattern BANK_CARD = Pattern.compile("(?<!\\d)\\d{16,19}(?!\\d)");

    /** 账号密码关键词（中英文，大小写不敏感） | Password keywords. */
    private static final List<String> PASSWORD_KEYWORDS = List.of(
            "密码", "password", "passwd", "pwd");

    /** 联系方式关键词（中英文） | Contact keywords. */
    private static final List<String> CONTACT_KEYWORDS = List.of(
            "手机号", "电话号码", "电话", "微信", "wechat", "qq号", "qq", "邮箱", "email", "联系方式");

    /**
     * 返回文本命中的敏感类型列表（空列表 = 非敏感，可直写）。
     *
     * @param text 记忆文本（semantic fact 或 episodic summary）
     * @return 命中的类型码，如 {@code ["CONTACT","ACCOUNT"]}
     */
    public static List<String> detectTypes(String text) {
        List<String> types = new ArrayList<>(3);
        if (StringUtils.isBlank(text)) {
            return types;
        }
        String lower = text.toLowerCase();
        if (PHONE.matcher(text).find() || EMAIL.matcher(text).find() || containsAny(lower, CONTACT_KEYWORDS)) {
            types.add(TYPE_CONTACT);
        }
        if (ID_CARD.matcher(text).find()) {
            types.add(TYPE_ID_CARD);
        }
        if (containsAny(lower, PASSWORD_KEYWORDS) || BANK_CARD.matcher(text).find()) {
            types.add(TYPE_ACCOUNT);
        }
        return types;
    }

    /**
     * 是否为敏感记忆（需要用户确认后才可落库）。
     */
    public static boolean isSensitive(String text) {
        return !detectTypes(text).isEmpty();
    }

    private static boolean containsAny(String lowerText, List<String> keywords) {
        for (String keyword : keywords) {
            if (lowerText.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
