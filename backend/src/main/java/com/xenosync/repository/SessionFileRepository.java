package com.xenosync.repository;

import com.xenosync.model.SessionFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SessionFileRepository extends JpaRepository<SessionFile, UUID> {
        }