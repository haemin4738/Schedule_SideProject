package com.lifelog.domain.specialday;

import java.time.LocalDate;

/** 외부 출처에서 받은 특일 한 건 (저장 전 값) */
public record SpecialDayData(LocalDate date, SpecialDayKind kind, String name, boolean holiday) {

    /** 같은 날짜·종류·이름이면 같은 특일 (special_days 유니크 키와 동일) */
    public Key key() {
        return new Key(date, kind, name);
    }

    public record Key(LocalDate date, SpecialDayKind kind, String name) {
    }
}
