package com.xenosync.repository;

import com.xenosync.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email); //ded

    Optional<User> findByGithubId(String githubId);

    boolean existsByUsername(String username);

    boolean existsByUsernameIgnoreCase(String username);
}