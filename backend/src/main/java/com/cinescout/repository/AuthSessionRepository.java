package com.cinescout.repository;

import com.cinescout.domain.AuthSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {

    /** The session with its account, which every authenticated request needs. */
    @Query("select s from AuthSession s join fetch s.user where s.tokenHash = :hash")
    Optional<AuthSession> findWithUserByTokenHash(@Param("hash") String hash);

    @Modifying
    @Query("delete from AuthSession s where s.tokenHash = :hash")
    int deleteByTokenHash(@Param("hash") String hash);

    @Modifying
    @Query("delete from AuthSession s where s.expiresAt <= :now")
    int deleteExpired(@Param("now") Instant now);
}
