package mamokey.mom_med.backend.parent.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 부모 약장(복용 중인 약) 엔티티.
 *
 * <p>app.patient_medications 테이블과 매핑됩니다.
 * deleted_at IS NULL인 레코드만 현재 복용 중인 약입니다 (soft delete 패턴).
 * ingredient_norm은 DrugMaster.mainIngrNorm에서 복사된 캐시로,
 * SafetyJudgeService의 DUR 병용금기 검사 시 사용됩니다.</p>
 */
@Entity
@Table(name = "patient_medications", schema = "app")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PatientMedication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parent_id", nullable = false, updatable = false)
    private UUID parentId;

    /** 식약처 품목기준코드 */
    @Column(name = "item_seq", nullable = false, updatable = false, length = 20)
    private String itemSeq;

    /** 약품명 (DrugMaster에서 복사, 캐싱) */
    @Column(name = "drug_name", nullable = false, length = 300)
    private String drugName;

    /** 정규화된 성분명 (DrugMaster.mainIngrNorm 복사) — DUR 병용금기 검사용 */
    @Column(name = "ingredient_norm", length = 200)
    private String ingredientNorm;

    @Column(name = "started_on")
    private LocalDate startedOn;

    @Column(columnDefinition = "TEXT")
    private String memo;

    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "TIMESTAMPTZ DEFAULT NOW()")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // ─── 생성 ─────────────────────────────────────────────────────────────

    public static PatientMedication create(
            UUID parentId,
            String itemSeq,
            String drugName,
            String ingredientNorm,
            LocalDate startedOn,
            String memo
    ) {
        PatientMedication m = new PatientMedication();
        m.parentId = parentId;
        m.itemSeq = itemSeq;
        m.drugName = drugName;
        m.ingredientNorm = ingredientNorm;
        m.startedOn = startedOn;
        m.memo = memo;
        return m;
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
