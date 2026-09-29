package com.lifelog.auth;

import com.lifelog.auth.dto.SocialLinkRequest;
import com.lifelog.auth.dto.SocialLoginRequest;
import com.lifelog.auth.dto.SocialLoginResponse;
import com.lifelog.auth.dto.TokenResponse;
import com.lifelog.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth/social")
@RequiredArgsConstructor
public class SocialAuthController {

    private final SocialAuthService socialAuthService;

    /** provider: kakao / naver / google (대소문자 무시). 결과는 data.status(LOGGED_IN / LINK_REQUIRED)로 분기 */
    @PostMapping("/{provider}/login")
    public ApiResponse<SocialLoginResponse> login(@PathVariable String provider,
                                                  @Valid @RequestBody SocialLoginRequest request) {
        return ApiResponse.ok(socialAuthService.login(provider, request));
    }

    @PostMapping("/link")
    public ApiResponse<TokenResponse> link(@Valid @RequestBody SocialLinkRequest request) {
        return ApiResponse.ok(socialAuthService.link(request));
    }
}
