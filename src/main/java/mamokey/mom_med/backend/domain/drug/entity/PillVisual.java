package mamokey.mom_med.backend.domain.drug.entity;

import java.math.BigDecimal;
import java.time.Clock;
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
 * 식약처 낱알식별 API에서 가져온 알약 사진과 외형 정보를 저장하는 Entity입니다.
 *
 * <p>약 이름만으로는 사용자가 실물 약을 확인하기 어렵기 때문에 이미지, 각인, 색상,
 * 크기 정보를 약 마스터와 1:1로 연결해 제공합니다.</p>
 */
@Getter
@Entity
@Table(name = "pill_visuals", schema = "ref")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PillVisual {

	@Id
	@Column(name = "item_seq", length = 20)
	private String itemSeq;

	@Column(name = "image_url", nullable = false)
	private String imageUrl;

	@Column(name = "drug_shape", length = 50)
	private String drugShape;

	@Column(name = "color_primary", length = 50)
	private String colorPrimary;

	@Column(name = "color_secondary", length = 50)
	private String colorSecondary;

	@Column(name = "print_front", length = 100)
	private String printFront;

	@Column(name = "print_back", length = 100)
	private String printBack;

	@Column(name = "line_front", length = 50)
	private String lineFront;

	@Column(name = "line_back", length = 50)
	private String lineBack;

	@Column(name = "length_long_mm", precision = 5, scale = 2)
	private BigDecimal lengthLongMm;

	@Column(name = "length_short_mm", precision = 5, scale = 2)
	private BigDecimal lengthShortMm;

	@Column(name = "thickness_mm", precision = 5, scale = 2)
	private BigDecimal thicknessMm;

	@Column(name = "form_name", length = 50)
	private String formName;

	@Column(name = "chart_text")
	private String chartText;

	@Column(name = "source_change_date")
	private LocalDate sourceChangeDate;

	@Column(name = "refreshed_at", nullable = false)
	private OffsetDateTime refreshedAt;

	@Column(name = "created_at", insertable = false, updatable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", insertable = false, updatable = false)
	private OffsetDateTime updatedAt;

	private PillVisual(String itemSeq) {
		this.itemSeq = itemSeq;
	}

	public static PillVisual create(String itemSeq) {
		return new PillVisual(itemSeq);
	}

	public void refresh(PillVisualRefreshValues values, Clock clock) {
		this.imageUrl = values.imageUrl();
		this.drugShape = values.drugShape();
		this.colorPrimary = values.colorPrimary();
		this.colorSecondary = values.colorSecondary();
		this.printFront = values.printFront();
		this.printBack = values.printBack();
		this.lineFront = values.lineFront();
		this.lineBack = values.lineBack();
		this.lengthLongMm = values.lengthLongMm();
		this.lengthShortMm = values.lengthShortMm();
		this.thicknessMm = values.thicknessMm();
		this.formName = values.formName();
		this.chartText = values.chartText();
		this.sourceChangeDate = values.sourceChangeDate();
		this.refreshedAt = OffsetDateTime.now(clock);
	}

	public record PillVisualRefreshValues(
			String imageUrl,
			String drugShape,
			String colorPrimary,
			String colorSecondary,
			String printFront,
			String printBack,
			String lineFront,
			String lineBack,
			BigDecimal lengthLongMm,
			BigDecimal lengthShortMm,
			BigDecimal thicknessMm,
			String formName,
			String chartText,
			LocalDate sourceChangeDate
	) {
	}
}
