package com.xenosync.repository;

import com.xenosync.model.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {

        Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

        List<EmailVerificationToken> findByUserIdAndUsedAtIsNull(UUID userId);
}