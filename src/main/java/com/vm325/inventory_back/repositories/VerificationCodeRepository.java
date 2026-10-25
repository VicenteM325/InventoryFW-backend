package com.vm325.inventory_back.repositories;

import com.vm325.inventory_back.entities.User;
import com.vm325.inventory_back.entities.VerificationCode;
import com.vm325.inventory_back.enums.VerificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VerificationCodeRepository extends JpaRepository<VerificationCode, UUID> {

    Optional<VerificationCode> findFirstByUserAndTypeAndConsumedFalseOrderByCreatedAtDesc(User user, VerificationType type);

    Optional<VerificationCode> findFirstByCodeHashAndTypeAndConsumedFalse(String codeHash, VerificationType type);

    Optional<VerificationCode> findFirstByUserAndTypeOrderByCreatedAtDesc(User user, VerificationType type);

    /** Invalida los códigos vigentes de un usuario para un tipo (al emitir uno nuevo o al cambiar la contraseña). */
    @Modifying
    @Query("update VerificationCode v set v.consumed = true where v.user = :user and v.type = :type and v.consumed = false")
    int consumeActive(User user, VerificationType type);

    /** Limpieza de códigos ya vencidos. */
    @Modifying
    @Query("delete from VerificationCode v where v.expiresAt < :limit")
    int deleteExpiredBefore(Instant limit);
}
