package com.xenosync.repository;

import com.xenosync.model.CommitVoteResponse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CommitVoteResponseRepository extends JpaRepository<CommitVoteResponse, UUID> {
        }