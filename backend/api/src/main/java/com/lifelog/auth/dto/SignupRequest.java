package com.lifelog.auth.dto;

import com.lifelog.common.validation.MaxUtf8Bytes;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        @NotBlank(message = "이메일은 필수입니다.")
        String email,

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
        // BCrypt는 72바이트까지만 처리한다 (초과 시 encode()가 예외를 던짐, CVE-2025-22228 대응)
        @MaxUtf8Bytes(value = 72, message = "비밀번호가 너무 깁니다. (영문 기준 72자, 한글 기준 24자 이하)")
        String password,

        @NotBlank(message = "이름은 필수입니다.")
        String name
) {}
