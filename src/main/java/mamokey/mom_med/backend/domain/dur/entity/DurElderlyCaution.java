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
 * ref.dur_elderly_caution 테이블을 읽는 노인주의 Entity입니다.
 *
 * <p>65세 이상 부모에게 새 약을 추가할 때만 {@code ingredientNorm}으로 정확 조회합니다.
 * 병용금기와 달리 약쌍이 아니라 단일 성분 기준 WARN evidence를 만들기 위한 데이터입니다.</p>
 */
@Getter
@Entity
@Table(name = "dur_elderly_caution", schema = "ref")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DurElderlyCaution {

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

	@Column(name = "gazette_date")
	private LocalDate gazetteDate;

	@Column(name = "gazette_no", length = 100)
	private String gazetteNo;

	@Column(name = "detail")
	private String detail;

	@Column(name = "note")
	private String note;

	@Column(name = "reimbursement", length = 50)
	private String reimbursement;

	@Column(name = "created_at", insertable = false, updatable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", insertable = false, updatable = false)
	private OffsetDateTime updatedAt;

	public static DurElderlyCaution fixture(
			Long id,
			String ingredientNorm,
			String detail,
			String gazetteNo,
			LocalDate gazetteDate
	) {
		DurElderlyCaution row = new DurElderlyCaution();
		row.id = id;
		row.sourceRowHash = "fixture-" + id;
		row.ingredientName = ingredientNorm;
		row.ingredientNorm = ingredientNorm;
		row.detail = detail;
		row.gazetteNo = gazetteNo;
		row.gazetteDate = gazetteDate;
		return row;
	}
}
