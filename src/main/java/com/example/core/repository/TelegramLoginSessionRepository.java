package com.example.core.repository;

import com.example.core.model.TelegramLoginSession;
import com.example.core.model.TelegramLoginSessionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;

@Repository
public interface TelegramLoginSessionRepository extends JpaRepository<TelegramLoginSession, String> {

    Optional<TelegramLoginSession> findBySessionId(String sessionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from TelegramLoginSession s where s.sessionId = :sessionId")
    Optional<TelegramLoginSession> findBySessionIdForUpdate(@Param("sessionId") String sessionId);

    int deleteByExpiresAtBefore(OffsetDateTime threshold);

    long countByStatus(TelegramLoginSessionStatus status);
}
