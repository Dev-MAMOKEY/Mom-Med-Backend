package mamokey.mom_med.backend.parent.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 자녀 디바이스 토큰 엔티티.
 *
 * <p>app.device_tokens 테이블과 매핑됩니다.
 * token_encrypted는 DB에 pgp_sym_encrypt로 암호화된 BYTEA입니다.
 * INSERT 시 암호화: {@code DeviceTokenRepository.insertEncrypted()} native query 사용.
 * 응답에는 plain token을 절대 포함하지 않습니다.</p>
 *
 * <p>revoked_at IS NULL인 레코드만 활성 토큰입니다.</p>
 */
@Entity
@Table(name = "device_tokens", schema = "app")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeviceToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parent_id", nullable = false, updatable = false)
    private UUID parentId;

    /** 향후 자녀 계정 PRD에서 활성화 */
    @Column(name = "child_user_id")
    private UUID childUserId;

    /**
     * 플랫폼: fcm | apns | web
     */
    @Column(nullable = false, length = 10)
    private String platform;

    /**
     * pgp_sym_encrypt로 암호화된 토큰 바이트.
     * 이 필드를 API 응답에 포함하지 마세요.
     */
    @Column(name = "token_encrypted", nullable = false)
    private byte[] tokenEncrypted;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "TIMESTAMPTZ DEFAULT NOW()")
    private LocalDateTime createdAt = LocalDateTime.now();

    // ─── 수정 ─────────────────────────────────────────────────────────────

    /** 토큰 취소: revoked_at = NOW() */
    public void revoke() {
        this.revokedAt = LocalDateTime.now();
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    /** 마지막 사용 시각 갱신 */
    public void updateLastUsed() {
        this.lastUsedAt = LocalDateTime.now();
    }
}
