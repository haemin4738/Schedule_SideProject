package com.lifelog.infrastructure.user;

import com.lifelog.domain.user.SignupProvider;
import com.lifelog.domain.user.User;
import com.lifelog.domain.user.social.SocialAccount;
import com.lifelog.domain.user.social.SocialAccountRepository;
import com.lifelog.domain.user.social.SocialProvider;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SocialAccountRepositoryImpl 통합 테스트 — 실제 MySQL(lifelog_test) 사용.
 * users.password nullable / signup_provider 저장도 함께 확인한다.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EntityScan(basePackages = "com.lifelog.domain")
@EnableJpaRepositories(basePackages = "com.lifelog.infrastructure")
@Import(SocialAccountRepositoryImplTest.TestConfig.class)
class SocialAccountRepositoryImplTest {

    @TestConfiguration
    @ComponentScan(basePackages = "com.lifelog.infrastructure.user",
            excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                    classes = {RedisPendingSocialLinkStore.class, RedisRefreshSessionStore.class,
                            RedisAppSocialLoginStore.class}))
    static class TestConfig {
    }

    @Autowired
    private SocialAccountRepository socialAccountRepository;

    @Autowired
    private EntityManager entityManager;

    private User user1;
    private User user2;
    private String suffix;

    @BeforeEach
    void setUp() {
        suffix = String.valueOf(System.nanoTime());
        user1 = User.create("sa1-" + suffix + "@test.com", "encoded-pw", "유저1");
        user2 = User.createSocial("sa2-" + suffix + "@test.com", "유저2", SocialProvider.KAKAO);
        entityManager.persist(user1);
        entityManager.persist(user2);
        entityManager.flush();
    }

    private SocialAccount save(User user, SocialProvider provider, String providerUserId) {
        return socialAccountRepository.save(SocialAccount.create(user, provider, providerUserId));
    }

    @Test
    void save_whenValid_persistsWithIdAndCreatedAt() {
        SocialAccount saved = save(user1, SocialProvider.KAKAO, "kakao-" + suffix);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void findByProviderAndProviderUserId_whenExists_returnsAccountWithUserFetched() {
        save(user1, SocialProvider.GOOGLE, "google-" + suffix);
        entityManager.flush();
        entityManager.clear();

        Optional<SocialAccount> found =
                socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.GOOGLE, "google-" + suffix);

        assertThat(found).isPresent();
        // fetch join — 영속성 컨텍스트를 비운 뒤에도 user 가 프록시가 아닌 초기화된 상태
        assertThat(Hibernate.isInitialized(found.get().getUser())).isTrue();
        assertThat(found.get().getUser().getId()).isEqualTo(user1.getId());
        assertThat(found.get().getUser().getEmail()).isEqualTo(user1.getEmail());
    }

    @Test
    void findByProviderAndProviderUserId_whenProviderDiffers_returnsEmpty() {
        save(user1, SocialProvider.KAKAO, "same-id-" + suffix);
        entityManager.flush();

        assertThat(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.NAVER, "same-id-" + suffix))
                .isEmpty();
        assertThat(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.KAKAO, "other-" + suffix))
                .isEmpty();
    }

    @Test
    void save_whenSameProviderAndProviderUserIdForOtherUser_throwsDataIntegrityViolation() {
        save(user1, SocialProvider.KAKAO, "dup-" + suffix);

        assertThatThrownBy(() -> {
            save(user2, SocialProvider.KAKAO, "dup-" + suffix);
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void save_whenSameUserAndProviderWithOtherProviderUserId_throwsDataIntegrityViolation() {
        save(user1, SocialProvider.NAVER, "naver-a-" + suffix);

        assertThatThrownBy(() -> {
            save(user1, SocialProvider.NAVER, "naver-b-" + suffix);
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void save_whenSameProviderUserIdOnDifferentProviders_succeeds() {
        save(user1, SocialProvider.KAKAO, "shared-" + suffix);
        save(user1, SocialProvider.NAVER, "shared-" + suffix);
        save(user1, SocialProvider.GOOGLE, "shared-" + suffix);
        entityManager.flush();

        assertThat(socialAccountRepository.existsByUserIdAndProvider(user1.getId(), SocialProvider.GOOGLE)).isTrue();
    }

    @Test
    void existsByUserIdAndProvider_whenLinkedOrNot_returnsExpected() {
        save(user1, SocialProvider.KAKAO, "kakao-" + suffix);
        entityManager.flush();

        assertThat(socialAccountRepository.existsByUserIdAndProvider(user1.getId(), SocialProvider.KAKAO)).isTrue();
        assertThat(socialAccountRepository.existsByUserIdAndProvider(user1.getId(), SocialProvider.NAVER)).isFalse();
        assertThat(socialAccountRepository.existsByUserIdAndProvider(user2.getId(), SocialProvider.KAKAO)).isFalse();
    }

    @Test
    void user_whenSocialOnly_persistsNullPasswordAndSignupProvider() {
        entityManager.clear();

        Object[] row = (Object[]) entityManager.createNativeQuery(
                        "select password, signup_provider from users where id = :id")
                .setParameter("id", user2.getId())
                .getSingleResult();
        assertThat(row[0]).isNull();
        assertThat(row[1]).isEqualTo("KAKAO");

        User reloaded = entityManager.find(User.class, user2.getId());
        assertThat(reloaded.hasPassword()).isFalse();
        assertThat(reloaded.getSignupProvider()).isEqualTo(SignupProvider.KAKAO);
    }

    @Test
    void user_whenLocal_persistsLocalSignupProvider() {
        entityManager.clear();

        User reloaded = entityManager.find(User.class, user1.getId());
        assertThat(reloaded.hasPassword()).isTrue();
        assertThat(reloaded.getSignupProvider()).isEqualTo(SignupProvider.LOCAL);
    }

    @Test
    void usersTable_whenSignupProviderOmittedInInsert_defaultsToLocal() {
        // 기존 행 호환: @ColumnDefault("'LOCAL'") 이 DDL DEFAULT 로 생성됐는지
        String email = "legacy-" + suffix + "@test.com";
        entityManager.createNativeQuery("insert into users (email, password, name) values (:email, 'pw', 'legacy')")
                .setParameter("email", email)
                .executeUpdate();

        Object provider = entityManager.createNativeQuery("select signup_provider from users where email = :email")
                .setParameter("email", email)
                .getSingleResult();
        assertThat(provider).isEqualTo("LOCAL");
    }
}
