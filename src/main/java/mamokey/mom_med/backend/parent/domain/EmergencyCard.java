package mamokey.mom_med.backend.parent.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 응급카드 엔티티 (Slice 08).
 *
 * <p>app.emergency_cards 테이블과 매핑됩니다.
 * 부모 1명당 1개 카드 (parent_id PK).
 * snapshot은 생성 시점의 의료정보 전체를 JSONB로 저장 — QR 스캔 시 즉시 반환.
 * qr_token은 외부 접근용 URL-safe 64자 토큰이며 revoke 전까지 유지됩니다.</p>
 *
 * <p>약장·알레르기·기저질환 변경 시 snapshot_at이 갱신되고 qr_token은 유지됩니다.
 * 자녀가 명시적으로 재발급 요청해야만 qr_token이 교체됩니다.</p>
 */
@Entity
@Table(name = "emergency_cards", schema = "app")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmergencyCard {

    @Id
    @Column(name = "parent_id", updatable = false, nullable = false)
    private UUID parentId;

    /** URL-safe Base64 토큰 (48 bytes → 64자) — GET /em/{token} 접근 키 */
    @Column(name = "qr_token", nullable = false, unique = true, length = 64)
    private String qrToken;

    /** 의료정보 전체 snapshot (JSONB) */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot", columnDefinition = "jsonb", nullable = false)
    private String snapshot;

    @Column(name = "snapshot_at", nullable = false)
    private LocalDateTime snapshotAt;

    /** 토큰 유효 기간 (발급/재발급으로부터 90일) */
    @Column(name = "valid_until", nullable = false)
    private LocalDateTime validUntil;

    /** 누적 외부 접근 횟수 (원자적으로 증가) */
    @Column(name = "access_count", nullable = false)
    private int accessCount = 0;

    @Column(name = "last_accessed_at")
    private LocalDateTime lastAccessedAt;

    /** revoke 시각 — NOT NULL이면 무효 토큰 */
    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    // ─── 생성 ─────────────────────────────────────────────────────────────

    public static EmergencyCard create(UUID parentId, String qrToken, String snapshot) {
        EmergencyCard card = new EmergencyCard();
        card.parentId = parentId;
        card.qrToken = qrToken;
        card.snapshot = snapshot;
        card.snapshotAt = LocalDateTime.now();
        card.validUntil = LocalDateTime.now().plusDays(90);
        return card;
    }

    // ─── 수정 ─────────────────────────────────────────────────────────────

    /** 약장·알레르기·기저질환 변경 시 snapshot만 갱신 (qr_token 유지) */
    public void refreshSnapshot(String snapshot) {
        this.snapshot = snapshot;
        this.snapshotAt = LocalDateTime.now();
    }

    /** 재발급: 새 qr_token + snapshot + 90일 유효기간 초기화 */
    public void regenerate(String newQrToken, String snapshot) {
        this.qrToken = newQrToken;
        this.snapshot = snapshot;
        this.snapshotAt = LocalDateTime.now();
        this.validUntil = LocalDateTime.now().plusDays(90);
        this.revokedAt = null;
        this.accessCount = 0;
    }

    /** 외부 접근 시 access_count는 DB에서 원자적으로 증가 — 이 메서드는 in-memory 반영용 */
    public void recordAccess() {
        this.accessCount++;
        this.lastAccessedAt = LocalDateTime.now();
    }

    /** 즉시 무효화 */
    public void revoke() {
        this.revokedAt = LocalDateTime.now();
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(validUntil);
    }
}
