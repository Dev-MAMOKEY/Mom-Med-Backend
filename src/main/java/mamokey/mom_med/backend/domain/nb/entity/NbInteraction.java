package mamokey.mom_med.backend.domain.nb.entity;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * derived.nb_interactions 테이블 Entity입니다.
 *
 * <p>LLM 추출 JSON의 interactions 배열을 행 단위로 펼친 결과입니다. SafetyJudge는 이 테이블을
 * item_seq로 조회하고, partner_drug_norm 또는 약물군 ATC prefix로 기존/신규 약과 매칭합니다.</p>
 */
@Getter
@Entity
@Table(name = "nb_interactions", schema = "derived")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NbInteraction {

	public static final String ENTRY_TYPE_DRUG_DRUG = "drug_drug";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "extraction_id", nullable = false)
	private Long extractionId;

	@Column(name = "item_seq", nullable = false, length = 20)
	private String itemSeq;

	@Column(name = "drug_name", nullable = false, length = 200)
	private String drugName;

	@Column(name = "entry_type", nullable = false, length = 20)
	private String entryType;

	@Column(name = "partner_drug_ko", length = 300)
	private String partnerDrugKo;

	@Column(name = "partner_drug_norm", length = 200)
	private String partnerDrugNorm;

	@Column(name = "partner_drug_en", length = 200)
	private String partnerDrugEn;

	@Column(name = "is_drug_group", nullable = false)
	private boolean drugGroup;

	@Column(name = "patient_class_text", length = 300)
	private String patientClassText;

	@Column(name = "patient_class_kcd", length = 50)
	private String patientClassKcd;

	@Column(name = "risk_level", nullable = false, length = 30)
	private String riskLevel;

	@Column(name = "reason_summary")
	private String reasonSummary;

	@Column(name = "source_quote")
	private String sourceQuote;

	@Column(name = "created_at", insertable = false, updatable = false)
	private OffsetDateTime createdAt;

	public static final String ENTRY_TYPE_PATIENT_CLASS = "patient_class";

	public static NbInteraction drugDrug(
			Long extractionId,
			String itemSeq,
			String drugName,
			String partnerDrugKo,
			String partnerDrugNorm,
			String partnerDrugEn,
			boolean drugGroup,
			String riskLevel,
			String reasonSummary,
			String sourceQuote
	) {
		NbInteraction interaction = new NbInteraction();
		interaction.extractionId = extractionId;
		interaction.itemSeq = itemSeq;
		interaction.drugName = drugName;
		interaction.entryType = ENTRY_TYPE_DRUG_DRUG;
		interaction.partnerDrugKo = partnerDrugKo;
		interaction.partnerDrugNorm = partnerDrugNorm;
		interaction.partnerDrugEn = partnerDrugEn;
		interaction.drugGroup = drugGroup;
		interaction.riskLevel = riskLevel;
		interaction.reasonSummary = reasonSummary;
		interaction.sourceQuote = sourceQuote;
		return interaction;
	}

	/**
	 * 환자분류 금기 entry 생성 팩토리 메서드 (Slice 05).
	 *
	 * <p>NB_DOC_DATA에서 추출한 "이 약을 투여하면 안 되는 환자 분류" 정보를 저장합니다.
	 * partner_drug* 필드는 null, patient_class_text/kcd가 의미를 가집니다.</p>
	 */
	public static NbInteraction patientClass(
			Long extractionId,
			String itemSeq,
			String drugName,
			String patientClassText,
			String patientClassKcd,
			String riskLevel,
			String reasonSummary,
			String sourceQuote
	) {
		NbInteraction interaction = new NbInteraction();
		interaction.extractionId = extractionId;
		interaction.itemSeq = itemSeq;
		interaction.drugName = drugName;
		interaction.entryType = ENTRY_TYPE_PATIENT_CLASS;
		interaction.patientClassText = patientClassText;
		interaction.patientClassKcd = patientClassKcd;
		interaction.drugGroup = false;
		interaction.riskLevel = riskLevel;
		interaction.reasonSummary = reasonSummary;
		interaction.sourceQuote = sourceQuote;
		return interaction;
	}
}
