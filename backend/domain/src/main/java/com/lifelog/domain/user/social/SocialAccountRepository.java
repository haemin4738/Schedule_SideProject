package com.lifelog.domain.user.social;

import java.util.Optional;

public interface SocialAccountRepository {
    SocialAccount save(SocialAccount account);
    /** user 를 fetch join 으로 함께 조회 */
    Optional<SocialAccount> findByProviderAndProviderUserId(SocialProvider provider, String providerUserId);
    boolean existsByUserIdAndProvider(Long userId, SocialProvider provider);
}
