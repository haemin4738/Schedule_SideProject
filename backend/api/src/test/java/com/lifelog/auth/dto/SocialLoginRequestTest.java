package com.lifelog.auth.dto;

import com.lifelog.common.exception.BusinessException;
import com.lifelog.domain.user.social.SocialCredential;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SocialLoginRequestTest {

    @Test
    void toCredential_whenAuthorizationCode_mapsAllFieldsAndBlankOptionalsToNull() {
        SocialLoginRequest request = new SocialLoginRequest(SocialGrantType.AUTHORIZATION_CODE,
                "code", "http://cb", " ", "nonce", "", null, null);

        assertThat(request.toCredential())
                .isEqualTo(new SocialCredential.AuthorizationCode("code", "http://cb", null, "nonce", null));
    }

    @Test
    void toCredential_whenAuthorizationCodeWithState_keepsState() {
        SocialLoginRequest request = new SocialLoginRequest(SocialGrantType.AUTHORIZATION_CODE,
                "code", "http://cb", "verifier", null, "st", null, null);

        assertThat(request.toCredential())
                .isEqualTo(new SocialCredential.AuthorizationCode("code", "http://cb", "verifier", null, "st"));
    }

    @Test
    void toCredential_whenAccessToken_returnsAccessToken() {
        SocialLoginRequest request = new SocialLoginRequest(SocialGrantType.ACCESS_TOKEN,
                null, null, null, null, null, "at", null);

        assertThat(request.toCredential()).isEqualTo(new SocialCredential.AccessToken("at"));
    }

    @Test
    void toCredential_whenIdToken_returnsIdTokenWithNonce() {
        SocialLoginRequest request = new SocialLoginRequest(SocialGrantType.ID_TOKEN,
                null, null, null, "n", null, null, "jwt");

        assertThat(request.toCredential()).isEqualTo(new SocialCredential.IdToken("jwt", "n"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  "})
    void toCredential_whenCodeMissing_throwsBadRequest(String code) {
        assertBadRequest(new SocialLoginRequest(SocialGrantType.AUTHORIZATION_CODE,
                code, "http://cb", null, null, null, null, null), "code은(는) 필수입니다.");
    }

    @Test
    void toCredential_whenRedirectUriMissing_throwsBadRequest() {
        assertBadRequest(new SocialLoginRequest(SocialGrantType.AUTHORIZATION_CODE,
                "code", null, null, null, null, null, null), "redirectUri은(는) 필수입니다.");
    }

    @Test
    void toCredential_whenAccessTokenMissing_throwsBadRequest() {
        assertBadRequest(new SocialLoginRequest(SocialGrantType.ACCESS_TOKEN,
                "code", "http://cb", null, null, null, " ", "jwt"), "accessToken은(는) 필수입니다.");
    }

    @Test
    void toCredential_whenIdTokenMissing_throwsBadRequest() {
        assertBadRequest(new SocialLoginRequest(SocialGrantType.ID_TOKEN,
                null, null, null, null, null, "at", null), "idToken은(는) 필수입니다.");
    }

    private static void assertBadRequest(SocialLoginRequest request, String message) {
        assertThatThrownBy(request::toCredential)
                .isInstanceOf(BusinessException.class)
                .hasMessage(message)
                .extracting(e -> ((BusinessException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
