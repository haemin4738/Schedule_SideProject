package com.lifelog.domain.user.social;

import com.lifelog.domain.user.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SocialAccountTest {

    @Test
    void create_whenValid_setsUserProviderAndProviderUserId() {
        User user = User.create("owner@test.com", "encoded-pw", "소유자");

        SocialAccount account = SocialAccount.create(user, SocialProvider.KAKAO, "kakao-123");

        assertThat(account.getUser()).isSameAs(user);
        assertThat(account.getProvider()).isEqualTo(SocialProvider.KAKAO);
        assertThat(account.getProviderUserId()).isEqualTo("kakao-123");
        assertThat(account.getId()).isNull();
        assertThat(account.getCreatedAt()).isNull();
    }

    @Test
    void socialAuthException_whenCreatedWithCause_keepsReasonAndCause() {
        RuntimeException cause = new RuntimeException("io");

        SocialAuthException e = new SocialAuthException(SocialAuthException.Reason.PROVIDER_UNAVAILABLE, "msg", cause);

        assertThat(e.getReason()).isEqualTo(SocialAuthException.Reason.PROVIDER_UNAVAILABLE);
        assertThat(e.getCause()).isSameAs(cause);
        assertThat(new SocialAuthException(SocialAuthException.Reason.INVALID_REQUEST, "m").getReason())
                .isEqualTo(SocialAuthException.Reason.INVALID_REQUEST);
    }
}
