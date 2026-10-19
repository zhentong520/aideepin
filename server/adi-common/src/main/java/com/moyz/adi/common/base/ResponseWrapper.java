package com.moyz.adi.common.base;

import com.moyz.adi.common.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@Slf4j
@RestControllerAdvice(basePackages = {"com.moyz.adi"})
public class ResponseWrapper implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter methodParameter, Class aClass) {
        return true;
    }

    @NotNull
    @Override
    public Object beforeBodyWrite(Object result, MethodParameter methodParameter,
                                  MediaType mediaType, Class aClass, ServerHttpRequest serverHttpRequest,
                                  ServerHttpResponse serverHttpResponse) {
        if (result instanceof BaseResponse) {
            return result;
        } else if (result instanceof String str) {
            return JsonUtil.toJson(new BaseResponse<>(true, str));
        } else if (result instanceof org.springframework.core.io.Resource) {
            return result;
        } else if (result instanceof byte[]) {
            return result;
        }
        // 声明返回 String 的接口（如 /auth/register、/character/message/text/{uuid}）会由
        // StringHttpMessageConverter 处理。若此处返回 BaseResponse 对象，转换器会把它强转成 String，
        // 抛 ClassCastException → 接口恒返回"全局异常"（返回值恰为 null 时必现）。
        // 因此对 String 声明的接口，无论结果是否为 null，都必须序列化成 JSON 字符串。
        // Handlers declared to return String are written by StringHttpMessageConverter. Returning a
        // BaseResponse object here makes that converter cast it to String and throw a ClassCastException,
        // so such endpoints failed with a generic error whenever the value was null. Serialize to a JSON
        // string instead, for null as well as non-String results.
        if (String.class.equals(methodParameter.getParameterType())) {
            return JsonUtil.toJson(new BaseResponse<>(true, result));
        }
        return new BaseResponse<>(true, result);
    }

}