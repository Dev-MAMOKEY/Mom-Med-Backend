package mamokey.mom_med.backend.domain.dur.entity;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * ref.dur_elderly_nsaid_caution 테이블을 읽는 NSAID 노인주의 Entity입니다.
 *
 * <p>HIRA가 일반 노인주의와 NSAID 노인주의를 별도 CSV로 제공하므로 테이블도 분리합니다.
 * SafetyJudge 입장에서는 둘 다 65세 이상 WARN evidence로 통합됩니다.</p>
 */
@Getter
@Entity
@Table(name = "dur_elderly_nsaid_caution", schema = "ref")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DurElderlyNsaidCaution {

	@Id
	@Column(name = "id")
	private Long id;

	@Column(name = "source_row_hash", nullable = false, length = 64)
	private String sourceRowHash;

	@Column(name = "ingredient_name", nullable = false, length = 500)
	private String ingredientName;

	@Column(name = "ingredient_norm", nullable = false, length = 200)
	private String ingredientNorm;

	@Column(name = "ingredient_code", length = 50)
	private String ingredientCode;

	@Column(name = "product_code", length = 50)
	private String productCode;

	@Column(name = "product_name", length = 500)
	private String productName;

	@Column(name = "company_name", length = 200)
	private String companyName;

	@Column(name = "detail")
	private String detail;

	@Column(name = "reimbursement", length = 50)
	private String reimbursement;

	@Column(name = "created_at", insertable = false, updatable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", insertable = false, updatable = false)
	private OffsetDateTime updatedAt;

	public static DurElderlyNsaidCaution fixture(
			Long id,
			String ingredientNorm,
			String detail
	) {
		DurElderlyNsaidCaution row = new DurElderlyNsaidCaution();
		row.id = id;
		row.sourceRowHash = "fixture-" + id;
		row.ingredientName = ingredientNorm;
		row.ingredientNorm = ingredientNorm;
		row.detail = detail;
		return row;
	}
}
