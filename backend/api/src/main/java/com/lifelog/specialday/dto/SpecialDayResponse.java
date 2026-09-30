package com.lifelog.specialday.dto;

import com.lifelog.domain.specialday.SpecialDay;
import com.lifelog.domain.specialday.SpecialDayKind;

import java.time.LocalDate;

public record SpecialDayResponse(LocalDate date, String name, SpecialDayKind kind, boolean holiday) {

    public static SpecialDayResponse from(SpecialDay day) {
        return new SpecialDayResponse(day.getSolDate(), day.getName(), day.getKind(), day.isHoliday());
    }
}
