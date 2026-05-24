package mamokey.mom_med.backend.parent.repository;

import mamokey.mom_med.backend.parent.domain.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * app.device_tokens JPA 리포지토리.
 *
 * <p>INSERT는 pgp_sym_encrypt를 사용하는 native query이므로 서비스 레이어에서
 * {@code NamedParameterJdbcTemplate}으로 직접 처리합니다.</p>
 */
public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {

    /** 부모의 활성 토큰 목록 */
    List<DeviceToken> findByParentIdAndRevokedAtIsNull(UUID parentId);

    /** 특정 토큰 조회 (부모 확인 포함) */
    Optional<DeviceToken> findByIdAndParentId(Long id, UUID parentId);

    /**
     * 암호화 INSERT — pgp_sym_encrypt를 사용하여 plain token이 DB에 노출되지 않도록 합니다.
     * 생성된 id를 RETURNING으로 반환합니다.
     *
     * <p>주의: @Modifying + INSERT + RETURNING은 일부 JPA 구현에서 지원 범위가 다를 수 있으므로
     * 서비스에서 NamedParameterJdbcTemplate을 사용하는 방식을 권장합니다.
     * 이 메서드는 참고용으로 남겨둡니다.</p>
     */
    @Modifying
    @Query(value = """
            INSERT INTO app.device_tokens (parent_id, platform, token_encrypted, created_at)
            VALUES (:parentId, :platform, pgp_sym_encrypt(:token, :encKey), NOW())
            """, nativeQuery = true)
    void insertEncrypted(
            @Param("parentId") UUID parentId,
            @Param("platform") String platform,
            @Param("token") String token,
            @Param("encKey") String encKey
    );
}
