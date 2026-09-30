package com.lifelog.common.dto;

import com.lifelog.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Jackson 3 에서 record 컴포넌트에 붙인 @JsonInclude(NON_NULL) 가 해당 필드에만 적용되는지 확인한다.
 * (Boot 설정 매퍼 기준 검증은 AuthControllerTest 의 MockMvc 응답으로 확인)
 */
class ApiResponseTest {

    // 애플리케이션 설정(spring.jackson.use-jackson2-defaults: true)과 같은 기본값
    private final JsonMapper mapper = JsonMapper.builderWithJackson2Defaults().build();
    private final JsonMapper jackson3DefaultMapper = JsonMapper.builder().build();

    @Test
    void serialize_whenOkWithNullData_keepsDataNullAndOmitsCode() {
        String json = mapper.writeValueAsString(ApiResponse.ok(null));

        assertThat(json).isEqualTo("{\"success\":true,\"data\":null,\"error\":null}");
    }

    @Test
    void serialize_whenErrorWithoutCode_omitsCode() {
        String json = mapper.writeValueAsString(ApiResponse.error("오류"));

        assertThat(json).isEqualTo("{\"success\":false,\"data\":null,\"error\":\"오류\"}");
    }

    @Test
    void serialize_whenErrorWithCode_includesCodeName() {
        String json = mapper.writeValueAsString(ApiResponse.error("폐기됨", ErrorCode.SESSION_REVOKED));

        assertThat(json).isEqualTo(
                "{\"success\":false,\"data\":null,\"error\":\"폐기됨\",\"code\":\"SESSION_REVOKED\"}");
    }

    @Test
    void serialize_whenJackson3Defaults_stillAppliesFieldLevelNonNull() {
        JsonNode node = jackson3DefaultMapper.readTree(jackson3DefaultMapper.writeValueAsString(ApiResponse.ok(null)));

        assertThat(node.has("data")).isTrue();
        assertThat(node.get("data").isNull()).isTrue();
        assertThat(node.has("code")).isFalse();
    }
}
