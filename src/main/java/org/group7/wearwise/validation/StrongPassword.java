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

    String message() default "Mật khẩu phải từ 8 đến 100 ký tự, có cả chữ và số, không chứa khoảng trắng.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
