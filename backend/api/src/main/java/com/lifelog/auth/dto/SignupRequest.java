package com.lifelog.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        @NotBlank(message = "이메일은 필수입니다.")
        String email,

        @NotBlank(message = "비밀번호는 필수입니다.")
        // 특수문자 = ASCII 구두점 전체(!-/ :-@ [-` {-~). 영문/숫자/특수문자를 각각 1자 이상 포함한 9~15자. ASCII만 허용하므로 BCrypt 72바이트 한계 안에 든다
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[!-/:-@\\[-`{-~])[A-Za-z\\d!-/:-@\\[-`{-~]{9,15}$",
                message = "비밀번호는 영문, 숫자, 특수문자를 각각 1자 이상 포함한 9~15자여야 합니다.")
        String password,

        @NotBlank(message = "이름은 필수입니다.")
        @Size(max = 12, message = "이름은 12자 이하여야 합니다.")
        String name
) {}
