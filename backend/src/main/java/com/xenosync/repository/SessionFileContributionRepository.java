package com.xenosync.repository;

import com.xenosync.model.SessionFileContribution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SessionFileContributionRepository extends JpaRepository<SessionFileContribution, UUID> {
}