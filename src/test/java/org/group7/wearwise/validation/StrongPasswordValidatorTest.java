package org.group7.wearwise.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class StrongPasswordValidatorTest {

    private final StrongPasswordValidator validator = new StrongPasswordValidator();

    @ParameterizedTest
    @ValueSource(strings = {"password123", "Abcdefg1", "matKhau2026", "a1b2c3d4"})
    void acceptsPasswordsWithLettersAndDigits(String password) {
        assertThat(validator.isValid(password, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "abc123",        // quá ngắn
            "abcdefgh",      // thiếu chữ số
            "12345678",      // thiếu chữ cái
            "pass word1",    // chứa khoảng trắng
            ""               // rỗng
    })
    void rejectsWeakPasswords(String password) {
        assertThat(validator.isValid(password, null)).isFalse();
    }

    @Test
    void rejectsPasswordLongerThanLimit() {
        assertThat(validator.isValid("a1".repeat(51), null)).isFalse();
    }

    @Test
    void skipsNullSoThatNotBlankReportsTheError() {
        assertThat(validator.isValid(null, null)).isTrue();
    }
}
