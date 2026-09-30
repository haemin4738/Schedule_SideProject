package com.lifelog.domain.specialday;

import java.util.List;

/** 특일 외부 출처 포트 (구현: 한국천문연구원 특일 정보 API) */
public interface SpecialDaySource {

    /** 호출에 필요한 설정(인증키)이 있는지. false 면 호출하지 않는다. */
    boolean isConfigured();

    /**
     * 해당 연도의 공휴일·기념일·24절기 전체. 세 종류를 모두 받아야 성공이며 하나라도 실패하면 예외.
     *
     * @throws SpecialDaySourceException 설정 누락·통신 실패·응답 형식 오류
     */
    List<SpecialDayData> fetchYear(int year);
}
