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
 * ref.dur_pregnancy_contraindication 테이블 Entity (Slice 05).
 *
 * <p>HIRA DUR 임부금기 CSV를 적재한 참조 테이블입니다.
 * 슬라이스 02에서 스키마를 생성하고 슬라이스 05에서 CSV를 적재해 판정합니다.
 * is_pregnant=true인 부모에게만 이 테이블을 조회합니다.</p>
 */
@Getter
@Entity
@Table(name = "dur_pregnancy_contraindication", schema = "ref")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DurPregnancyContraindication {

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

	/** 임부금기 등급 (예: "1등급", "2등급"). */
	@Column(name = "pregnancy_grade", length = 100)
	private String pregnancyGrade;

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
