package com.lifelog.infrastructure.user;

import com.lifelog.domain.user.social.SocialAccount;
import com.lifelog.domain.user.social.SocialAccountRepository;
import com.lifelog.domain.user.social.SocialProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class SocialAccountRepositoryImpl implements SocialAccountRepository {

    private final SocialAccountJpaRepository jpa;

    @Override
    public SocialAccount save(SocialAccount account) {
        return jpa.save(account);
    }

    @Override
    public Optional<SocialAccount> findByProviderAndProviderUserId(SocialProvider provider, String providerUserId) {
        return jpa.findWithUserByProviderAndProviderUserId(provider, providerUserId);
    }

    @Override
    public boolean existsByUserIdAndProvider(Long userId, SocialProvider provider) {
        return jpa.existsByUserIdAndProvider(userId, provider);
    }
}
