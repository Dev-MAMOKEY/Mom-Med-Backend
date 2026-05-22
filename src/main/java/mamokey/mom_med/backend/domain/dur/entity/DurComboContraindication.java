package mamokey.mom_med.backend.domain.dur.entity;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * ref.dur_combo_contraindications 테이블을 읽는 병용금기 Entity입니다.
 *
 * <p>Slice 02의 핵심 조회 대상이며, DURRuleEngine은 {@code ingredientNormA/B}만 사용해
 * 인덱스 기반 {@code =} 정확 매칭을 수행합니다. 원본 성분명과 상세정보는 사용자에게
 * evidence를 설명할 때 사용합니다.</p>
 */
@Getter
@Entity
@Table(name = "dur_combo_contraindications", schema = "ref")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DurComboContraindication {

	@Id
	@Column(name = "id")
	private Long id;

	@Column(name = "source_row_hash", nullable = false, length = 64)
	private String sourceRowHash;

	@Column(name = "ingredient_name_a", nullable = false, length = 500)
	private String ingredientNameA;

	@Column(name = "ingredient_norm_a", nullable = false, length = 200)
	private String ingredientNormA;

	@Column(name = "ingredient_code_a", length = 50)
	private String ingredientCodeA;

	@Column(name = "product_code_a", length = 50)
	private String productCodeA;

	@Column(name = "product_name_a", length = 500)
	private String productNameA;

	@Column(name = "company_name_a", length = 200)
	private String companyNameA;

	@Column(name = "reimbursement_a", length = 50)
	private String reimbursementA;

	@Column(name = "ingredient_name_b", nullable = false, length = 500)
	private String ingredientNameB;

	@Column(name = "ingredient_norm_b", nullable = false, length = 200)
	private String ingredientNormB;

	@Column(name = "ingredient_code_b", length = 50)
	private String ingredientCodeB;

	@Column(name = "product_code_b", length = 50)
	private String productCodeB;

	@Column(name = "product_name_b", length = 500)
	private String productNameB;

	@Column(name = "company_name_b", length = 200)
	private String companyNameB;

	@Column(name = "reimbursement_b", length = 50)
	private String reimbursementB;

	@Column(name = "gazette_no", length = 100)
	private String gazetteNo;

	@Column(name = "gazette_date")
	private LocalDate gazetteDate;

	@Column(name = "detail")
	private String detail;

	@Column(name = "note")
	private String note;

	@Column(name = "created_at", insertable = false, updatable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", insertable = false, updatable = false)
	private OffsetDateTime updatedAt;

	public static DurComboContraindication fixture(
			Long id,
			String ingredientNormA,
			String ingredientNormB,
			String detail,
			String gazetteNo,
			LocalDate gazetteDate
	) {
		DurComboContraindication row = new DurComboContraindication();
		row.id = id;
		row.sourceRowHash = "fixture-" + id;
		row.ingredientNameA = ingredientNormA;
		row.ingredientNormA = ingredientNormA;
		row.ingredientNameB = ingredientNormB;
		row.ingredientNormB = ingredientNormB;
		row.detail = detail;
		row.gazetteNo = gazetteNo;
		row.gazetteDate = gazetteDate;
		return row;
	}
}
