package com.moyz.adi.common.config;

import com.moyz.adi.common.base.BaseResponse;
import com.moyz.adi.common.enums.ErrorEnum;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.util.SpringUtil;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    /**
     * 参数校验异常
     *
     * @return BaseResponse
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    private BaseResponse handleMethodArgumentNotValidException(
            final MethodArgumentNotValidException exception) {
        Map<Object, Object> error = wrapperError(exception.getBindingResult());
        log.error("Parameter validation error:{}", error);
        return new BaseResponse(ErrorEnum.A_PARAMS_ERROR.getCode(), SpringUtil.getMessage(ErrorEnum.A_PARAMS_ERROR.getInfo()), error);
    }

    /**
     * 缺少必填请求参数（query/form）或路径变量 | Missing required request parameter or path variable.
     * <p>
     * 这类问题是**调用方**的错，必须给出可定位的参数名。以前全部落进兜底的
     * B0003「全局异常」，客户端既不知道错在哪，也会被误当成服务端故障。
     * These are caller errors and must name the offending parameter. They used to fall through to the
     * generic B0003 "global exception", which hid the real cause and looked like a server fault.
     * </p>
     */
    @ExceptionHandler({MissingServletRequestParameterException.class, MissingPathVariableException.class})
    private BaseResponse handleMissingParam(final Exception exception) {
        Map<Object, Object> data = new HashMap<>(3);
        if (exception instanceof MissingServletRequestParameterException e) {
            data.put("missingParameter", e.getParameterName());
            data.put("expectedType", e.getParameterType());
        } else if (exception instanceof MissingPathVariableException e) {
            data.put("missingPathVariable", e.getVariableName());
        }
        log.error("Missing parameter:{}", data);
        return new BaseResponse(ErrorEnum.A_PARAMS_ERROR.getCode(), SpringUtil.getMessage(ErrorEnum.A_PARAMS_ERROR.getInfo()), data);
    }

    /**
     * 参数类型/格式不合法（如 currentPage 传了空串、路径变量不是数字） | Bad parameter type or format.
     */
    @ExceptionHandler({MethodArgumentTypeMismatchException.class, ServletRequestBindingException.class})
    private BaseResponse handleTypeMismatch(final Exception exception) {
        Map<Object, Object> data = new HashMap<>(3);
        if (exception instanceof MethodArgumentTypeMismatchException e) {
            data.put("parameter", e.getName());
            data.put("rejectedValue", String.valueOf(e.getValue()));
            data.put("expectedType", e.getRequiredType() == null ? "" : e.getRequiredType().getSimpleName());
        }
        log.error("Parameter type mismatch:{}", data);
        return new BaseResponse(ErrorEnum.A_PARAMS_ERROR.getCode(), SpringUtil.getMessage(ErrorEnum.A_PARAMS_ERROR.getInfo()), data);
    }

    /**
     * 请求体不可读（JSON 格式错误 / 字段类型对不上 / 空 body） | Unreadable request body.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    private BaseResponse handleNotReadable(final HttpMessageNotReadableException exception) {
        log.error("Request body not readable:{}", exception.getMessage());
        return new BaseResponse(ErrorEnum.A_PARAMS_ERROR.getCode(), SpringUtil.getMessage(ErrorEnum.A_PARAMS_ERROR.getInfo()), null);
    }

    /**
     * 方法级约束校验失败（@RequestParam/@PathVariable 上的 @NotBlank、@Length 等）
     * Method level constraint violations (@NotBlank/@Length on @RequestParam/@PathVariable).
     */
    @ExceptionHandler(ConstraintViolationException.class)
    private BaseResponse handleConstraintViolation(final ConstraintViolationException exception) {
        Map<Object, Object> data = new HashMap<>(5);
        for (ConstraintViolation<?> violation : exception.getConstraintViolations()) {
            data.put(String.valueOf(violation.getPropertyPath()), violation.getMessage());
        }
        log.error("Constraint violation:{}", data);
        return new BaseResponse(ErrorEnum.A_PARAMS_ERROR.getCode(), SpringUtil.getMessage(ErrorEnum.A_PARAMS_ERROR.getInfo()), data);
    }

    /**
     * Spring 6.1+ 的方法参数校验失败（形参上直接写 {@code @NotNull} / {@code @Min} 的写法走这条）。
     * <p>
     * 本项目大量分页入参写作 {@code @NotNull @Min(1) Integer currentPage}，缺参时抛的是
     * {@code HandlerMethodValidationException} 而不是 {@code ConstraintViolationException}；
     * 没有这个处理器时会一路落进兜底的 B0003「全局异常」，把"调用方少传了分页参数"
     * 误报成服务端故障。这里按参数名给出可定位的报错。
     * </p>
     * Method-argument validation failures (Spring 6.1+, for constraints declared directly on the
     * parameter, e.g. {@code @NotNull @Min(1) Integer currentPage}). Without this handler they fell
     * through to the generic B0003 "global exception", reporting a caller-side missing parameter as a
     * server fault.
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    private BaseResponse handleHandlerMethodValidation(final HandlerMethodValidationException exception) {
        Map<Object, Object> data = new HashMap<>(5);
        for (ParameterValidationResult result : exception.getAllValidationResults()) {
            String name = result.getMethodParameter().getParameterName();
            for (MessageSourceResolvable error : result.getResolvableErrors()) {
                data.put(null == name ? "parameter" : name, error.getDefaultMessage());
            }
        }
        log.error("Handler method validation error:{}", data);
        return new BaseResponse(ErrorEnum.A_PARAMS_ERROR.getCode(), SpringUtil.getMessage(ErrorEnum.A_PARAMS_ERROR.getInfo()), data);
    }

    @ExceptionHandler(BaseException.class)
    private BaseResponse handleBaseException(final BaseException exception) {
        log.error("Business exception intercepted:{}", exception);
        return new BaseResponse(exception.getCode(), exception.getInfo(), exception.getData());
    }

    /**
     * URL 不存在（打错的路径、或前端拼错的接口名） | Unknown URL.
     * <p>
     * 以前这类请求落进兜底的 B0003「全局异常」，调用方会以为服务端坏了，
     * 实际是自己把地址写错了。按 A0001 明确回报。
     * These used to fall through to B0003 "global exception", making a caller-side typo look like a
     * server outage. Report A0001 instead.
     * </p>
     */
    @ExceptionHandler(NoResourceFoundException.class)
    private BaseResponse handleUrlNotFound(final NoResourceFoundException exception) {
        Map<Object, Object> data = new HashMap<>(2);
        data.put("path", "/" + exception.getResourcePath());
        log.warn("URL not found:{}", data);
        return new BaseResponse(ErrorEnum.A_URL_NOT_FOUND.getCode(), SpringUtil.getMessage(ErrorEnum.A_URL_NOT_FOUND.getInfo()), data);
    }

    /**
     * HTTP 方法不匹配（应 POST 却用 GET 等） | HTTP method not allowed.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    private BaseResponse handleMethodNotSupported(final HttpRequestMethodNotSupportedException exception) {
        Map<Object, Object> data = new HashMap<>(3);
        data.put("method", exception.getMethod());
        data.put("supportedMethods", String.join(",", exception.getSupportedMethods() == null ? new String[0] : exception.getSupportedMethods()));
        log.warn("HTTP method not supported:{}", data);
        return new BaseResponse(ErrorEnum.A_PARAMS_ERROR.getCode(), SpringUtil.getMessage(ErrorEnum.A_PARAMS_ERROR.getInfo()), data);
    }

    /**
     * S1-10：Redis 连接故障（登录写 token、缓存读写等硬依赖场景）。
     * <p>
     * 只拦 Redis 侧连接异常（RedisConnectionFailureException / RedisSystemException /
     * 通用的 DataAccessResourceFailureException），不吞普通 DataAccessException——
     * 后者覆盖 JDBC，错把数据库故障报成"缓存不可用"会误导排障。
     * 调用方拿到 A_REDIS_UNAVAILABLE 明确语义，而非兜底 B0003 全局异常（HTTP 500 观感）。
     * </p>
     */
    @ExceptionHandler({RedisConnectionFailureException.class, RedisSystemException.class,
            DataAccessResourceFailureException.class})
    private BaseResponse handleRedisUnavailable(final Exception exception) {
        log.error("Redis unavailable intercepted:", exception);
        return new BaseResponse(ErrorEnum.A_REDIS_UNAVAILABLE.getCode(),
                SpringUtil.getMessage(ErrorEnum.A_REDIS_UNAVAILABLE.getInfo()), null);
    }

    /**
     * 兜底
     *
     * @return BaseResponse
     */
    @ExceptionHandler(Exception.class)
    private BaseResponse handleException(final Exception exception) {
        log.error("Global exception intercepted:", exception);
        return new BaseResponse(ErrorEnum.B_GLOBAL_ERROR.getCode(), SpringUtil.getMessage(ErrorEnum.B_GLOBAL_ERROR.getInfo()), null);
    }

    private Map<Object, Object> wrapperError(BindingResult result) {
        Map<Object, Object> errorMap = new HashMap<>(5);
        result.getFieldErrors().forEach(x -> errorMap.put(x.getField(), x.getDefaultMessage()));
        return errorMap;
    }
}
