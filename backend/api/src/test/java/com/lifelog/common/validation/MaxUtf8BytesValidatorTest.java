package com.lifelog.common.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MaxUtf8BytesValidatorTest {

    private MaxUtf8BytesValidator validatorOf(int max) {
        MaxUtf8BytesValidator validator = new MaxUtf8BytesValidator();
        validator.initialize(new MaxUtf8Bytes() {
            @Override public int value() { return max; }
            @Override public String message() { return ""; }
            @Override public Class<?>[] groups() { return new Class<?>[0]; }
            @SuppressWarnings("unchecked")
            @Override public Class<? extends jakarta.validation.Payload>[] payload() { return new Class[0]; }
            @Override public Class<MaxUtf8Bytes> annotationType() { return MaxUtf8Bytes.class; }
        });
        return validator;
    }

    @Test
    void isValid_whenAsciiAtLimit_returnsTrue() {
        assertThat(validatorOf(72).isValid("a".repeat(72), null)).isTrue();
    }

    @Test
    void isValid_whenAsciiOverLimit_returnsFalse() {
        assertThat(validatorOf(72).isValid("a".repeat(73), null)).isFalse();
    }

    @Test
    void isValid_whenKoreanCountedAsThreeBytes_checksByteLength() {
        // 한글 1자 = UTF-8 3바이트 → 24자 = 72바이트, 25자 = 75바이트
        assertThat(validatorOf(72).isValid("가".repeat(24), null)).isTrue();
        assertThat(validatorOf(72).isValid("가".repeat(25), null)).isFalse();
    }

    @Test
    void isValid_whenNull_returnsTrue() {
        assertThat(validatorOf(72).isValid(null, null)).isTrue();
    }
}
