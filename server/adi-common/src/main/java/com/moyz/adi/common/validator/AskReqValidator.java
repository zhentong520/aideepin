package com.moyz.adi.common.validator;

import com.moyz.adi.common.annotation.AskReqCheck;
import com.moyz.adi.common.dto.AskReq;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.apache.commons.lang3.StringUtils;

import java.util.regex.Pattern;

public class AskReqValidator implements
        ConstraintValidator<AskReqCheck, AskReq> {

    /**
     * UUID v4 正则 | UUID v4 regex
     */
    private static final String UUID_REGEX = "^[0-9a-fA-F]{8}[0-9a-fA-F]{4}4[0-9a-fA-F]{3}[89abAB][0-9a-fA-F]{3}[0-9a-fA-F]{12}$";

    @Override
    public void initialize(AskReqCheck constraintAnnotation) {
        //无需校验
    }

    @Override
    public boolean isValid(AskReq value, ConstraintValidatorContext context) {
        // 约束校验器不允许"抛异常表示校验失败"：Jakarta Bean Validation 规定非法输入必须
        // 返回 false。之前这里直接 throw IllegalArgumentException，Hibernate Validator 会把它包成
        // HV000028（Unexpected exception during isValid call），接口只能返回"全局异常"，
        // 客户端拿不到"到底哪个字段不合法"。
        // A constraint validator must not signal failure by throwing: the Bean Validation spec requires
        // returning false. Throwing here was wrapped into HV000028 by Hibernate Validator, so the endpoint
        // could only answer with a generic server error and the client never learned which field was wrong.
        if (null == value) {
            return false;
        }
        if (StringUtils.isAllBlank(value.getPrompt(), value.getRegenerateQuestionUuid(), value.getAudioUuid())) {
            return fail(context, "prompt/regenerateQuestionUuid/audioUuid cannot all be empty");
        }
        // characterUuid 是本接口的必填契约（下游 sseAsk 依赖角色上下文），空或非法均拒绝
        if (!Pattern.matches(UUID_REGEX, StringUtils.defaultString(value.getCharacterUuid()))) {
            return fail(context, "characterUuid is required and must be a valid uuid");
        }
        if (StringUtils.isNotBlank(value.getRegenerateQuestionUuid())
                && !Pattern.matches(UUID_REGEX, value.getRegenerateQuestionUuid())) {
            return fail(context, "regenerateQuestionUuid is not a valid uuid");
        }
        return true;
    }

    private boolean fail(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
        return false;
    }
}
