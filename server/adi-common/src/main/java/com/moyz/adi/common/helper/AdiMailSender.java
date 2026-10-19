package com.moyz.adi.common.helper;

import com.moyz.adi.common.config.AdiProperties;
import com.moyz.adi.common.vo.CustomMailInfo;
import jakarta.annotation.Resource;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

@Slf4j
@Service
public class AdiMailSender {

    /** 上游默认占位符：出现即视为"未配置真实 SMTP" */
    private static final String PLACEHOLDER_HOST = "your-email-host";
    private static final String PLACEHOLDER_USERNAME = "your-email-username";

    @Resource
    private JavaMailSender javaMailSender;

    @Resource
    private AdiProperties adiProperties;

    @Value("${spring.application.name}")
    private String appName;

    @Value("${spring.mail.host:}")
    private String mailHost;

    @Value("${spring.mail.username:}")
    private String senderMail;

    @Value("${local.base-data-path:E:/reg-project/data}")
    private String baseDataPath;

    public boolean send(String subject, String content, String to) {
        return this.send(subject, content, to, null);
    }

    /**
     * 发送邮件。
     * <p>
     * 返回值表示"邮件是否成功投递"。以前发送失败只会打日志并吞掉异常，
     * 注册/找回密码对外仍返回成功（假成功），用户永远收不到邮件。
     * 调用方（注册、找回密码）现在应检查返回值并向用户显式报错。
     *
     * @return true=已投递（或 mock 模式下已落盘）；false=发送失败
     */
    public boolean send(String subject, String content, String to, String cc) {
        log.info("mail sender:{}, to:{}, subject:{}", senderMail, to, subject);
        if (StringUtils.isAnyBlank(senderMail, to)) {
            log.warn("邮件发送参数缺失 senderMail/to 为空，跳过发送. subject:{}", subject);
            return false;
        }
        if (isMock()) {
            mockSend(subject, content, to, cc);
            return true;
        }
        CustomMailInfo customMailInfo = new CustomMailInfo();
        customMailInfo.setSenderMail(senderMail);
        customMailInfo.setSenderName(appName);
        customMailInfo.setCcMails(cc);
        customMailInfo.setToMails(to);
        customMailInfo.setSubject(subject);
        customMailInfo.setContent(content);
        return sendBySender(javaMailSender, customMailInfo);
    }

    public void customSend(CustomMailInfo customMailInfo) {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(customMailInfo.getHost());
        mailSender.setPort(customMailInfo.getPort());
        mailSender.setUsername(customMailInfo.getSenderMail());
        mailSender.setPassword(customMailInfo.getSenderPassword());
        mailSender.setDefaultEncoding(StandardCharsets.UTF_8.name());
        mailSender.setProtocol("smtps");
        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.smtp.ssl.enable", "true");
        sendBySender(mailSender, customMailInfo);
    }

    /**
     * 是否走 mock：显式开关 adi.mail.mock=true，或 spring.mail 仍是上游占位符（未配置）。
     */
    private boolean isMock() {
        return adiProperties.getMail().isMock()
                || PLACEHOLDER_HOST.equals(mailHost)
                || PLACEHOLDER_USERNAME.equals(senderMail);
    }

    /**
     * mock 发送：不连 SMTP。邮件全文打日志，并落盘到 {local.base-data-path}/mail-outbox/，
     * 本地开发时打开文件即可拿到激活链接 / 重置密码链接，注册与找回密码流程可以走通。
     */
    private void mockSend(String subject, String content, String to, String cc) {
        log.warn("[MAIL-MOCK] 未配置真实 SMTP（adi.mail.mock=true 或 spring.mail 为占位符），邮件不外发：");
        log.warn("[MAIL-MOCK] to={} cc={} subject={}", to, cc, subject);
        log.warn("[MAIL-MOCK] content:\n{}", content);
        try {
            File dir = new File(baseDataPath, "mail-outbox");
            if (!dir.exists() && !dir.mkdirs()) {
                log.warn("[MAIL-MOCK] 创建 outbox 目录失败: {}", dir.getAbsolutePath());
            }
            String name = "mail-" + new SimpleDateFormat("yyyyMMdd-HHmmss-SSS").format(new Date()) + ".txt";
            File file = new File(dir, name);
            try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
                pw.println("to: " + to);
                if (StringUtils.isNotBlank(cc)) {
                    pw.println("cc: " + cc);
                }
                pw.println("subject: " + subject);
                pw.println();
                pw.println(content);
            }
            log.warn("[MAIL-MOCK] 邮件已写入 {}", file.getAbsolutePath());
        } catch (Exception e) {
            log.error("[MAIL-MOCK] 邮件落盘失败", e);
        }
    }

    private boolean sendBySender(JavaMailSender mailSender, CustomMailInfo customMailInfo) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            InternetAddress fromAddress = new InternetAddress(customMailInfo.getSenderMail(), customMailInfo.getSenderName());
            helper.setFrom(fromAddress);
            if (StringUtils.isNotBlank(customMailInfo.getCcMails())) {
                helper.setCc(customMailInfo.getCcMails());
            }
            helper.setTo(customMailInfo.getToMails());
            helper.setSubject(customMailInfo.getSubject());
            helper.setText(customMailInfo.getContent());
            mailSender.send(message);
            return true;
        } catch (Exception e) {
            log.error("Exception occurred while sending email", e);
            return false;
        }
    }
}
