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
 * ref.dur_age_contraindication 테이블 Entity (Slice 05).
 *
 * <p>HIRA DUR 연령금기 CSV를 적재한 참조 테이블입니다.
 * 슬라이스 02에서 스키마를 생성하고 슬라이스 05에서 CSV를 적재해 판정합니다.
 * age_limit는 "특정연령+특정연령단위+연령처리조건"을 결합한 문자열 (예: "12세미만")입니다.</p>
 */
@Getter
@Entity
@Table(name = "dur_age_contraindication", schema = "ref")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DurAgeContraindication {

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

	/** 연령 제한 문자열 (예: "12세미만", "65세이상"). CSV의 특정연령+특정연령단위+연령처리조건 결합. */
	@Column(name = "age_limit", length = 100)
	private String ageLimit;

	@Column(name = "gazette_no", length = 100)
	private String gazetteNo;

	@Column(name = "gazette_date")
	private LocalDate gazetteDate;

	@Column(name = "detail")
	private String detail;

	@Column(name = "created_at", insertable = false, updatable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", insertable = false, updatable = false)
	private OffsetDateTime updatedAt;
}
