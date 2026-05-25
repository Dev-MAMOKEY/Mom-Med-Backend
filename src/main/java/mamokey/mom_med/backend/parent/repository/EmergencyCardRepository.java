package mamokey.mom_med.backend.parent.repository;

import mamokey.mom_med.backend.parent.domain.EmergencyCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface EmergencyCardRepository extends JpaRepository<EmergencyCard, UUID> {

    Optional<EmergencyCard> findByQrTokenAndRevokedAtIsNull(String qrToken);

    Optional<EmergencyCard> findByParentId(UUID parentId);

    /** access_count 원자적 증가 + last_accessed_at 갱신 */
    @Modifying
    @Query("""
            UPDATE EmergencyCard ec
               SET ec.accessCount    = ec.accessCount + 1,
                   ec.lastAccessedAt = CURRENT_TIMESTAMP
             WHERE ec.qrToken = :token
            """)
    void incrementAccessCount(@Param("token") String token);
}
