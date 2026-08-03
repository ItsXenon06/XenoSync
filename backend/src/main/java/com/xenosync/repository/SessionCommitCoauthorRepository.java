package com.xenosync.repository;

import com.xenosync.model.SessionCommitCoauthor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SessionCommitCoauthorRepository extends JpaRepository<SessionCommitCoauthor, UUID> {
        }