package mamokey.mom_med.backend.parent.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 부모 기저질환 엔티티 (Slice 05).
 *
 * <p>app.patient_conditions 테이블과 매핑됩니다.
 * deleted_at IS NULL인 레코드만 현재 활성 기저질환입니다 (soft delete 패턴).
 * condition_norm은 정규화된 질환명으로, 향후 약물-질환 상호작용 검사에 사용됩니다.
 * kcd_code는 HIRA KCD(한국표준질병사인분류) 코드로, HIRA API로 조회해 저장합니다.</p>
 */
@Entity
@Table(name = "patient_conditions", schema = "app")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PatientCondition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parent_id", nullable = false, updatable = false)
    private UUID parentId;

    /** 질환명 (예: "고혈압", "2형 당뇨병") */
    @Column(name = "condition_name", nullable = false, length = 200)
    private String conditionName;

    /** 정규화된 질환명 — 약물-질환 상호작용 검사용 */
    @Column(name = "condition_norm", length = 200)
    private String conditionNorm;

    /** HIRA KCD 코드 (예: "I10", "E11") */
    @Column(name = "kcd_code", length = 20)
    private String kcdCode;

    /**
     * 중증도: severe | moderate | mild | unknown
     */
    @Column(length = 20)
    private String severity;

    @Column(name = "diagnosed_at")
    private LocalDate diagnosedAt;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "TIMESTAMPTZ DEFAULT NOW()")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // ─── 생성 ─────────────────────────────────────────────────────────────

    public static PatientCondition create(
            UUID parentId,
            String conditionName,
            String conditionNorm,
            String kcdCode,
            String severity,
            LocalDate diagnosedAt,
            String notes
    ) {
        PatientCondition c = new PatientCondition();
        c.parentId = parentId;
        c.conditionName = conditionName;
        c.conditionNorm = conditionNorm;
        c.kcdCode = kcdCode;
        c.severity = severity;
        c.diagnosedAt = diagnosedAt;
        c.notes = notes;
        return c;
    }

    // ─── 수정 ─────────────────────────────────────────────────────────────

    /** Soft delete: deleted_at = NOW() */
    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
