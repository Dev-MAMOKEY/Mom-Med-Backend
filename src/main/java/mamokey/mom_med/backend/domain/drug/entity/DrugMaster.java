package mamokey.mom_med.backend.domain.drug.entity;

import java.time.Clock;
import java.time.Duration;
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
 * 식약처 의약품 제품 허가정보를 저장하는 약 마스터 Entity입니다.
 *
 * <p>이 테이블은 Slice 01의 결과물이면서 Slice 02 DUR 매칭, Slice 03 NB 추출,
 * Slice 04 부모 약장 등록에서 공통 기준 데이터로 사용됩니다.</p>
 */
@Getter
@Entity
@Table(name = "drugs_master", schema = "ref")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DrugMaster {

	private static final Duration DEFAULT_CACHE_TTL = Duration.ofDays(30);

	@Id
	@Column(name = "item_seq", length = 20)
	private String itemSeq;

	@Column(name = "item_name", nullable = false, length = 300)
	private String itemName;

	@Column(name = "item_name_eng", length = 300)
	private String itemNameEng;

	@Column(name = "entp_name", nullable = false, length = 200)
	private String entpName;

	@Column(name = "entp_no", length = 20)
	private String entpNo;

	@Column(name = "item_permit_date")
	private LocalDate itemPermitDate;

	@Column(name = "specialty_type", length = 20)
	private String specialtyType;

	@Column(name = "edi_code", length = 20)
	private String ediCode;

	@Column(name = "atc_code", length = 10)
	private String atcCode;

	@Column(name = "main_ingr_en", length = 500)
	private String mainIngrEn;

	@Column(name = "main_ingr_norm", length = 200)
	private String mainIngrNorm;

	@Column(name = "chart_text")
	private String chartText;

	@Column(name = "nb_doc_data")
	private String nbDocData;

	@Column(name = "ee_doc_data")
	private String eeDocData;

	@Column(name = "ud_doc_data")
	private String udDocData;

	@Column(name = "source_change_date")
	private LocalDate sourceChangeDate;

	@Column(name = "refreshed_at", nullable = false)
	private OffsetDateTime refreshedAt;

	@Column(name = "created_at", insertable = false, updatable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", insertable = false, updatable = false)
	private OffsetDateTime updatedAt;

	private DrugMaster(String itemSeq) {
		this.itemSeq = itemSeq;
	}

	public static DrugMaster create(String itemSeq) {
		return new DrugMaster(itemSeq);
	}

	/**
	 * 식약처 상세 응답으로 약 마스터 정보를 갱신합니다.
	 * JPA save가 insert/update 모두 처리하므로 같은 메서드를 재사용합니다.
	 */
	public void refresh(
			DrugMasterRefreshValues values,
			Clock clock
	) {
		this.itemName = values.itemName();
		this.itemNameEng = values.itemNameEng();
		this.entpName = values.entpName();
		this.entpNo = values.entpNo();
		this.itemPermitDate = values.itemPermitDate();
		this.specialtyType = values.specialtyType();
		this.ediCode = values.ediCode();
		this.atcCode = values.atcCode();
		this.mainIngrEn = values.mainIngrEn();
		this.mainIngrNorm = values.mainIngrNorm();
		this.chartText = values.chartText();
		this.nbDocData = values.nbDocData();
		this.eeDocData = values.eeDocData();
		this.udDocData = values.udDocData();
		this.sourceChangeDate = values.sourceChangeDate();
		this.refreshedAt = OffsetDateTime.now(clock);
	}

	/**
	 * 식약처 CHANGE_DATE가 있으면 날짜 비교를 우선하고, 없으면 refreshed_at 기준 30일 TTL을 적용합니다.
	 */
	public boolean isFresh(LocalDate apiChangeDate, Clock clock) {
		// API가 CHANGE_DATE를 주면 원본 날짜를 신뢰하고, 날짜가 없을 때만 TTL fallback을 사용합니다.
		if (apiChangeDate != null) {
			return sourceChangeDate.equals(apiChangeDate);
		}
		if (refreshedAt == null) {
			return false;
		}
		return refreshedAt.isAfter(OffsetDateTime.now(clock).minus(DEFAULT_CACHE_TTL));
	}

	public record DrugMasterRefreshValues(
			String itemName,
			String itemNameEng,
			String entpName,
			String entpNo,
			LocalDate itemPermitDate,
			String specialtyType,
			String ediCode,
			String atcCode,
			String mainIngrEn,
			String mainIngrNorm,
			String chartText,
			String nbDocData,
			String eeDocData,
			String udDocData,
			LocalDate sourceChangeDate
	) {
	}
}
