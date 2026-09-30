package com.lifelog.domain.specialday;

/** 특일 종류. 선언 순서가 같은 날짜 안의 정렬 순서다. */
public enum SpecialDayKind {
    /** 공휴일(쉬는 날) — 한국천문연구원 getRestDeInfo 의 isHoliday=Y */
    HOLIDAY,
    /** 기념일 — getAnniversaryInfo, getRestDeInfo 의 isHoliday=N */
    ANNIVERSARY,
    /** 24절기 — get24DivisionsInfo */
    SOLAR_TERM
}
