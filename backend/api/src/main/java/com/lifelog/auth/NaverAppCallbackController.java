package com.lifelog.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * 네이버 로그인(iOS 앱)의 redirect_uri. 제공자가 넘긴 code/state/error 만 앱 딥링크로 전달한다.
 * 대상은 설정값 하나로 고정 — 요청 값으로 대상이 바뀌지 않는다 (open redirect 방지).
 * 리다이렉트 전용이므로 ApiResponse envelope 을 쓰지 않는다. code 는 로그에 남기지 않는다.
 */
@RestController
public class NaverAppCallbackController {

    private final String callbackTarget;

    public NaverAppCallbackController(@Value("${oauth.naver.app-callback-target}") String callbackTarget) {
        this.callbackTarget = callbackTarget;
    }

    @GetMapping("/api/v1/auth/social/naver/app-callback")
    public ResponseEntity<Void> callback(@RequestParam(required = false) String code,
                                         @RequestParam(required = false) String state,
                                         @RequestParam(required = false) String error) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(callbackTarget);
        Map<String, String> params = new HashMap<>();
        addParam(builder, params, "code", code);
        addParam(builder, params, "state", state);
        addParam(builder, params, "error", error);

        // 값은 URI 변수로 확장해 예약 문자(&, =, +, # 등)까지 모두 인코딩한다
        URI location = builder.encode().buildAndExpand(params).toUri();

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(location)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("Referrer-Policy", "no-referrer")
                .build();
    }

    private static void addParam(UriComponentsBuilder builder, Map<String, String> params, String name, String value) {
        if (value != null) {
            builder.queryParam(name, "{" + name + "}");
            params.put(name, value);
        }
    }
}
