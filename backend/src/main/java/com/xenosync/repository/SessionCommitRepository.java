package com.xenosync.repository;

import com.xenosync.model.SessionCommit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SessionCommitRepository extends JpaRepository<SessionCommit, UUID> {
        }