package com.lifelog.domain.user;

import com.lifelog.domain.user.social.SocialProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * User 정적 팩토리 / 소셜 전용 회원 판별 단위 테스트 (순수 자바, DB 없음).
 */
class UserTest {

    @Test
    void create_whenLocalSignup_setsPasswordAndLocalProvider() {
        User user = User.create("local@test.com", "encoded-pw", "로컬");

        assertThat(user.getEmail()).isEqualTo("local@test.com");
        assertThat(user.getPassword()).isEqualTo("encoded-pw");
        assertThat(user.getName()).isEqualTo("로컬");
        assertThat(user.getSignupProvider()).isEqualTo(SignupProvider.LOCAL);
        assertThat(user.hasPassword()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(SocialProvider.class)
    void createSocial_whenSocialSignup_hasNoPasswordAndMatchingProvider(SocialProvider provider) {
        User user = User.createSocial("social@test.com", "소셜", provider);

        assertThat(user.getEmail()).isEqualTo("social@test.com");
        assertThat(user.getName()).isEqualTo("소셜");
        assertThat(user.getPassword()).isNull();
        assertThat(user.hasPassword()).isFalse();
        assertThat(user.getSignupProvider().name()).isEqualTo(provider.name());
    }

    @Test
    void hasPassword_whenPasswordNull_returnsFalse() {
        assertThat(User.create("a@test.com", null, "이름").hasPassword()).isFalse();
    }
}
