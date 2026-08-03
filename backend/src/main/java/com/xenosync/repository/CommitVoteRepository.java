package com.xenosync.repository;

import com.xenosync.model.CommitVote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CommitVoteRepository extends JpaRepository<CommitVote, UUID> {
        }