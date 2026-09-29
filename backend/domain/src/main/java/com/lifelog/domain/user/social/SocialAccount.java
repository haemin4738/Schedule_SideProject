package com.lifelog.domain.user.social;

import com.lifelog.domain.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/** 회원에 연결된 소셜 로그인 계정. (provider, providerUserId) 가 신원 매칭 기준 */
@Entity
@Table(name = "social_accounts",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_social_accounts_provider_uid", columnNames = {"provider", "provider_user_id"}),
                @UniqueConstraint(name = "uk_social_accounts_user_provider", columnNames = {"user_id", "provider"})
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SocialAccount {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 20, nullable = false, updatable = false)
    private SocialProvider provider;

    @Column(name = "provider_user_id", nullable = false, updatable = false)
    private String providerUserId;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    private void prePersist() {
        createdAt = LocalDateTime.now();
    }

    public static SocialAccount create(User user, SocialProvider provider, String providerUserId) {
        SocialAccount account = new SocialAccount();
        account.user = user;
        account.provider = provider;
        account.providerUserId = providerUserId;
        return account;
    }
}
