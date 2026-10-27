package com.moyz.adi.common.workflow.node.httprequest;

import com.aliyun.core.utils.StringUtils;
import com.moyz.adi.common.entity.WorkflowComponent;
import com.moyz.adi.common.entity.WorkflowNode;
import com.moyz.adi.common.util.JsonUtil;
import com.moyz.adi.common.util.SafeUrlChecker;
import com.moyz.adi.common.workflow.NodeProcessResult;
import com.moyz.adi.common.workflow.WfNodeState;
import com.moyz.adi.common.workflow.WfState;
import com.moyz.adi.common.workflow.data.NodeIOData;
import com.moyz.adi.common.workflow.node.AbstractWfNode;
import com.moyz.adi.common.workflow.metrics.HttpRequestMetrics;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.Consts;
import org.apache.http.Header;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static com.moyz.adi.common.constant.GeneralConstant.FORM_DATA_BOUNDARY_PRE;
import static com.moyz.adi.common.workflow.WfConstant.DEFAULT_ERROR_OUTPUT_PARAM_NAME;
import static com.moyz.adi.common.workflow.WfConstant.DEFAULT_OUTPUT_PARAM_NAME;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;

@Slf4j
public class HttpRequestNode extends AbstractWfNode {

    public HttpRequestNode(WorkflowComponent wfComponent, WorkflowNode node, WfState wfState, WfNodeState nodeState) {
        super(wfComponent, node, wfState, nodeState);
        state.setMetrics(new HttpRequestMetrics());
    }

    protected NodeProcessResult onProcess() {
        List<NodeIOData> outputData = new ArrayList<>();

        HttpRequestNodeConfig nodeConfig = checkAndGetConfig(HttpRequestNodeConfig.class);
        int timeoutMs = nodeConfig.getTimeout() * 1000;
        //禁用自动重定向：自动跟随只校验首跳，公网 302 跳内网会绕过防护，
        //必须手动跟随并对每一跳做 SSRF 复检（与 KbDocSourceService 同一套防护策略）
        //Disable auto redirect: recheck every hop against SSRF instead of trusting the first-hop check only
        RequestConfig requestConfig = RequestConfig.custom()
                .setRedirectsEnabled(false)
                .setConnectionRequestTimeout(timeoutMs)
                .setConnectTimeout(timeoutMs)
                .setSocketTimeout(timeoutMs)
                .build();
        try (CloseableHttpClient httpClient = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .setRetryHandler(new DefaultHttpRequestRetryHandler(nodeConfig.getRetryTimes(), true))
                .build()) {
            String currentUrl = appendParams(nodeConfig.getUrl(), nodeConfig.getParams());
            //SSRF 防护：首跳同样必须过校验 | SSRF guard: validate the first hop too
            SafeUrlChecker.check(currentUrl);
            String currentMethod = nodeConfig.getMethod();
            for (int hop = 0; hop <= SafeUrlChecker.MAX_REDIRECTS; hop++) {
                HttpUriRequest httpRequest = buildRequest(currentUrl, currentMethod, nodeConfig);
                try (CloseableHttpResponse response = httpClient.execute(httpRequest)) {
                    int statusCode = response.getStatusLine().getStatusCode();
                    if (statusCode == 301 || statusCode == 302 || statusCode == 303
                            || statusCode == 307 || statusCode == 308) {
                        Header locationHeader = response.getFirstHeader("Location");
                        if (null == locationHeader || StringUtils.isBlank(locationHeader.getValue())) {
                            throw new RuntimeException("HTTP " + statusCode + " 缺少 Location 头");
                        }
                        //Location 可能是相对路径，按当前请求地址解析成绝对地址后再复检
                        currentUrl = httpRequest.getURI().resolve(locationHeader.getValue()).toString();
                        SafeUrlChecker.check(currentUrl);
                        //303 与 302/301 的 POST 按惯例改写为 GET
                        if (statusCode == 303
                                || (statusCode == 302 && HttpPost.METHOD_NAME.equalsIgnoreCase(currentMethod))) {
                            currentMethod = HttpGet.METHOD_NAME;
                        }
                        continue;
                    }
                    String responseBody = EntityUtils.toString(response.getEntity(), Consts.UTF_8);
                    //记录 HTTP 指标 | Record HTTP metrics
                    HttpRequestMetrics httpMetrics = (HttpRequestMetrics) state.getMetrics();
                    httpMetrics.setHttpStatusCode(statusCode);
                    httpMetrics.setHttpMethod(nodeConfig.getMethod());
                    if (Boolean.TRUE.equals(nodeConfig.getClearHtml())) {
                        Document doc = Jsoup.parse(responseBody);
                        responseBody = doc.body().text();
                    }
                    if (statusCode == 200) {
                        NodeIOData output = NodeIOData.createByText(DEFAULT_OUTPUT_PARAM_NAME, "响应内容", responseBody);
                        outputData.add(output);
                    } else {
                        log.error("Request failed:{}", response);
                        NodeIOData output = NodeIOData.createByText(DEFAULT_ERROR_OUTPUT_PARAM_NAME, "错误内容", responseBody);
                        outputData.add(output);
                    }
                    outputData.add(NodeIOData.createByText("status_code", "http状态码", String.valueOf(statusCode)));
                    return NodeProcessResult.builder().content(outputData).build();
                }
            }
            throw new RuntimeException("重定向超过 " + SafeUrlChecker.MAX_REDIRECTS + " 次");
        } catch (IOException e) {
            log.error("Request failed:{}", e.getMessage());
            //IO 异常时仍记录请求方法 | Record HTTP method even on IO failure
            ((HttpRequestMetrics) state.getMetrics()).setHttpMethod(nodeConfig.getMethod());
        }
        return NodeProcessResult.builder().content(outputData).build();
    }

    /**
     * 按当前 URL 与方法构建请求。重定向循环里每跳都会重建（POST 转 GET 后不再带请求体）。
     */
    private HttpUriRequest buildRequest(String url, String method, HttpRequestNodeConfig nodeConfig) {
        String contentType = nodeConfig.getContentType();
        if (HttpGet.METHOD_NAME.equalsIgnoreCase(method)) {
            HttpUriRequest httpGet = new HttpGet(url);
            httpGet.setHeader(CONTENT_TYPE, contentType);
            setHeaders(httpGet, nodeConfig.getHeaders());
            return httpGet;
        } else if (HttpPost.METHOD_NAME.equalsIgnoreCase(method)) {
            HttpPost httpPost = new HttpPost(url);
            httpPost.setHeader(CONTENT_TYPE, contentType);
            setHeaders(httpPost, nodeConfig.getHeaders());
            if (contentType.equalsIgnoreCase("text/plain")) {
                StringEntity jsonEntity = new StringEntity(nodeConfig.getTextBody(), ContentType.TEXT_PLAIN.withCharset(Consts.UTF_8));
                httpPost.setEntity(jsonEntity);
            } else if (contentType.equalsIgnoreCase("application/json")) {
                StringEntity jsonEntity = new StringEntity(JsonUtil.toJson(nodeConfig.getJsonBody()), ContentType.APPLICATION_JSON.withCharset(Consts.UTF_8));
                httpPost.setEntity(jsonEntity);
            } else if (contentType.equalsIgnoreCase("multipart/form-data")) {
                MultipartEntityBuilder entityBuilder = MultipartEntityBuilder.create();
                if (nodeConfig.getFormDataBody() != null) {
                    for (HttpRequestNodeConfig.Param entry : nodeConfig.getFormDataBody()) {
                        if (entry.getValue() instanceof File) {
                            entityBuilder.addPart(entry.getName(), new FileBody((File) entry.getValue()));
                        } else {
                            entityBuilder.addTextBody(entry.getName(), entry.getValue().toString());
                        }
                    }
                }
                String boundary = FORM_DATA_BOUNDARY_PRE + System.currentTimeMillis();
                entityBuilder.setBoundary(boundary);
                httpPost.setEntity(entityBuilder.build());
                httpPost.setHeader(CONTENT_TYPE, "multipart/form-data; boundary=" + boundary);
            } else if (contentType.equalsIgnoreCase("application/x-www-from-urlencoded")) {
                StringEntity jsonEntity = new StringEntity(JsonUtil.toJson(nodeConfig.getFormUrlencodedBody()), ContentType.APPLICATION_FORM_URLENCODED.withCharset(Consts.UTF_8));
                httpPost.setEntity(jsonEntity);
            }
            return httpPost;
        }
        log.error("Unsupported request method:{}", method);
        throw new RuntimeException("不支持的请求方法：" + method);
    }

    private String appendParams(String url, List<HttpRequestNodeConfig.Param> params) {
        String result = "";
        if (!params.isEmpty()) {
            //获取Map集合中的键集合
            Iterator<HttpRequestNodeConfig.Param> iterator = params.iterator();
            StringBuilder stringBuffer = new StringBuilder();
            while (iterator.hasNext()) {
                HttpRequestNodeConfig.Param item = iterator.next();
                String value = item.getValue().toString();
                stringBuffer.append("&");
                stringBuffer.append(item.getName());
                stringBuffer.append("=");
                stringBuffer.append(value);
            }
            result = stringBuffer.toString();
        }
        if (StringUtils.isBlank(result)) {
            return url;
        } else {
            if (url.contains("?")) {
                return url + result;
            } else {
                return url + "?" + result;
            }
        }
    }

    private void setHeaders(HttpUriRequest httpRequest, List<HttpRequestNodeConfig.Param> headers) {
        for (HttpRequestNodeConfig.Param entry : headers) {
            httpRequest.addHeader(entry.getName(), entry.getValue().toString());
        }
    }
}
