package com.moyz.adi.common.searchengine;

import com.moyz.adi.common.constant.AdiConstant;
import com.moyz.adi.common.dto.SearchReturn;
import com.moyz.adi.common.dto.SearchReturnWebPage;
import com.moyz.adi.common.interfaces.AbstractSearchEngineService;
import com.moyz.adi.common.util.SearchEngineUtil;
import com.moyz.adi.common.vo.GoogleSetting;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.List;

import static com.moyz.adi.common.constant.AdiConstant.SysConfigKey.GOOGLE_SETTING;

@Slf4j
public class GoogleSearchEngineService extends AbstractSearchEngineService<GoogleSetting> {

    public GoogleSearchEngineService(InetSocketAddress proxy) {
        super(AdiConstant.SearchEngineName.GOOGLE, GOOGLE_SETTING, GoogleSetting.class, proxy);
    }

    @Override
    protected void initSearchEngine() {
        AdiGoogleCustomWebSearchEngine.AdiGoogleCustomWebSearchEngineBuilder builder = AdiGoogleCustomWebSearchEngine.builder()
                .apiKey(setting.getKey())
                .csi(setting.getCx())
                .siteRestrict(false)
                .includeImages(false)
                .logRequests(true)
                .logResponses(true);
        if (null != proxyAddress) {
            builder.proxy(new Proxy(Proxy.Type.HTTP, proxyAddress));
        }
        searchEngine = builder.build();
    }

    @Override
    public boolean isEnabled() {
        return StringUtils.isNoneBlank(setting.getKey(), setting.getCx());
    }

    @Override
    public SearchReturn search(String searchTxt, String country, String language, Integer topN) {
        SearchReturn result = new SearchReturn();
        List<SearchReturnWebPage> items = new ArrayList<>();
        result.setItems(items);
        if (StringUtils.isBlank(searchTxt)) {
            log.warn("google search terms is empty");
            return result;
        }
        String tmpCountry = SearchEngineUtil.checkGoogleCountry(country) ? country : "cn";
        String tmpLang = SearchEngineUtil.checkGoogleLanguage(country) ? country : "zh-cn";
        // 2026-10-16：未启用（无 key/cx）时 searchEngine 不会初始化，
        // 此时返回空结果而非 NPE——调用方（如工作流联网节点）拿到空列表可正常降级
        if (!isEnabled() || null == searchEngine) {
            log.warn("google search engine is not enabled (missing api key/csi), return empty result");
            return result;
        }
        try {
            WebSearchRequest webSearchRequest = WebSearchRequest.builder()
                    .searchTerms(searchTxt)
                    .language(tmpLang)
                    .geoLocation(tmpCountry)
                    .maxResults(topN)
                    .build();
            WebSearchResults webSearchResults = searchEngine.search(webSearchRequest);
            if (null != webSearchResults && webSearchResults.searchInformation().totalResults() > 0) {
                List<SearchReturnWebPage> wrapItems = webSearchResults.results()
                        .stream()
                        .map(
                                item -> SearchReturnWebPage.builder()
                                        .title(item.title())
                                        .link(item.url().toString())
                                        .snippet(item.snippet())
                                        .content(item.content())
                                        .build()
                        )
                        .toList();
                items.addAll(wrapItems);
            } else {
                log.warn("google search result is empty");
            }
        } catch (Exception e) {
            log.error("google search error", e);
        }
        return result;
    }

}
