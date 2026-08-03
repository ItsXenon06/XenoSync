package com.xenosync.repository;

import com.xenosync.model.SessionLinkedRepo;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface SessionLinkedRepoRepository extends JpaRepository<SessionLinkedRepo, UUID> {
}