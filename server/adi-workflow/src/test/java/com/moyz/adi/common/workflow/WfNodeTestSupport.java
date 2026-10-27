package com.moyz.adi.common.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyz.adi.common.entity.User;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;
import java.util.Locale;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * P3 新节点（T9-02/03/04/07）单测支撑：装配无容器的最小 Spring 桩。
 * <ul>
 *   <li>MessageSource 桩：BaseException 取文案不 NPE（返回带 {0} 的模板，参数可还原）；</li>
 *   <li>beanValidator：真实 {@link LocalValidatorFactoryBean}（jakarta 校验在单测可用）；</li>
 *   <li>SseManager.selfInstance 桩：S1-02 后静态方法（parseAndSendPartialMsg 等）经
 *       selfInstance 访问实例状态，无容器环境下 @PostConstruct 不会触发，这里显式
 *       推入一个空实例（未注册 uuid → getEntry 返回 null → 静态方法安全早退）；</li>
 *   <li>不提供 AdiProperties bean → WfFeatureGate 兜底为「开启」（与生产缺省一致）。</li>
 * </ul>
 */
public final class WfNodeTestSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private WfNodeTestSupport() {
    }

    /** 装配 SpringUtil 桩（BeforeEach 调用）；返回装配好的 ApplicationContext 以便测试追加 bean */
    public static ApplicationContext install() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        MessageSource messageSource = new MessageSource() {
            @Override
            public String getMessage(String code, Object[] args, String defaultMessage, Locale locale) {
                return code + ": {0}";
            }

            @Override
            public String getMessage(String code, Object[] args, Locale locale) {
                return code + ": {0}";
            }

            @Override
            public String getMessage(MessageSourceResolvable resolvable, Locale locale) {
                return String.join(",", resolvable.getCodes() == null ? new String[0] : resolvable.getCodes());
            }
        };
        ApplicationContext context = mock(ApplicationContext.class);
        when(context.getBean(MessageSource.class)).thenReturn(messageSource);
        //Code 节点沙箱根目录指向临时目录，避免测试写 /data/aideepin（与生产 SkillToolService 同配置键）
        org.springframework.mock.env.MockEnvironment env = new org.springframework.mock.env.MockEnvironment();
        env.setProperty("local.base-data-path",
                System.getProperty("java.io.tmpdir") + "/adi-wf-test-sandbox");
        when(context.getEnvironment()).thenReturn(env);
        ReflectionTestUtils.setField(com.moyz.adi.common.util.SpringUtil.class, "applicationContext", context);
        //S1-02：SseManager 静态方法改走 selfInstance（@PostConstruct 注入），无容器测试需手动推桩
        ReflectionTestUtils.setField(com.moyz.adi.common.helper.SseManager.class, "selfInstance",
                new com.moyz.adi.common.helper.SseManager());
        //beanValidator 按名获取：checkAndGetConfig 用它做 jakarta 校验
        org.mockito.Mockito.doReturn(validator).when(context).getBean("beanValidator", LocalValidatorFactoryBean.class);
        return context;
    }

    /** 卸载桩（AfterEach 调用，防止跨测试类泄漏静态上下文） */
    public static void uninstall() {
        ReflectionTestUtils.setField(com.moyz.adi.common.util.SpringUtil.class, "applicationContext", null);
        ReflectionTestUtils.setField(com.moyz.adi.common.helper.SseManager.class, "selfInstance", null);
    }

    public static User user(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    public static WfState wfState(User user) {
        return new WfState(user, List.of(), "test-wf-state");
    }

    public static com.fasterxml.jackson.databind.node.ObjectNode config(String json) {
        try {
            return (com.fasterxml.jackson.databind.node.ObjectNode) MAPPER.readTree(json);
        } catch (Exception e) {
            throw new IllegalArgumentException("bad config json: " + json, e);
        }
    }
}
