package com.moyz.adi.common.service.biztool;

import com.moyz.adi.common.interfaces.BizToolProvider;
import com.moyz.adi.common.interfaces.BizToolSpi;
import dev.langchain4j.agent.tool.ToolSpecification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BizToolService 前缀路由的回归测试（T9-M2，2026-10-29）。
 *
 * <p>路由约定：工具名第一个 '_' 之前是插件码，按码找到 provider 后整名透传。
 * 这里用纯 Mockito 风格的假 provider 验证三条铁律：
 * 1. prepare 只拼 plugin_ids 里勾选的插件（未勾选的不进 manifest/规格）；
 * 2. plugin_ids 配了不存在的插件码——跳过而不是抛异常（配置错误不应 500）；
 * 3. execute 按前缀路由，无匹配前缀返回 ERROR 字符串而不是抛异常。
 */
class BizToolServiceRoutingTest {

    /** 假 provider：code/name 可定制，execute 回显收到的一切，方便断言路由落点。 */
    private static class FakeProvider implements BizToolProvider {
        private final String code;
        private final String echo;

        FakeProvider(String code, String echo) {
            this.code = code;
            this.echo = echo;
        }

        @Override
        public String code() {
            return code;
        }

        @Override
        public String title() {
            return code + "-title";
        }

        @Override
        public String manifest() {
            return "MANIFEST::" + code;
        }

        @Override
        public List<ToolSpecification> toolSpecifications() {
            return List.of(ToolSpecification.builder()
                    .name(code + "_probe")
                    .description("probe of " + code)
                    .build());
        }

        @Override
        public String execute(String toolName, String argsJson, Long userId, String characterUuid) {
            return echo + "|" + toolName + "|" + argsJson + "|" + userId + "|" + characterUuid;
        }
    }

    private BizToolService serviceWith(BizToolProvider... providers) {
        BizToolService service = new BizToolService();
        ReflectionTestUtils.setField(service, "providers", List.of(providers));
        return service;
    }

    // ------------------------------------------------------------------
    // prepare：按 pluginCodes 过滤
    // ------------------------------------------------------------------

    @DisplayName("prepare 空插件码返回空 bundle")
    @Test
    void prepareEmptyCodes() {
        BizToolSpi.ToolBundle bundle = serviceWith(new FakeProvider("mall", "M")).prepare(List.of());
        assertEquals("", bundle.manifest());
        assertTrue(bundle.toolSpecifications().isEmpty());
        bundle = serviceWith(new FakeProvider("mall", "M")).prepare(null);
        assertEquals("", bundle.manifest());
        assertTrue(bundle.toolSpecifications().isEmpty());
    }

    @DisplayName("prepare 只拼勾选插件的 manifest 与工具规格")
    @Test
    void prepareFiltersByCode() {
        BizToolService service = serviceWith(new FakeProvider("mall", "M"), new FakeProvider("trip", "T"));
        BizToolSpi.ToolBundle bundle = service.prepare(List.of("mall"));
        assertEquals("MANIFEST::mall\n\n", bundle.manifest());
        assertEquals(1, bundle.toolSpecifications().size());
        assertEquals("mall_probe", bundle.toolSpecifications().get(0).name());
    }

    @DisplayName("prepare 多插件全量拼接且保持勾选顺序")
    @Test
    void prepareConcatenatesAll() {
        BizToolService service = serviceWith(new FakeProvider("mall", "M"), new FakeProvider("trip", "T"));
        BizToolSpi.ToolBundle bundle = service.prepare(List.of("trip", "mall"));
        assertTrue(bundle.manifest().startsWith("MANIFEST::trip"), "manifest 顺序应与勾选顺序一致");
        assertTrue(bundle.manifest().contains("MANIFEST::mall"));
        assertEquals(2, bundle.toolSpecifications().size());
    }

    @DisplayName("prepare 含未知插件码：跳过该码，其余照常，不抛异常")
    @Test
    void prepareSkipsUnknownCode() {
        BizToolService service = serviceWith(new FakeProvider("mall", "M"));
        BizToolSpi.ToolBundle bundle = service.prepare(List.of("ghost", "mall"));
        assertTrue(bundle.manifest().contains("MANIFEST::mall"));
        assertEquals(1, bundle.toolSpecifications().size());
    }

    // ------------------------------------------------------------------
    // execute：前缀路由
    // ------------------------------------------------------------------

    @DisplayName("execute 按第一个 '_' 前缀路由到对应 provider，并整名透传")
    @Test
    void executeRoutesByPrefix() {
        BizToolService service = serviceWith(new FakeProvider("mall", "M"), new FakeProvider("trip", "T"));
        String result = service.execute("trip_query_hotels", "{\"city\":\"Sanya\"}", 42L, "char-uuid");
        assertEquals("T|trip_query_hotels|{\"city\":\"Sanya\"}|42|char-uuid", result);
    }

    @DisplayName("execute 前缀不在容器内返回 ERROR 字符串，不抛异常")
    @Test
    void executeUnknownPrefix() {
        BizToolService service = serviceWith(new FakeProvider("mall", "M"));
        String result = service.execute("ghost_tool", "{}", 42L, "char-uuid");
        assertTrue(result.startsWith("ERROR: unknown plugin"), result);
    }

    @DisplayName("execute 无下划线工具名返回 ERROR 字符串，不抛异常")
    @Test
    void executeNoPrefix() {
        BizToolService service = serviceWith();
        String result = service.execute("noplugin", "{}", 42L, "char-uuid");
        assertTrue(result.startsWith("ERROR: unknown tool"), result);
    }
}
