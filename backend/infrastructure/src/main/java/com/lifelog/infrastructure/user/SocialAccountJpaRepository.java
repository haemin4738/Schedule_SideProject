package com.lifelog.infrastructure.user;

import com.lifelog.domain.user.social.SocialAccount;
import com.lifelog.domain.user.social.SocialProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

interface SocialAccountJpaRepository extends JpaRepository<SocialAccount, Long> {

    // 로그인 판정 직후 User 로 토큰을 발급하므로 user 를 함께 조회한다 (N+1 방지)
    @Query("select sa from SocialAccount sa join fetch sa.user " +
            "where sa.provider = :provider and sa.providerUserId = :providerUserId")
    Optional<SocialAccount> findWithUserByProviderAndProviderUserId(@Param("provider") SocialProvider provider,
                                                                    @Param("providerUserId") String providerUserId);

    boolean existsByUserIdAndProvider(Long userId, SocialProvider provider);
}
