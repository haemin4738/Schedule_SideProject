package com.lifelog.domain.specialday;

/**
 * 특일 외부 출처 조회 실패(설정 누락·HTTP 오류·타임아웃·응답 형식 오류).
 * 원인 예외 메시지에 요청 URI(인증키 포함)나 응답 본문이 담길 수 있으므로
 * 원인을 연결하지 않고, 메시지에는 인증키·응답 본문을 넣지 않는다.
 */
public class SpecialDaySourceException extends RuntimeException {

    public SpecialDaySourceException(String message) {
        super(message);
    }
}
