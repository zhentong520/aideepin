package com.moyz.adi.common.interfaces;

import com.moyz.adi.common.dto.SearchReturn;
import com.moyz.adi.common.util.JsonUtil;
import com.moyz.adi.common.util.LocalCache;
import dev.langchain4j.web.search.WebSearchEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.net.InetSocketAddress;
import java.net.Proxy;

@Slf4j
public abstract class AbstractSearchEngineService<T> {

    protected String engineName;

    protected InetSocketAddress proxyAddress;

    protected WebSearchEngine searchEngine;

    /**
     * S1-02：由调用方（Spring bean）注入共享 RestTemplate，替代 SpringUtil 服务定位器。
     * 本类实例由 AiModelInitializer 手动 new（非 Spring bean），无法字段注入。
     */
    protected final RestTemplate restTemplate;

    protected AbstractSearchEngineService(String engineName, String settingName, Class<T> clazz,
                                          InetSocketAddress proxyAddress, RestTemplate restTemplate) {
        this.engineName = engineName;
        String st = LocalCache.CONFIGS.get(settingName);
        setting = JsonUtil.fromJson(st, clazz);
        this.proxyAddress = proxyAddress;
        this.restTemplate = restTemplate;
        // 2026-10-16：未启用的引擎（如未配 Google key）不再执行 initSearchEngine——
        // 此前 GoogleSearchEngineService.initSearchEngine 会对空 key 直接抛
        // IllegalArgumentException，导致全新空库首次启动直接失败。
        // isEnabled() 依赖 setting 已在上面赋值，此处调用安全。
        if (isEnabled()) {
            initSearchEngine();
        } else {
            log.info("Search engine [{}] not enabled (missing config), skip initializing", engineName);
        }
    }

    protected abstract void initSearchEngine();

    protected T setting;

    public abstract boolean isEnabled();

    public String getEngineName() {
        return engineName;
    }

    public abstract SearchReturn search(String searchTxt, String country, String language, Integer topN);

    protected RestTemplate getRestTemplate() {
        RestTemplate rt = restTemplate;
        if (null != proxyAddress) {
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setProxy(new Proxy(Proxy.Type.HTTP, proxyAddress));
            rt.setRequestFactory(requestFactory);
        }
        return rt;
    }
}
