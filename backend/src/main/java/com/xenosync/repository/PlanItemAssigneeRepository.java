package com.xenosync.repository;

import com.xenosync.model.PlanItemAssignee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PlanItemAssigneeRepository extends JpaRepository<PlanItemAssignee, UUID> {
        }