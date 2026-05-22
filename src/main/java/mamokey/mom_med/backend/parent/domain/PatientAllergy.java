package mamokey.mom_med.backend.parent.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 부모의 알레르기 정보 엔티티.
 *
 * <p>app.patient_allergies 테이블과 매핑됩니다.
 * deleted_at IS NULL인 레코드만 현재 활성 알레르기입니다 (soft delete 패턴).
 * created_at은 DB DEFAULT(NOW())로 관리하며 JPA Auditing은 사용하지 않습니다
 * (BaseTimeEntity를 상속하지 않음 — updated_at 컬럼이 없기 때문).</p>
 */
@Entity
@Table(name = "patient_allergies", schema = "app")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PatientAllergy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parent_id", nullable = false, updatable = false)
    private UUID parentId;

    /**
     * 알레르기 유형: drug | food | env | other
     */
    @Column(name = "allergen_type", nullable = false, length = 20)
    private String allergenType;

    /** 원본 텍스트 (예: "페니실린", "땅콩") */
    @Column(name = "allergen_name", nullable = false, length = 200)
    private String allergenName;

    /** 정규화된 약물명 — drug 타입일 때 DrugNameNormalizer가 설정 */
    @Column(name = "allergen_norm", length = 200)
    private String allergenNorm;

    /**
     * 중증도: severe | moderate | mild | unknown
     */
    @Column(length = 20)
    private String severity;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "confirmed_at")
    private LocalDate confirmedAt;

    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "TIMESTAMPTZ DEFAULT NOW()")
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // ─── 생성 ─────────────────────────────────────────────────────────────

    public static PatientAllergy create(
            UUID parentId,
            String allergenType,
            String allergenName,
            String allergenNorm,
            String severity,
            String notes,
            LocalDate confirmedAt
    ) {
        PatientAllergy a = new PatientAllergy();
        a.parentId = parentId;
        a.allergenType = allergenType;
        a.allergenName = allergenName;
        a.allergenNorm = allergenNorm;
        a.severity = severity;
        a.notes = notes;
        a.confirmedAt = confirmedAt;
        return a;
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
