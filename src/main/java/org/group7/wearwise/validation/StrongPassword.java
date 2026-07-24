package org.group7.wearwise.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Ràng buộc độ mạnh mật khẩu, dùng chung cho đăng ký, đặt lại và đổi mật khẩu. */
@Documented
@Constraint(validatedBy = StrongPasswordValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface StrongPassword {

    String message() default "Password must be 8-100 characters and include at least one letter and one digit.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
