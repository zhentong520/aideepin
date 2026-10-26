package com.moyz.adi.common.service;

import com.moyz.adi.common.entity.DbDatasource;
import com.moyz.adi.common.exception.BaseException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DbDatasourceService} 连接前校验的回归测试（2026-10-15）。
 *
 * <p>背景：host 与 jdbcParams 完全由用户填写，{@code testConnection} 直接用请求体构建连接，
 * 是「让服务器按用户指令发起出站连接」的入口。此前没有任何校验：
 * 可探测服务器本机端口（响应差异盲扫）、可注入 {@code socketFactory} 等
 * 高危 JDBC 参数（类加载 / 读本地文件 / 反序列化）。
 *
 * <p><b>刻意保留私网段</b>：功能定位就是连接企业内网数据库，禁私网等于废掉核心场景；
 * 这里只拦 loopback / 链路本地 / 云元数据与高危参数。
 */
class DbDatasourceGuardTest {

    private final DbDatasourceService service = new DbDatasourceService();

    private void invokeHost(String host) throws Exception {
        Method m = DbDatasourceService.class.getDeclaredMethod("assertConnectableHost", String.class);
        m.setAccessible(true);
        try {
            m.invoke(service, host);
        } catch (java.lang.reflect.InvocationTargetException te) {
            throw (Exception) te.getCause();
        }
    }

    private void invokeParams(String params) throws Exception {
        Method m = DbDatasourceService.class.getDeclaredMethod("assertSafeJdbcParams", String.class);
        m.setAccessible(true);
        try {
            m.invoke(service, params);
        } catch (java.lang.reflect.InvocationTargetException te) {
            throw (Exception) te.getCause();
        }
    }

    // ------------------------------------------------------------------
    // host 校验
    // ------------------------------------------------------------------

    @DisplayName("loopback / 链路本地 / 云元数据地址拒绝（IP 字面量，不触发 DNS）")
    @ParameterizedTest(name = "{0} 应拒绝")
    @ValueSource(strings = {
            "127.0.0.1",
            "localhost",
            "169.254.169.254",
            "0.0.0.0",
            "[::1]",
    })
    void loopbackAndMetadataRejected(String host) {
        assertThrows(BaseException.class, () -> invokeHost(host),
                host + " 指向服务器自身或云元数据，必须拒绝");
    }

    @DisplayName("私网段保留：企业内网数据库是本功能核心场景")
    @ParameterizedTest(name = "{0} 应放行")
    @ValueSource(strings = {
            "10.0.0.5",
            "172.16.1.10",
            "192.168.1.100",
            "172.17.30.40",
    })
    void privateNetworkAllowed(String host) {
        assertDoesNotThrow(() -> invokeHost(host),
                host + " 是合法的内网数据库地址，禁掉会废掉功能");
    }

    @DisplayName("公网地址放行")
    @Test
    void publicHostAllowed() {
        assertDoesNotThrow(() -> invokeHost("1.1.1.1"));
    }

    @DisplayName("host 为空拒绝")
    @Test
    void blankHostRejected() {
        assertThrows(BaseException.class, () -> invokeHost(" "));
    }

    // ------------------------------------------------------------------
    // jdbcParams 校验
    // ------------------------------------------------------------------

    @DisplayName("高危 JDBC 参数拒绝（类加载/本地文件读取/反序列化）")
    @ParameterizedTest(name = "{0} 应拒绝")
    @ValueSource(strings = {
            "socketFactory=org.attack.Evil",
            "socketFactory=org.attack.Evil&ssl=true",
            "SocketFactoryArg=com.x.Y",
            "allowLoadLocalInfile=true",
            "allowUrlInLocalInfile=true",
            "useLocalInfile=true",
            "autoDeserialize=true",
            "loggerFile=/etc/passwd",
            "loggerLevel=DEBUG",
            "?socketFactory=evil",
            "sslmode=require&socketFactory=evil",
    })
    void dangerousJdbcParamsRejected(String params) {
        assertThrows(BaseException.class, () -> invokeParams(params),
                params + " 含高危参数，必须拒绝");
    }

    @DisplayName("常规参数放行：白名单会打断现有用户配置，所以走黑名单")
    @ParameterizedTest(name = "{0} 应放行")
    @ValueSource(strings = {
            "sslmode=require",
            "?useSSL=false&serverTimezone=UTC",
            "characterEncoding=utf8&connectTimeout=10",
            "options=-c%20search_path%3Dpublic",
            "ApplicationName=my-app",
    })
    void normalJdbcParamsAllowed(String params) {
        assertDoesNotThrow(() -> invokeParams(params));
    }

    @DisplayName("空参数放行")
    @Test
    void blankParamsAllowed() {
        assertDoesNotThrow(() -> invokeParams(null));
        assertDoesNotThrow(() -> invokeParams(""));
    }

    @DisplayName("拒绝时抛的是业务异常（走统一错误码，不裸抛）")
    @Test
    void rejectionIsBusinessException() {
        Exception e = assertThrows(BaseException.class, () -> invokeParams("socketFactory=evil"));
        assertTrue(e.getMessage() != null && !e.getMessage().isBlank());
    }
}
