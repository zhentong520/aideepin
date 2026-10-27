package com.moyz.adi.common.util;

import com.moyz.adi.common.exception.UnsafeUrlException;
import org.apache.commons.lang3.StringUtils;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * SSRF 防护统一入口：校验 URL 的协议与解析出来的每一个 IP，禁止指向内网/本机/链路本地/云元数据。
 *
 * <p>背景：工作流 HTTP 请求节点、远程图片保存（本地/OSS）、KB 文档抓取等场景都会以用户可配置的
 * URL 发起服务端请求。若不校验，攻击者可把 URL 指向 {@code 127.0.0.1}、
 * {@code 169.254.169.254}（云厂商元数据）等地址探测内网或窃取云凭证。
 *
 * <p>校验失败抛 {@link UnsafeUrlException}，调用方按自身语义翻译错误码（各处错误枚举不同，
 * 因此本类不依赖任何 ErrorEnum）。
 *
 * <p>注意：这里只做「发起请求前」的静态校验；重定向场景每一跳的目标都必须重新校验
 * （自动跟随重定向只校验首跳，公网 302 跳内网不会再过防护），见各调用方的逐跳复检循环。
 */
public final class SafeUrlChecker {

    /** 最大重定向跳数，供各调用方的手动跟随循环复用，防止重定向环拖垮服务 */
    public static final int MAX_REDIRECTS = 5;

    private SafeUrlChecker() {
    }

    /**
     * 校验 URL 可安全地作为服务端出站请求目标，不安全时抛 {@link UnsafeUrlException}。
     *
     * @param url 待校验 URL（首跳必须为绝对地址）
     * @throws UnsafeUrlException 协议非 http/https、格式非法、域名不可解析或解析结果含内网地址
     */
    public static void check(String url) {
        if (StringUtils.isBlank(url)) {
            throw new UnsafeUrlException(UnsafeUrlException.Reason.BLANK, null, "URL 为空");
        }
        URI uri;
        try {
            uri = new URI(url.trim());
        } catch (Exception e) {
            throw new UnsafeUrlException(UnsafeUrlException.Reason.MALFORMED, null, "URL 格式非法");
        }
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new UnsafeUrlException(UnsafeUrlException.Reason.SCHEME_NOT_ALLOWED, null,
                    "协议必须是 http/https，实际：" + scheme);
        }
        String host = uri.getHost();
        if (StringUtils.isBlank(host)) {
            throw new UnsafeUrlException(UnsafeUrlException.Reason.HOST_MISSING, null, "URL 缺少主机名");
        }
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new UnsafeUrlException(UnsafeUrlException.Reason.UNRESOLVABLE, host, "域名解析失败：" + host);
        }
        for (InetAddress address : addresses) {
            if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress() || address.isMulticastAddress()
                    || "169.254.169.254".equals(address.getHostAddress())) {
                throw new UnsafeUrlException(UnsafeUrlException.Reason.PRIVATE_ADDRESS, host,
                        "禁止访问内网/本机地址：" + host);
            }
        }
    }
}
