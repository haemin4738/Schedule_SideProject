package com.lifelog.auth;

import com.lifelog.auth.dto.NaverAppCompleteRequest;
import com.lifelog.auth.dto.NaverAppStartRequest;
import com.lifelog.auth.dto.NaverAppStartResponse;
import com.lifelog.auth.dto.SocialLoginResponse;
import com.lifelog.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 네이버 앱(iOS) 로그인 — {@link NaverAppLoginService} 참고. 세 경로 모두 /api/v1/auth/** permitAll */
@RestController
@RequestMapping("/api/v1/auth/social/naver")
@RequiredArgsConstructor
public class NaverAppLoginController {

    private final NaverAppLoginService naverAppLoginService;

    @PostMapping("/app/start")
    public ApiResponse<NaverAppStartResponse> start(@Valid @RequestBody NaverAppStartRequest request) {
        return ApiResponse.ok(naverAppLoginService.start(request.codeChallenge()));
    }

    /** 네이버 redirect_uri. 리다이렉트 전용이므로 ApiResponse envelope 을 쓰지 않는다 */
    @GetMapping("/app-callback")
    public ResponseEntity<Void> callback(@RequestParam(required = false) String code,
                                         @RequestParam(required = false) String state,
                                         @RequestParam(required = false) String error) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(naverAppLoginService.callback(code, state, error))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("Referrer-Policy", "no-referrer")
                .build();
    }

    /** 결과는 /social/{provider}/login 과 같은 형태 (data.status 로 분기) */
    @PostMapping("/app/complete")
    public ApiResponse<SocialLoginResponse> complete(@Valid @RequestBody NaverAppCompleteRequest request) {
        return ApiResponse.ok(naverAppLoginService.complete(request.ticket(), request.codeVerifier()));
    }
}
