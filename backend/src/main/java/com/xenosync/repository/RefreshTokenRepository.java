package com.xenosync.repository;

import com.xenosync.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

        List<RefreshToken> findByUserIdAndRevokedAtIsNull(UUID userId);
}