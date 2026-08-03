package com.xenosync.repository;

import com.xenosync.model.PlanItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PlanItemRepository extends JpaRepository<PlanItem, UUID> {
        }