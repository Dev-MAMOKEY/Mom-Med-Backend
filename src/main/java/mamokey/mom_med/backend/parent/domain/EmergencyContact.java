package mamokey.mom_med.backend.parent.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 부모 비상연락처 엔티티 (Slice 08).
 *
 * <p>app.emergency_contacts 테이블과 매핑됩니다.
 * 응급카드 snapshot에 포함되어 QR 스캔 시 노출됩니다.</p>
 */
@Entity
@Table(name = "emergency_contacts", schema = "app")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmergencyContact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parent_id", nullable = false, updatable = false)
    private UUID parentId;

    /** 이름 */
    @Column(nullable = false, length = 100)
    private String name;

    /** 관계 (배우자, 자녀, 형제 등) */
    @Column(nullable = false, length = 50)
    private String relationship;

    /** 연락처 */
    @Column(nullable = false, length = 50)
    private String phone;

    /** 표시 우선순위 — 낮을수록 먼저 표시 */
    @Column(nullable = false)
    private int priority = 0;

    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "TIMESTAMPTZ DEFAULT NOW()")
    private LocalDateTime createdAt = LocalDateTime.now();

    // ─── 생성 ─────────────────────────────────────────────────────────────

    public static EmergencyContact create(
            UUID parentId,
            String name,
            String relationship,
            String phone,
            int priority
    ) {
        EmergencyContact c = new EmergencyContact();
        c.parentId = parentId;
        c.name = name;
        c.relationship = relationship;
        c.phone = phone;
        c.priority = priority;
        return c;
    }

    // ─── 수정 ─────────────────────────────────────────────────────────────

    public void update(String name, String relationship, String phone, int priority) {
        this.name = name;
        this.relationship = relationship;
        this.phone = phone;
        this.priority = priority;
    }
}
