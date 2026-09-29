package com.lifelog.domain.user;

import com.lifelog.domain.user.social.SocialProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SignupProviderTest {

    @Test
    void from_whenEachSocialProvider_mapsToSameName() {
        assertThat(SignupProvider.from(SocialProvider.KAKAO)).isEqualTo(SignupProvider.KAKAO);
        assertThat(SignupProvider.from(SocialProvider.NAVER)).isEqualTo(SignupProvider.NAVER);
        assertThat(SignupProvider.from(SocialProvider.GOOGLE)).isEqualTo(SignupProvider.GOOGLE);
    }

    @Test
    void from_whenNull_throwsNullPointerException() {
        assertThatThrownBy(() -> SignupProvider.from(null)).isInstanceOf(NullPointerException.class);
    }
}
