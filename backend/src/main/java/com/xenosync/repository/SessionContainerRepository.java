package com.xenosync.repository;

import com.xenosync.model.SessionContainer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SessionContainerRepository extends JpaRepository<SessionContainer, UUID> {
}