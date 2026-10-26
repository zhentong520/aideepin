package com.moyz.adi.common.validator;

import com.moyz.adi.common.annotation.CreateImageReqCheck;
import com.moyz.adi.common.dto.CreateImageDto;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.apache.commons.lang3.StringUtils;

import static com.moyz.adi.common.constant.GeneralConstant.GenerateImage.*;

public class CreateImageReqValidator implements
        ConstraintValidator<CreateImageReqCheck, CreateImageDto> {

    @Override
    public boolean isValid(CreateImageDto createImageDto, ConstraintValidatorContext constraintValidatorContext) {
        // 同 AskReqValidator：校验失败必须返回 false，不能抛异常（否则被包成 HV000028 → 全局异常）
        // Same as AskReqValidator: return false instead of throwing, otherwise Hibernate Validator wraps
        // the exception into HV000028 and the endpoint answers with a generic server error.
        if (null == createImageDto) {
            return false;
        }
        if (createImageDto.getInteractingMethod() == INTERACTING_METHOD_GENERATE_IMAGE
                && StringUtils.isBlank(createImageDto.getPrompt())) {
            constraintValidatorContext.disableDefaultConstraintViolation();
            constraintValidatorContext.buildConstraintViolationWithTemplate("prompt can not be empty")
                    .addConstraintViolation();
            return false;
        }
        return true;
    }
}
