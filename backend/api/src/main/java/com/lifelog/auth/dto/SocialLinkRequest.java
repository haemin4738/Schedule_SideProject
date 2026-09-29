package com.lifelog.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SocialLinkRequest(
        @NotBlank(message = "linkToken은 필수입니다.")
        @Size(max = 128, message = "linkToken이 너무 깁니다.")
        String linkToken,

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(max = 128, message = "비밀번호가 너무 깁니다.")
        String password
) {}
