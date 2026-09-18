package com.lifelog.infrastructure.user;

import com.lifelog.domain.user.User;
import com.lifelog.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

    private final UserJpaRepository jpa;

    @Override public User save(User user) { return jpa.save(user); }
    @Override public Optional<User> findById(Long id) { return jpa.findById(id); }
    @Override public Optional<User> findByEmail(String email) { return jpa.findByEmail(email); }
    @Override public boolean existsByEmail(String email) { return jpa.existsByEmail(email); }
}
