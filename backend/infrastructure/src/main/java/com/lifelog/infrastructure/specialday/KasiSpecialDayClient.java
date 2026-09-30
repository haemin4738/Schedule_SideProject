package com.lifelog.infrastructure.specialday;

import com.lifelog.domain.specialday.SpecialDay;
import com.lifelog.domain.specialday.SpecialDayData;
import com.lifelog.domain.specialday.SpecialDayKind;
import com.lifelog.domain.specialday.SpecialDaySource;
import com.lifelog.domain.specialday.SpecialDaySourceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 한국천문연구원 특일 정보 API(공공데이터포털 SpcdeInfoService) 클라이언트.
 * 연도 단위로 공휴일(getRestDeInfo)·기념일(getAnniversaryInfo)·24절기(get24DivisionsInfo)를 모두 받아
 * {@link SpecialDayData} 로 변환한다. 세 오퍼레이션 중 하나라도 실패하면 예외를 던진다.
 *
 * 게이트웨이 오류(키 미등록·트래픽 초과)는 4xx/5xx, 200 + XML, 200/4xx + JSON {@code OpenAPI_ServiceResponse} 로 오며 모두 예외로 처리한다.
 *
 * <p>보안: 인증키(Encoding 형태면 한 번 디코딩한 원문)는 URI 변수로만 전달해 한 번만 엄격 인코딩하고(+ → %2B), 요청 URI·응답 본문은
 * 로그·예외 메시지에 넣지 않는다(RestClient 예외 메시지에 URI 가 포함되므로 원인 예외도 연결하지 않는다).
 */
@Slf4j
@Component
public class KasiSpecialDayClient implements SpecialDaySource {

    static final String OP_REST_DE = "getRestDeInfo";
    static final String OP_ANNIVERSARY = "getAnniversaryInfo";
    static final String OP_24_DIVISIONS = "get24DivisionsInfo";
    static final int NUM_OF_ROWS = 100;
    static final int MAX_PAGES = 5;

    private static final String SUCCESS_CODE = "00";
    private static final Pattern SAFE_CODE = Pattern.compile("[A-Za-z0-9_]{1,20}");
    /** 게이트웨이 오류 문구로 기록해도 안전한 형태 (SERVICE_KEY_IS_NOT_REGISTERED_ERROR, SERVICE ERROR 등) */
    private static final Pattern SAFE_MESSAGE = Pattern.compile("[A-Za-z0-9_ .\\-]{1,80}");
    private static final String GATEWAY_ROOT = "OpenAPI_ServiceResponse";
    private static final Pattern LOCDATE = Pattern.compile("\\d{8}");
    private static final JsonMapper MAPPER = JsonMapper.shared();

    private final RestClient restClient;
    private final SpecialDayProperties properties;
    /** URI 변수로 넘길 원문(Decoding) 키 */
    private final String serviceKey;

    public KasiSpecialDayClient(@Qualifier("specialDayRestClient") RestClient restClient,
                                SpecialDayProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
        this.serviceKey = decodedServiceKey(properties.serviceKey());
        if (!properties.hasServiceKey()) {
            log.info("특일 API 인증키(DATA_GO_KR_SERVICE_KEY)가 설정되지 않아 공휴일·기념일·절기 외부 조회를 생략합니다.");
        }
    }

    @Override
    public boolean isConfigured() {
        return properties.hasServiceKey();
    }

    @Override
    public List<SpecialDayData> fetchYear(int year) {
        if (!isConfigured()) {
            throw new SpecialDaySourceException("special-day service key is not configured");
        }
        List<JsonNode> restDe = fetchAll(OP_REST_DE, year);
        List<JsonNode> anniversaries = fetchAll(OP_ANNIVERSARY, year);
        List<JsonNode> divisions = fetchAll(OP_24_DIVISIONS, year);
        return map(year, restDe, anniversaries, divisions);
    }

    // ---------- 매핑 ----------

    /**
     * RestDe isHoliday=Y → HOLIDAY, 그 외 → ANNIVERSARY / Anniversary → ANNIVERSARY(같은 날짜·이름의 HOLIDAY 가 있으면 제외)
     * / 24Divisions → SOLAR_TERM. 같은 (날짜, 종류, 이름)은 처음 것만 남긴다.
     */
    static List<SpecialDayData> map(int year, List<JsonNode> restDe, List<JsonNode> anniversaries, List<JsonNode> divisions) {
        Map<SpecialDayData.Key, SpecialDayData> result = new LinkedHashMap<>();
        for (JsonNode item : restDe) {
            boolean holiday = "Y".equalsIgnoreCase(text(item.get("isHoliday")));
            toData(OP_REST_DE, year, item, holiday ? SpecialDayKind.HOLIDAY : SpecialDayKind.ANNIVERSARY, holiday)
                    .ifPresent(d -> result.putIfAbsent(d.key(), d));
        }
        Set<SpecialDayData.Key> holidays = result.values().stream()
                .filter(d -> d.kind() == SpecialDayKind.HOLIDAY)
                .map(SpecialDayData::key)
                .collect(Collectors.toSet());
        for (JsonNode item : anniversaries) {
            toData(OP_ANNIVERSARY, year, item, SpecialDayKind.ANNIVERSARY, false)
                    .filter(d -> !holidays.contains(new SpecialDayData.Key(d.date(), SpecialDayKind.HOLIDAY, d.name())))
                    .ifPresent(d -> result.putIfAbsent(d.key(), d));
        }
        for (JsonNode item : divisions) {
            toData(OP_24_DIVISIONS, year, item, SpecialDayKind.SOLAR_TERM, false)
                    .ifPresent(d -> result.putIfAbsent(d.key(), d));
        }
        return List.copyOf(result.values());
    }

    private static Optional<SpecialDayData> toData(String operation, int year, JsonNode item,
                                                            SpecialDayKind kind, boolean holiday) {
        String rawDate = text(item.get("locdate"));
        LocalDate date = parseLocdate(rawDate);
        if (date == null || date.getYear() != year) {
            log.warn("특일 항목 건너뜀(잘못된 locdate): operation={}, year={}, locdate={}",
                    operation, year, rawDate != null && LOCDATE.matcher(rawDate).matches() ? rawDate : "invalid");
            return Optional.empty();
        }
        String name = text(item.get("dateName"));
        if (name == null || name.length() > SpecialDay.NAME_MAX_LENGTH) {
            log.warn("특일 항목 건너뜀(잘못된 dateName): operation={}, year={}, locdate={}", operation, year, rawDate);
            return Optional.empty();
        }
        return Optional.of(new SpecialDayData(date, kind, name, holiday));
    }

    private static LocalDate parseLocdate(String raw) {
        if (raw == null || !LOCDATE.matcher(raw).matches()) {
            return null;
        }
        try {
            return LocalDate.parse(raw, DateTimeFormatter.BASIC_ISO_DATE);
        } catch (DateTimeException e) {
            return null;
        }
    }

    // ---------- 호출 ----------

    /** totalCount 가 한 페이지(numOfRows)를 넘으면 pageNo 를 올려 최대 {@link #MAX_PAGES} 페이지까지 받는다. 넘으면 실패 */
    private List<JsonNode> fetchAll(String operation, int year) {
        List<JsonNode> items = new ArrayList<>();
        for (int pageNo = 1; pageNo <= MAX_PAGES; pageNo++) {
            Page page = fetchPage(operation, year, pageNo);
            items.addAll(page.items());
            if (page.items().isEmpty() || page.totalCount() <= (long) pageNo * NUM_OF_ROWS) {
                return items;
            }
        }
        // 잘린 데이터로 연도를 교체하지 않는다(부분 갱신 금지)
        throw failure(operation, year, "result exceeds " + MAX_PAGES + " pages (truncated)");
    }

    private Page fetchPage(String operation, int year, int pageNo) {
        ResponseEntity<byte[]> response;
        try {
            response = restClient.get()
                    .uri(uri(operation, year, pageNo))
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .toEntity(byte[].class);
        } catch (RestClientResponseException e) {
            // 게이트웨이 오류(키 미등록 등)는 4xx + JSON/XML OpenAPI_ServiceResponse 로도 온다
            throw failure(operation, year, "HTTP " + e.getStatusCode().value()
                    + gatewayDetail(e.getResponseBodyAsByteArray()));
        } catch (RestClientException e) {
            // 타임아웃·연결 실패 등 — 예외 메시지에 요청 URI(인증키)가 포함되므로 타입만 남긴다
            throw failure(operation, year, e.getClass().getSimpleName());
        }
        return parse(operation, year, response);
    }

    /** 인증키는 URI 변수로 전달 → encode() 후 expand 시 예약문자까지 한 번만 인코딩된다(+ → %2B, / → %2F, = → %3D) */
    URI uri(String operation, int year, int pageNo) {
        return UriComponentsBuilder.fromUriString(properties.baseUrl())
                .pathSegment(operation)
                .queryParam("ServiceKey", "{serviceKey}")
                .queryParam("solYear", "{solYear}")
                .queryParam("numOfRows", "{numOfRows}")
                .queryParam("pageNo", "{pageNo}")
                .queryParam("_type", "json")
                .encode()
                .buildAndExpand(Map.of(
                        "serviceKey", serviceKey,
                        "solYear", year,
                        "numOfRows", NUM_OF_ROWS,
                        "pageNo", pageNo))
                .toUri();
    }

    /**
     * 공공데이터포털 일반 인증키는 Encoding 형태(%2B, %3D 포함)로 보이는 경우가 많다.
     * {@code %} 가 있으면 한 번 퍼센트 디코딩해 원문으로 되돌린다({@code +} 는 공백으로 바꾸지 않는다).
     * {@code %} 가 없으면 이미 원문(Decoding) 키로 보고 그대로 쓴다. 두 입력 모두 URI 에서 같은 값이 된다.
     */
    static String decodedServiceKey(String key) {
        if (key == null || key.indexOf('%') < 0) {
            return key;
        }
        try {
            return UriUtils.decode(key, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            log.warn("특일 API 인증키에 잘못된 퍼센트 인코딩이 있어 입력값 그대로 사용합니다.");
            return key;
        }
    }

    private static Page parse(String operation, int year, ResponseEntity<byte[]> response) {
        byte[] body = response.getBody();
        if (body == null || body.length == 0) {
            throw failure(operation, year, "empty body");
        }
        // 게이트웨이 오류(키 미등록·트래픽 초과 등)는 HTTP 200 + XML 또는 JSON OpenAPI_ServiceResponse 로도 온다
        MediaType contentType = response.getHeaders().getContentType();
        if (isXml(contentType) || startsWithTag(body)) {
            throw failure(operation, year, "gateway error (XML response)" + gatewayDetail(body));
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(body);
        } catch (JacksonException e) {
            throw failure(operation, year, "malformed JSON");
        }
        if (root.has(GATEWAY_ROOT)) {
            throw failure(operation, year, "gateway error" + jsonGatewayDetail(root));
        }
        JsonNode resp = root.path("response");
        String resultCode = text(resp.path("header").get("resultCode"));
        if (!SUCCESS_CODE.equals(resultCode)) {
            String safe = resultCode != null && SAFE_CODE.matcher(resultCode).matches() ? resultCode : "unknown";
            throw failure(operation, year, "resultCode=" + safe);
        }
        JsonNode resultBody = resp.path("body");
        return new Page(items(operation, year, resultBody.path("items")), longValue(resultBody.get("totalCount")));
    }

    /** items.item 은 배열 / 단일 객체 / (결과 없음) items 가 빈 문자열 "" 세 형태 */
    private static List<JsonNode> items(String operation, int year, JsonNode items) {
        if (items.isMissingNode() || items.isNull() || (items.isString() && items.stringValue().isBlank())) {
            return List.of();
        }
        if (!items.isObject()) {
            throw failure(operation, year, "unexpected items format");
        }
        JsonNode item = items.path("item");
        if (item.isArray()) {
            List<JsonNode> list = new ArrayList<>();
            item.forEach(list::add);
            return list;
        }
        if (item.isObject()) {
            return List.of(item);
        }
        if (item.isMissingNode() || item.isNull()) {
            return List.of();
        }
        throw failure(operation, year, "unexpected item format");
    }

    /**
     * 게이트웨이 오류 본문(JSON/XML OpenAPI_ServiceResponse.cmmMsgHeader)에서 returnReasonCode·errMsg·returnAuthMsg 중
     * 안전한 형식(영숫자 코드)만 뽑는다. 본문 전체·한글 문구는 남기지 않는다. 해당 없으면 빈 문자열.
     */
    static String gatewayDetail(byte[] body) {
        if (body == null || body.length == 0) {
            return "";
        }
        if (startsWithTag(body)) {
            String xml = new String(body, StandardCharsets.UTF_8);
            return detail(xmlValue(xml, "returnReasonCode"), xmlValue(xml, "errMsg"), xmlValue(xml, "returnAuthMsg"));
        }
        try {
            return jsonGatewayDetail(MAPPER.readTree(body));
        } catch (JacksonException e) {
            return "";
        }
    }

    private static String jsonGatewayDetail(JsonNode root) {
        JsonNode header = root.path(GATEWAY_ROOT).path("cmmMsgHeader");
        if (!header.isObject()) {
            return "";
        }
        return detail(text(header.get("returnReasonCode")), text(header.get("errMsg")), text(header.get("returnAuthMsg")));
    }

    private static String detail(String reasonCode, String errMsg, String authMsg) {
        StringBuilder sb = new StringBuilder();
        if (reasonCode != null && SAFE_CODE.matcher(reasonCode).matches()) {
            sb.append(", returnReasonCode=").append(reasonCode);
        }
        if (errMsg != null && SAFE_MESSAGE.matcher(errMsg).matches()) {
            sb.append(", errMsg=").append(errMsg);
        }
        if (authMsg != null && SAFE_MESSAGE.matcher(authMsg).matches()) {
            sb.append(", returnAuthMsg=").append(authMsg);
        }
        return sb.toString();
    }

    private static String xmlValue(String xml, String tag) {
        Matcher m = Pattern.compile("<" + tag + ">\\s*([^<]{0,100}?)\\s*</" + tag + ">").matcher(xml);
        return m.find() && !m.group(1).isEmpty() ? m.group(1) : null;
    }

    private static boolean isXml(MediaType contentType) {
        return contentType != null && contentType.getSubtype().toLowerCase().contains("xml");
    }

    private static boolean startsWithTag(byte[] body) {
        int i = 0;
        // UTF-8 BOM
        if (body.length >= 3 && (body[0] & 0xFF) == 0xEF && (body[1] & 0xFF) == 0xBB && (body[2] & 0xFF) == 0xBF) {
            i = 3;
        }
        while (i < body.length && Character.isWhitespace(body[i])) {
            i++;
        }
        return i < body.length && body[i] == '<';
    }

    private static SpecialDaySourceException failure(String operation, int year, String reason) {
        return new SpecialDaySourceException("special-day " + operation + " failed: year=" + year + ", " + reason);
    }

    /** 문자열/숫자 노드만 문자열로(앞뒤 공백 제거), 그 외·빈 값은 null */
    private static String text(JsonNode node) {
        if (node == null) {
            return null;
        }
        String value;
        if (node.isString()) {
            value = node.stringValue().trim();
        } else if (node.isIntegralNumber()) {
            value = node.asString();
        } else {
            return null;
        }
        return value.isEmpty() ? null : value;
    }

    private static long longValue(JsonNode node) {
        String value = text(node);
        if (value == null) {
            return 0;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private record Page(List<JsonNode> items, long totalCount) {
    }
}
