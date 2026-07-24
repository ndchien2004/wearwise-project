package org.group7.wearwise.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 100;

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        // @NotBlank lo phần rỗng; ở đây bỏ qua null để không báo lỗi trùng lặp.
        if (password == null) {
            return true;
        }

        if (password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            return false;
        }

        boolean hasLetter = false;
        boolean hasDigit = false;

        for (int i = 0; i < password.length(); i++) {
            char character = password.charAt(i);
            if (Character.isWhitespace(character)) {
                return false;
            }
            if (Character.isLetter(character)) {
                hasLetter = true;
            } else if (Character.isDigit(character)) {
                hasDigit = true;
            }
        }

        return hasLetter && hasDigit;
    }
}
