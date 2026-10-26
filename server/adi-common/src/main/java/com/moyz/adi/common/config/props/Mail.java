package com.moyz.adi.common.config.props;

import lombok.Data;

/**
 * 邮件发送配置项。
 * <p>
 * Mail sending configuration.
 * <p>
 * OPT-17 拆分：原 {@code AdiProperties.Mail} 嵌套类提为顶层类，Spring 绑定键不变。
 */
@Data
public class Mail {
    /**
     * mock 模式：不连接 SMTP，邮件全文打日志并落盘到 {local.base-data-path}/mail-outbox/。
     * 用于本地开发（未配置 spring.mail 时，注册/找回密码流程也能走通）。
     * <p>
     * Mock mode: skip SMTP, log the mail and save it to {local.base-data-path}/mail-outbox/
     * so registration / password-reset flows work without a real SMTP server.
     */
    private boolean mock = false;
}
