package com.lifelog.domain.user;

import com.lifelog.domain.user.social.SocialProvider;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    /** 소셜 로그인으로만 가입한 회원은 null */
    private String password;

    @Column(nullable = false)
    private String name;

    /** 가입 경로. 가입 후 불변 — 현재 연결된 소셜 계정 목록은 social_accounts 가 기준 */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @ColumnDefault("'LOCAL'")
    @Column(length = 20, nullable = false, updatable = false)
    private SignupProvider signupProvider;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    private void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    private void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public static User create(String email, String encodedPassword, String name) {
        User user = new User();
        user.email = email;
        user.password = encodedPassword;
        user.name = name;
        user.signupProvider = SignupProvider.LOCAL;
        return user;
    }

    public static User createSocial(String email, String name, SocialProvider provider) {
        User user = new User();
        user.email = email;
        user.name = name;
        user.signupProvider = SignupProvider.from(provider);
        return user;
    }

    public boolean hasPassword() {
        return password != null;
    }
}
