package com.lifelog.auth;

import com.lifelog.common.exception.BusinessException;
import com.lifelog.domain.user.SignupProvider;
import com.lifelog.domain.user.User;
import com.lifelog.expense.ExpenseCategoryService;
import com.lifelog.domain.user.UserRepository;
import com.lifelog.domain.user.social.PendingSocialLink;
import com.lifelog.domain.user.social.SocialAccount;
import com.lifelog.domain.user.social.SocialAccountRepository;
import com.lifelog.domain.user.social.SocialProvider;
import com.lifelog.domain.user.social.SocialUserInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SocialAccountRegistrar 단위 테스트.
 * api 모듈에는 DB 통합 테스트 인프라가 없으므로 판정 로직은 Mock 으로 검증하고,
 * unique 제약·fetch join 등 실제 DB 동작은 infrastructure 의 SocialAccountRepositoryImplTest 에서 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class SocialAccountRegistrarTest {

    @Mock private UserRepository userRepository;
    @Mock private SocialAccountRepository socialAccountRepository;
    @Mock private ExpenseCategoryService expenseCategoryService;

    private SocialAccountRegistrar registrar;

    private static final PendingSocialLink PENDING = new PendingSocialLink(5L, SocialProvider.GOOGLE, "google-sub");

    @BeforeEach
    void setUp() {
        registrar = new SocialAccountRegistrar(userRepository, socialAccountRepository, expenseCategoryService);
    }

    private static User localUser(long id) {
        User user = User.create("u@test.com", "encoded-pw", "사용자");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    @Test
    void registerNewUser_whenCalled_savesSocialUserAndAccount() {
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        SocialUserInfo info = new SocialUserInfo(SocialProvider.KAKAO, "kakao-1", "new@test.com", true, "  카카오닉  ");

        User user = registrar.registerNewUser(info);

        assertThat(user.getEmail()).isEqualTo("new@test.com");
        assertThat(user.getName()).isEqualTo("카카오닉");
        assertThat(user.hasPassword()).isFalse();
        assertThat(user.getSignupProvider()).isEqualTo(SignupProvider.KAKAO);

        ArgumentCaptor<SocialAccount> captor = ArgumentCaptor.forClass(SocialAccount.class);
        verify(socialAccountRepository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isSameAs(user);
        assertThat(captor.getValue().getProvider()).isEqualTo(SocialProvider.KAKAO);
        assertThat(captor.getValue().getProviderUserId()).isEqualTo("kakao-1");
        verify(expenseCategoryService).addDefaults(user);
    }

    @Test
    void registerNewUser_whenNameMissing_usesEmailLocalPart() {
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        User user = registrar.registerNewUser(
                new SocialUserInfo(SocialProvider.NAVER, "naver-1", "leeheamin@naver.com", true, null));

        assertThat(user.getName()).isEqualTo("leeheamin");
    }

    @ParameterizedTest
    @CsvSource(value = {
            "닉네임, a@b.com, 닉네임",
            "NULL, local@b.com, local",
            "'', local@b.com, local",
            "'   ', local@b.com, local",
            "NULL, @b.com, @b.com",
            "NULL, noat, noat"
    }, nullValues = "NULL")
    void resolveName_whenVariousInputs_returnsExpected(String name, String email, String expected) {
        assertThat(SocialAccountRegistrar.resolveName(
                new SocialUserInfo(SocialProvider.KAKAO, "id", email, true, name))).isEqualTo(expected);
    }

    @Test
    void link_whenNoConflict_savesAccountForUser() {
        User user = localUser(5L);
        when(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.GOOGLE, "google-sub"))
                .thenReturn(Optional.empty());
        when(socialAccountRepository.existsByUserIdAndProvider(5L, SocialProvider.GOOGLE)).thenReturn(false);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));

        User linked = registrar.link(PENDING);

        assertThat(linked).isSameAs(user);
        ArgumentCaptor<SocialAccount> captor = ArgumentCaptor.forClass(SocialAccount.class);
        verify(socialAccountRepository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isSameAs(user);
        assertThat(captor.getValue().getProvider()).isEqualTo(SocialProvider.GOOGLE);
        assertThat(captor.getValue().getProviderUserId()).isEqualTo("google-sub");
    }

    @Test
    void link_whenSocialAccountLinkedToAnotherUserMeanwhile_throwsConflict() {
        when(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.GOOGLE, "google-sub"))
                .thenReturn(Optional.of(SocialAccount.create(localUser(99L), SocialProvider.GOOGLE, "google-sub")));

        assertThatThrownBy(() -> registrar.link(PENDING))
                .isInstanceOf(BusinessException.class)
                .hasMessage(SocialAccountRegistrar.ALREADY_LINKED_MESSAGE)
                .extracting(e -> ((BusinessException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT);
        verify(socialAccountRepository, never()).save(any());
    }

    @Test
    void link_whenUserLinkedSameProviderMeanwhile_throwsConflict() {
        when(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.GOOGLE, "google-sub"))
                .thenReturn(Optional.empty());
        when(socialAccountRepository.existsByUserIdAndProvider(5L, SocialProvider.GOOGLE)).thenReturn(true);

        assertThatThrownBy(() -> registrar.link(PENDING))
                .isInstanceOf(BusinessException.class)
                .hasMessage(SocialAuthService.PROVIDER_ALREADY_LINKED_MESSAGE)
                .extracting(e -> ((BusinessException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT);
        verify(socialAccountRepository, never()).save(any());
    }

    @Test
    void link_whenUserDeletedMeanwhile_throwsUnauthorized() {
        when(socialAccountRepository.findByProviderAndProviderUserId(any(), any())).thenReturn(Optional.empty());
        when(socialAccountRepository.existsByUserIdAndProvider(5L, SocialProvider.GOOGLE)).thenReturn(false);
        when(userRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> registrar.link(PENDING))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(socialAccountRepository, never()).save(any());
    }
}
