package com.lifelog.specialday;

import com.lifelog.common.dto.ApiResponse;
import com.lifelog.specialday.dto.SpecialDayResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** 공휴일·기념일·24절기 (사용자 공용 데이터, 로그인 필요). 범위가 최대 366일로 제한되어 페이지네이션 없음 */
@RestController
@RequestMapping("/api/v1/special-days")
@RequiredArgsConstructor
public class SpecialDayController {

    private final SpecialDayService specialDayService;

    @GetMapping
    public ApiResponse<List<SpecialDayResponse>> list(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(specialDayService.list(from, to));
    }
}
