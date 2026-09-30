package com.lifelog.infrastructure.specialday;

import com.lifelog.domain.specialday.SpecialDayData;
import com.lifelog.domain.specialday.SpecialDayKind;
import com.lifelog.domain.specialday.SpecialDaySourceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(OutputCaptureExtension.class)
class KasiSpecialDayClientTest {

    private static final String BASE = "https://apis.example.test/SpcdeInfoService";
    /** Decoding 형태 키 — +, /, = 가 모두 들어 있어 인코딩 규칙을 고정한다 (Encoding 형태 = ENCODED_KEY) */
    private static final String KEY = "ab+c/d==";
    private static final String ENCODED_KEY = "ab%2Bc%2Fd%3D%3D";
    private static final int YEAR = 2026;

    private MockRestServiceServer server;
    private KasiSpecialDayClient client;

    @BeforeEach
    void setUp() {
        client = newClient(KEY);
    }

    private KasiSpecialDayClient newClient(String key) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        return new KasiSpecialDayClient(builder.build(), new SpecialDayProperties(key, BASE, null));
    }

    private static String url(String operation, int pageNo) {
        return BASE + "/" + operation + "?ServiceKey=" + ENCODED_KEY + "&solYear=" + YEAR
                + "&numOfRows=100&pageNo=" + pageNo + "&_type=json";
    }

    private static String ok(long totalCount, String items) {
        return """
                {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE."},
                 "body":{"items":%s,"numOfRows":100,"pageNo":1,"totalCount":%d}}}
                """.formatted(items, totalCount);
    }

    private static String item(String locdate, String name, String isHoliday) {
        return """
                {"dateKind":"01","dateName":"%s","isHoliday":"%s","locdate":%s,"seq":1}
                """.formatted(name, isHoliday, locdate);
    }

    private static String array(String... items) {
        return "{\"item\":[" + String.join(",", items) + "]}";
    }

    private static String single(String item) {
        return "{\"item\":" + item + "}";
    }

    private static final String EMPTY = "\"\"";

    private void expect(String operation, int pageNo, String json) {
        server.expect(requestTo(url(operation, pageNo)))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json.getBytes(StandardCharsets.UTF_8), MediaType.APPLICATION_JSON));
    }

    private void expectAllEmpty() {
        expect(KasiSpecialDayClient.OP_REST_DE, 1, ok(0, EMPTY));
        expect(KasiSpecialDayClient.OP_ANNIVERSARY, 1, ok(0, EMPTY));
        expect(KasiSpecialDayClient.OP_24_DIVISIONS, 1, ok(0, EMPTY));
    }

    private static SpecialDayData data(int month, int day, SpecialDayKind kind, String name, boolean holiday) {
        return new SpecialDayData(LocalDate.of(YEAR, month, day), kind, name, holiday);
    }

    // ---------- 정상 응답 / 매핑 ----------

    @Test
    void fetchYear_whenArrayObjectAndEmptyItems_mapsByKindRules() {
        expect(KasiSpecialDayClient.OP_REST_DE, 1, ok(3, array(
                item("20260101", "1월1일", "Y"),
                item("20260301", "삼일절", "Y"),
                item("20260717", "제헌절", "N"))));
        // 공휴일과 같은 날짜·이름의 기념일은 제외, 다른 이름은 유지
        expect(KasiSpecialDayClient.OP_ANNIVERSARY, 1, ok(2, array(
                item("20260301", "삼일절", "N"),
                item("20260303", "납세자의 날", "N"))));
        expect(KasiSpecialDayClient.OP_24_DIVISIONS, 1, ok(1, single(item("20260105", "소한", "N"))));

        List<SpecialDayData> result = client.fetchYear(YEAR);

        assertThat(result).containsExactly(
                data(1, 1, SpecialDayKind.HOLIDAY, "1월1일", true),
                data(3, 1, SpecialDayKind.HOLIDAY, "삼일절", true),
                data(7, 17, SpecialDayKind.ANNIVERSARY, "제헌절", false),
                data(3, 3, SpecialDayKind.ANNIVERSARY, "납세자의 날", false),
                data(1, 5, SpecialDayKind.SOLAR_TERM, "소한", false));
        server.verify();
    }

    @Test
    void fetchYear_whenAllItemsEmptyString_returnsEmptyList() {
        expectAllEmpty();

        assertThat(client.fetchYear(YEAR)).isEmpty();
        server.verify();
    }

    @Test
    void fetchYear_whenItemsMissingOrItemNull_returnsEmptyList() {
        expect(KasiSpecialDayClient.OP_REST_DE, 1,
                "{\"response\":{\"header\":{\"resultCode\":\"00\"},\"body\":{\"totalCount\":0}}}");
        expect(KasiSpecialDayClient.OP_ANNIVERSARY, 1, ok(0, "{\"item\":null}"));
        expect(KasiSpecialDayClient.OP_24_DIVISIONS, 1, ok(0, "{}"));

        assertThat(client.fetchYear(YEAR)).isEmpty();
    }

    @Test
    void fetchYear_whenSameKeyRepeated_keepsFirstOnly() {
        // RestDe 의 isHoliday=N 기념일과 Anniversary 의 같은 기념일은 한 건
        expect(KasiSpecialDayClient.OP_REST_DE, 1, ok(1, single(item("20260717", "제헌절", "N"))));
        expect(KasiSpecialDayClient.OP_ANNIVERSARY, 1, ok(2, array(
                item("20260717", "제헌절", "N"), item("20260717", "제헌절", "N"))));
        expect(KasiSpecialDayClient.OP_24_DIVISIONS, 1, ok(0, EMPTY));

        assertThat(client.fetchYear(YEAR)).containsExactly(data(7, 17, SpecialDayKind.ANNIVERSARY, "제헌절", false));
    }

    @Test
    void fetchYear_whenLocdateStringAndHolidayLowercaseAndNameSpaced_normalizes() {
        expect(KasiSpecialDayClient.OP_REST_DE, 1, ok(1,
                single("{\"dateName\":\"  기독탄신일 \",\"isHoliday\":\"y\",\"locdate\":\"20261225\"}")));
        expect(KasiSpecialDayClient.OP_ANNIVERSARY, 1, ok(0, EMPTY));
        expect(KasiSpecialDayClient.OP_24_DIVISIONS, 1, ok(0, EMPTY));

        assertThat(client.fetchYear(YEAR)).containsExactly(data(12, 25, SpecialDayKind.HOLIDAY, "기독탄신일", true));
    }

    @Test
    void fetchYear_whenInvalidItems_skipsWithWarn(CapturedOutput output) {
        expect(KasiSpecialDayClient.OP_REST_DE, 1, ok(7, array(
                item("20261340", "잘못된 월", "Y"),
                item("\"abc\"", "문자 날짜", "Y"),
                item("20250101", "다른 연도", "Y"),
                "{\"dateName\":\"날짜 없음\",\"isHoliday\":\"Y\"}",
                "{\"dateName\":\"실수 날짜\",\"isHoliday\":\"Y\",\"locdate\":2026.5}",
                "{\"isHoliday\":\"Y\",\"locdate\":20260505}",
                item("20260506", "가".repeat(101), "Y"),
                item("20260505", "어린이날", "Y"))));
        expect(KasiSpecialDayClient.OP_ANNIVERSARY, 1, ok(0, EMPTY));
        expect(KasiSpecialDayClient.OP_24_DIVISIONS, 1, ok(0, EMPTY));

        assertThat(client.fetchYear(YEAR)).containsExactly(data(5, 5, SpecialDayKind.HOLIDAY, "어린이날", true));
        assertThat(output).contains("잘못된 locdate", "locdate=invalid", "locdate=20250101", "잘못된 dateName");
    }

    // ---------- 페이지 ----------

    @Test
    void fetchYear_whenTotalCountExceedsNumOfRows_requestsNextPage() {
        expect(KasiSpecialDayClient.OP_REST_DE, 1, ok(101, single(item("20260101", "1월1일", "Y"))));
        expect(KasiSpecialDayClient.OP_REST_DE, 2, ok(101, single(item("20261225", "기독탄신일", "Y"))));
        expect(KasiSpecialDayClient.OP_ANNIVERSARY, 1, ok(0, EMPTY));
        expect(KasiSpecialDayClient.OP_24_DIVISIONS, 1, ok(0, EMPTY));

        assertThat(client.fetchYear(YEAR)).extracting(SpecialDayData::name).containsExactly("1월1일", "기독탄신일");
        server.verify();
    }

    @Test
    void fetchYear_whenTotalCountIsString_parsesIt() {
        expect(KasiSpecialDayClient.OP_REST_DE, 1, ok(0, single(item("20260101", "1월1일", "Y")))
                .replace("\"totalCount\":0", "\"totalCount\":\"101\""));
        expect(KasiSpecialDayClient.OP_REST_DE, 2, ok(0, EMPTY).replace("\"totalCount\":0", "\"totalCount\":\"x\""));
        expect(KasiSpecialDayClient.OP_ANNIVERSARY, 1, ok(0, EMPTY));
        expect(KasiSpecialDayClient.OP_24_DIVISIONS, 1, ok(0, EMPTY));

        assertThat(client.fetchYear(YEAR)).hasSize(1);
        server.verify();
    }

    @Test
    void fetchYear_whenMoreThanMaxPages_throwsInsteadOfReturningTruncatedData() {
        for (int page = 1; page <= KasiSpecialDayClient.MAX_PAGES; page++) {
            expect(KasiSpecialDayClient.OP_REST_DE, page,
                    ok(10_000, single(item("202601%02d".formatted(page), "휴일" + page, "Y"))));
        }

        assertThatThrownBy(() -> client.fetchYear(YEAR))
                .isInstanceOf(SpecialDaySourceException.class)
                .hasMessageContaining("truncated");
        server.verify();
    }

    @Test
    void fetchYear_whenTotalCountMissing_treatsAsSinglePage() {
        expect(KasiSpecialDayClient.OP_REST_DE, 1,
                "{\"response\":{\"header\":{\"resultCode\":\"00\"},\"body\":{\"items\":{\"item\":"
                        + item("20260101", "1월1일", "Y") + "}}}}");
        expect(KasiSpecialDayClient.OP_ANNIVERSARY, 1, ok(0, EMPTY));
        expect(KasiSpecialDayClient.OP_24_DIVISIONS, 1, ok(0, EMPTY));

        assertThat(client.fetchYear(YEAR)).hasSize(1);
        server.verify();
    }

    // ---------- 인증키 인코딩 / 로그 ----------

    @Test
    void uri_whenServiceKeyHasReservedChars_encodesExactlyOnce() {
        assertThat(client.uri(KasiSpecialDayClient.OP_REST_DE, YEAR, 1).toString())
                .isEqualTo(url(KasiSpecialDayClient.OP_REST_DE, 1))
                .contains("ServiceKey=ab%2Bc%2Fd%3D%3D&")
                .doesNotContain("%252B", "+");
    }

    @Test
    void uri_whenServiceKeyInEncodingForm_decodesOnceThenEncodesToSameUri() {
        // 공공데이터포털 마이페이지의 일반 인증키(Encoding 형태)를 그대로 넣어도 Decoding 키와 같은 URI
        KasiSpecialDayClient encodingKeyClient = newClient(ENCODED_KEY);

        assertThat(encodingKeyClient.uri(KasiSpecialDayClient.OP_REST_DE, YEAR, 1).toString())
                .isEqualTo(url(KasiSpecialDayClient.OP_REST_DE, 1))
                .doesNotContain("%25");
    }

    @Test
    void uri_whenEncodingAndDecodingKeysOfSameValue_produceIdenticalServiceKeyParam() {
        String fromEncoding = newClient("ab%2Bc%3D%3D").uri(KasiSpecialDayClient.OP_REST_DE, YEAR, 1).toString();
        String fromDecoding = newClient("ab+c==").uri(KasiSpecialDayClient.OP_REST_DE, YEAR, 1).toString();

        assertThat(fromEncoding).isEqualTo(fromDecoding).contains("ServiceKey=ab%2Bc%3D%3D&");
    }

    @Test
    void decodedServiceKey_whenNoPercent_keepsPlusAsIs() {
        assertThat(KasiSpecialDayClient.decodedServiceKey("ab+c==")).isEqualTo("ab+c==");
        assertThat(KasiSpecialDayClient.decodedServiceKey(null)).isNull();
    }

    @Test
    void decodedServiceKey_whenMalformedPercent_usesRawKeyWithoutLoggingIt(CapturedOutput output) {
        assertThat(KasiSpecialDayClient.decodedServiceKey("ab%zzc")).isEqualTo("ab%zzc");
        assertThat(output).contains("잘못된 퍼센트 인코딩").doesNotContain("ab%zzc");
    }

    // ---------- 실제 응답 픽스처 (2026년, 키 활성화 후 수집, 인증키 미포함) ----------

    private static String fixture(String name) {
        try (var in = KasiSpecialDayClientTest.class.getResourceAsStream("/specialday/kasi/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    @Test
    void fetchYear_whenRealResponses_maps22Holidays81Anniversaries24SolarTerms() {
        expect(KasiSpecialDayClient.OP_REST_DE, 1, fixture("getRestDeInfo.json"));
        expect(KasiSpecialDayClient.OP_ANNIVERSARY, 1, fixture("getAnniversaryInfo.json"));
        expect(KasiSpecialDayClient.OP_24_DIVISIONS, 1, fixture("get24DivisionsInfo.json"));

        List<SpecialDayData> result = client.fetchYear(YEAR);

        assertThat(result).filteredOn(d -> d.kind() == SpecialDayKind.HOLIDAY).hasSize(22).allMatch(SpecialDayData::holiday);
        // 기념일 82건 중 공휴일과 같은 날짜·이름인 현충일 1건 제외
        assertThat(result).filteredOn(d -> d.kind() == SpecialDayKind.ANNIVERSARY).hasSize(81).noneMatch(SpecialDayData::holiday);
        assertThat(result).filteredOn(d -> d.kind() == SpecialDayKind.SOLAR_TERM).hasSize(24);
        assertThat(result).contains(
                data(3, 2, SpecialDayKind.HOLIDAY, "대체공휴일(삼일절)", true),
                data(6, 3, SpecialDayKind.HOLIDAY, "전국동시지방선거", true),
                data(6, 6, SpecialDayKind.HOLIDAY, "현충일", true),
                data(1, 5, SpecialDayKind.SOLAR_TERM, "소한", false));
        assertThat(result).doesNotContain(data(6, 6, SpecialDayKind.ANNIVERSARY, "현충일", false));
        assertThat(result).filteredOn(d -> d.name().equals("설날")).hasSize(3);
        server.verify();
    }

    @Test
    void fetchYear_whenRealSingleObjectAndEmptyStringResponses_parsesBoth() {
        expect(KasiSpecialDayClient.OP_REST_DE, 1, fixture("getRestDeInfo-single.json"));
        expect(KasiSpecialDayClient.OP_ANNIVERSARY, 1, fixture("getRestDeInfo-empty.json"));
        expect(KasiSpecialDayClient.OP_24_DIVISIONS, 1, fixture("getRestDeInfo-empty.json"));

        assertThat(client.fetchYear(YEAR)).containsExactly(data(12, 25, SpecialDayKind.HOLIDAY, "기독탄신일", true));
    }

    @Test
    void fetchYear_whenFailuresAndWarnings_neverLogsOrThrowsServiceKey(CapturedOutput output) {
        // 1회차: 성공 + 항목 WARN, 2회차: 타임아웃
        expect(KasiSpecialDayClient.OP_REST_DE, 1, ok(1, single(item("20261340", "잘못된 날짜", "Y"))));
        expect(KasiSpecialDayClient.OP_ANNIVERSARY, 1, ok(0, EMPTY));
        expect(KasiSpecialDayClient.OP_24_DIVISIONS, 1, ok(0, EMPTY));
        server.expect(requestTo(url(KasiSpecialDayClient.OP_REST_DE, 1)))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));
        assertThat(client.fetchYear(YEAR)).isEmpty();

        assertThatThrownBy(() -> client.fetchYear(YEAR))
                .isInstanceOf(SpecialDaySourceException.class)
                .hasNoCause()
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(KEY, ENCODED_KEY, "ServiceKey"));
        assertThat(output).contains("잘못된 locdate").doesNotContain(KEY, ENCODED_KEY, "ServiceKey=");
    }

    // ---------- 오류 ----------

    @Test
    void fetchYear_whenResultCodeNotSuccess_throwsWithSafeCode() {
        expect(KasiSpecialDayClient.OP_REST_DE, 1,
                "{\"response\":{\"header\":{\"resultCode\":\"22\",\"resultMsg\":\"LIMITED NUMBER OF SERVICE REQUESTS EXCEEDS ERROR.\"}}}");

        assertThatThrownBy(() -> client.fetchYear(YEAR))
                .isInstanceOf(SpecialDaySourceException.class)
                .hasMessageContaining("resultCode=22")
                .hasMessageNotContaining("LIMITED");
    }

    @Test
    void fetchYear_whenResultCodeUnsafeOrMissing_throwsWithUnknownCode() {
        expect(KasiSpecialDayClient.OP_REST_DE, 1, "{\"response\":{\"header\":{\"resultCode\":\"a b<script>\"}}}");

        assertThatThrownBy(() -> client.fetchYear(YEAR))
                .isInstanceOf(SpecialDaySourceException.class)
                .hasMessageContaining("resultCode=unknown");

        KasiSpecialDayClient other = newClient(KEY);
        expect(KasiSpecialDayClient.OP_REST_DE, 1, "{\"unexpected\":true}");
        assertThatThrownBy(() -> other.fetchYear(YEAR)).hasMessageContaining("resultCode=unknown");
    }

    @Test
    void fetchYear_whenHttp200WithXmlContentType_throwsGatewayError() {
        server.expect(requestTo(url(KasiSpecialDayClient.OP_REST_DE, 1)))
                .andRespond(withSuccess("""
                        <OpenAPI_ServiceResponse><cmmMsgHeader><errMsg>SERVICE ERROR</errMsg>
                        <returnAuthMsg>SERVICE_KEY_IS_NOT_REGISTERED_ERROR</returnAuthMsg>
                        <returnReasonCode>30</returnReasonCode></cmmMsgHeader></OpenAPI_ServiceResponse>
                        """, MediaType.TEXT_XML));

        assertThatThrownBy(() -> client.fetchYear(YEAR))
                .isInstanceOf(SpecialDaySourceException.class)
                .hasMessageContaining("XML")
                .hasMessageContaining("returnReasonCode=30")
                .hasMessageContaining("errMsg=SERVICE ERROR")
                .hasMessageContaining("returnAuthMsg=SERVICE_KEY_IS_NOT_REGISTERED_ERROR")
                .hasMessageNotContaining("<");
    }

    /** 키 활성화 전 실제 응답: HTTP 403 + JSON OpenAPI_ServiceResponse (2026-09-30 확인) */
    private static final String NOT_REGISTERED_JSON = """
            { "OpenAPI_ServiceResponse": { "cmmMsgHeader": { "errMsg": "SERVICE_KEY_IS_NOT_REGISTERED_ERROR",
              "returnAuthMsg": "등록되지 않은 서비스키", "returnReasonCode": "30" } } }
            """;

    @Test
    void fetchYear_whenHttp403WithJsonGatewayError_throwsWithReasonCodeAndErrMsgOnly(CapturedOutput output) {
        server.expect(requestTo(url(KasiSpecialDayClient.OP_REST_DE, 1)))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON)
                        .body(NOT_REGISTERED_JSON.getBytes(StandardCharsets.UTF_8)));

        assertThatThrownBy(() -> client.fetchYear(YEAR))
                .isInstanceOf(SpecialDaySourceException.class)
                .hasNoCause()
                .hasMessageContaining("HTTP 403")
                .hasMessageContaining("returnReasonCode=30")
                .hasMessageContaining("errMsg=SERVICE_KEY_IS_NOT_REGISTERED_ERROR")
                .hasMessageNotContaining("등록되지 않은")
                .hasMessageNotContaining("returnAuthMsg")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(KEY, ENCODED_KEY));
        assertThat(output).doesNotContain(KEY, ENCODED_KEY);
    }

    @Test
    void fetchYear_whenHttp200WithJsonGatewayError_throwsGatewayError() {
        expect(KasiSpecialDayClient.OP_REST_DE, 1, NOT_REGISTERED_JSON);

        assertThatThrownBy(() -> client.fetchYear(YEAR))
                .isInstanceOf(SpecialDaySourceException.class)
                .hasMessageContaining("gateway error")
                .hasMessageContaining("returnReasonCode=30")
                .hasMessageNotContaining("resultCode=");
    }

    @Test
    void fetchYear_whenGatewayErrorFieldsUnsafeOrMissing_omitsThem() {
        expect(KasiSpecialDayClient.OP_REST_DE, 1,
                "{\"OpenAPI_ServiceResponse\":{\"cmmMsgHeader\":{\"errMsg\":\"<b>x</b>\",\"returnReasonCode\":\"3 0\"}}}");

        assertThatThrownBy(() -> client.fetchYear(YEAR))
                .isInstanceOf(SpecialDaySourceException.class)
                .hasMessageEndingWith("gateway error");

        KasiSpecialDayClient other = newClient(KEY);
        expect(KasiSpecialDayClient.OP_REST_DE, 1, "{\"OpenAPI_ServiceResponse\":\"x\"}");
        assertThatThrownBy(() -> other.fetchYear(YEAR)).hasMessageEndingWith("gateway error");
    }

    @Test
    void gatewayDetail_whenBodyEmptyOrNotGateway_returnsEmpty() {
        assertThat(KasiSpecialDayClient.gatewayDetail(null)).isEmpty();
        assertThat(KasiSpecialDayClient.gatewayDetail(new byte[0])).isEmpty();
        assertThat(KasiSpecialDayClient.gatewayDetail("Forbidden".getBytes(StandardCharsets.UTF_8))).isEmpty();
        assertThat(KasiSpecialDayClient.gatewayDetail("{\"a\":1}".getBytes(StandardCharsets.UTF_8))).isEmpty();
        assertThat(KasiSpecialDayClient.gatewayDetail("<x><errMsg></errMsg></x>".getBytes(StandardCharsets.UTF_8))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"<OpenAPI_ServiceResponse/>", "  \n<OpenAPI_ServiceResponse/>", "﻿<OpenAPI_ServiceResponse/>"})
    void fetchYear_whenHttp200WithXmlBodyButJsonContentType_throwsGatewayError(String body) {
        server.expect(requestTo(url(KasiSpecialDayClient.OP_REST_DE, 1)))
                .andRespond(withSuccess(body.getBytes(StandardCharsets.UTF_8), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetchYear(YEAR))
                .isInstanceOf(SpecialDaySourceException.class)
                .hasMessageContaining("XML");
    }

    @Test
    void fetchYear_whenNoContentType_parsesJsonBody() {
        server.expect(requestTo(url(KasiSpecialDayClient.OP_REST_DE, 1)))
                .andRespond(withStatus(HttpStatus.OK).body(ok(0, EMPTY).getBytes(StandardCharsets.UTF_8)));
        expect(KasiSpecialDayClient.OP_ANNIVERSARY, 1, ok(0, EMPTY));
        expect(KasiSpecialDayClient.OP_24_DIVISIONS, 1, ok(0, EMPTY));

        assertThat(client.fetchYear(YEAR)).isEmpty();
    }

    @Test
    void fetchYear_whenServerError_throwsWithStatus() {
        server.expect(requestTo(url(KasiSpecialDayClient.OP_REST_DE, 1))).andRespond(withServerError());

        assertThatThrownBy(() -> client.fetchYear(YEAR))
                .isInstanceOf(SpecialDaySourceException.class)
                .hasMessageContaining("HTTP 500")
                .hasNoCause();
    }

    @Test
    void fetchYear_whenClientError_throwsWithStatus() {
        server.expect(requestTo(url(KasiSpecialDayClient.OP_REST_DE, 1)))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).body("Forbidden"));

        assertThatThrownBy(() -> client.fetchYear(YEAR)).hasMessageEndingWith("HTTP 403");
    }

    @Test
    void fetchYear_whenTimeout_throwsSourceException() {
        server.expect(requestTo(url(KasiSpecialDayClient.OP_REST_DE, 1)))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> client.fetchYear(YEAR))
                .isInstanceOf(SpecialDaySourceException.class)
                .hasMessageContaining("ResourceAccessException");
    }

    @Test
    void fetchYear_whenLaterOperationFails_throwsWithoutPartialResult() {
        expect(KasiSpecialDayClient.OP_REST_DE, 1, ok(1, single(item("20260101", "1월1일", "Y"))));
        expect(KasiSpecialDayClient.OP_ANNIVERSARY, 1, ok(0, EMPTY));
        server.expect(requestTo(url(KasiSpecialDayClient.OP_24_DIVISIONS, 1))).andRespond(withServerError());

        assertThatThrownBy(() -> client.fetchYear(YEAR))
                .isInstanceOf(SpecialDaySourceException.class)
                .hasMessageContaining(KasiSpecialDayClient.OP_24_DIVISIONS);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{not-json", "[1,2", ""})
    void fetchYear_whenMalformedOrEmptyBody_throwsSourceException(String body) {
        server.expect(requestTo(url(KasiSpecialDayClient.OP_REST_DE, 1)))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetchYear(YEAR)).isInstanceOf(SpecialDaySourceException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"5", "[]", "\"x\"", "true"})
    void fetchYear_whenItemsFormatUnexpected_throwsSourceException(String items) {
        server.expect(requestTo(url(KasiSpecialDayClient.OP_REST_DE, 1)))
                .andRespond(withSuccess(ok(1, items), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetchYear(YEAR))
                .isInstanceOf(SpecialDaySourceException.class)
                .hasMessageContaining("unexpected");
    }

    @Test
    void fetchYear_whenItemIsScalar_throwsSourceException() {
        server.expect(requestTo(url(KasiSpecialDayClient.OP_REST_DE, 1)))
                .andRespond(withSuccess(ok(1, "{\"item\":\"x\"}"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetchYear(YEAR)).hasMessageContaining("unexpected item format");
    }

    // ---------- 키 미설정 ----------

    @Test
    void fetchYear_whenServiceKeyNotConfigured_throwsWithoutCallAndLogsInfoOnce(CapturedOutput output) {
        KasiSpecialDayClient unconfigured = newClient("  ");
        server.expect(ExpectedCount.never(), requestTo(url(KasiSpecialDayClient.OP_REST_DE, 1)));

        assertThat(unconfigured.isConfigured()).isFalse();
        assertThatThrownBy(() -> unconfigured.fetchYear(YEAR))
                .isInstanceOf(SpecialDaySourceException.class)
                .hasMessageContaining("not configured");
        server.verify();
        assertThat(output.getOut().split("DATA_GO_KR_SERVICE_KEY", -1)).hasSize(2);
    }

    @Test
    void isConfigured_whenServiceKeySet_returnsTrue(CapturedOutput output) {
        assertThat(client.isConfigured()).isTrue();
        assertThat(output).doesNotContain("DATA_GO_KR_SERVICE_KEY");
    }
}
