package com.lifelog.specialday;

import com.lifelog.common.exception.BusinessException;
import com.lifelog.domain.specialday.SpecialDayKind;
import com.lifelog.security.JwtTokenProvider;
import com.lifelog.security.SecurityConfig;
import com.lifelog.specialday.dto.SpecialDayResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** SpecialDayController @WebMvcTest — Security 필터 포함 */
@WebMvcTest(SpecialDayController.class)
@Import(SecurityConfig.class)
class SpecialDayControllerTest {

    private static final String URL = "/api/v1/special-days";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SpecialDayService specialDayService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    private static RequestPostProcessor asUser() {
        return SecurityMockMvcRequestPostProcessors.authentication(new UsernamePasswordAuthenticationToken(
                1L, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    @Test
    void list_whenAuthenticated_returnsEnvelopeWithSpecialDays() throws Exception {
        LocalDate from = LocalDate.of(2026, 3, 1);
        LocalDate to = LocalDate.of(2026, 3, 31);
        when(specialDayService.list(from, to)).thenReturn(List.of(
                new SpecialDayResponse(LocalDate.of(2026, 3, 1), "삼일절", SpecialDayKind.HOLIDAY, true),
                new SpecialDayResponse(LocalDate.of(2026, 3, 5), "경칩", SpecialDayKind.SOLAR_TERM, false)));

        mockMvc.perform(get(URL).param("from", "2026-03-01").param("to", "2026-03-31").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(jsonPath("$.meta").doesNotExist())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].date").value("2026-03-01"))
                .andExpect(jsonPath("$.data[0].name").value("삼일절"))
                .andExpect(jsonPath("$.data[0].kind").value("HOLIDAY"))
                .andExpect(jsonPath("$.data[0].holiday").value(true))
                .andExpect(jsonPath("$.data[1].kind").value("SOLAR_TERM"))
                .andExpect(jsonPath("$.data[1].holiday").value(false));
    }

    @Test
    void list_whenNoData_returnsEmptyArray() throws Exception {
        when(specialDayService.list(any(), any())).thenReturn(List.of());

        mockMvc.perform(get(URL).param("from", "2026-03-01").param("to", "2026-03-31").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void list_withoutAuthentication_returnsUnauthorized() throws Exception {
        mockMvc.perform(get(URL).param("from", "2026-03-01").param("to", "2026-03-31"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
        verify(specialDayService, never()).list(any(), any());
    }

    @Test
    void list_whenServiceRejectsRange_returnsBadRequest() throws Exception {
        when(specialDayService.list(any(), any())).thenThrow(BusinessException.badRequest("from 은 to 보다 늦을 수 없습니다."));

        mockMvc.perform(get(URL).param("from", "2026-03-31").param("to", "2026-03-01").with(asUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("from 은 to 보다 늦을 수 없습니다."));
    }

    @Test
    void list_whenParamMissing_returnsBadRequest() throws Exception {
        mockMvc.perform(get(URL).param("from", "2026-03-01").with(asUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("필수 요청 파라미터가 누락되었습니다: to"));
    }

    @Test
    void list_whenDateFormatInvalid_returnsBadRequest() throws Exception {
        mockMvc.perform(get(URL).param("from", "2026/03/01").param("to", "2026-03-31").with(asUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("잘못된 요청 파라미터입니다: from"));
    }
}
