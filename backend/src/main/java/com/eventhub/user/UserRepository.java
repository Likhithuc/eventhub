package com.eventhub.user;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<User> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    long countByStatus(UserStatus status);

    @org.springframework.data.jpa.repository.Query("select count(distinct u) from User u join u.roles r where r = :role")
    long countWithRole(@org.springframework.data.repository.query.Param("role") Role role);
}
