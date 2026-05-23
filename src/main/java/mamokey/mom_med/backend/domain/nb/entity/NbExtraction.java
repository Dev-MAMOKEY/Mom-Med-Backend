package mamokey.mom_med.backend.domain.nb.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * derived.nb_extractions 테이블 Entity입니다.
 *
 * <p>하나의 약 라벨 버전(item_seq + change_date)에 대한 LLM 추출 원본 JSON과 환각 검증 결과를 저장합니다.
 * 같은 약/모델/prompt 조합은 캐시 키로 재사용되어 Gemini 재호출을 줄입니다.</p>
 */
@Getter
@Entity
@Table(name = "nb_extractions", schema = "derived")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NbExtraction {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "item_seq", nullable = false, length = 20)
	private String itemSeq;

	@Column(name = "drug_change_date")
	private LocalDate drugChangeDate;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "extraction_json", nullable = false)
	private Map<String, Object> extractionJson;

	@Column(name = "verified", nullable = false)
	private boolean verified;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "verification_summary")
	private Map<String, Object> verificationSummary;

	@Column(name = "llm_model", nullable = false, length = 50)
	private String llmModel;

	@Column(name = "prompt_version", nullable = false, length = 20)
	private String promptVersion;

	@Column(name = "token_input")
	private Integer tokenInput;

	@Column(name = "token_output")
	private Integer tokenOutput;

	@Column(name = "cost_usd", precision = 10, scale = 6)
	private BigDecimal costUsd;

	@Column(name = "extracted_at", insertable = false, updatable = false)
	private OffsetDateTime extractedAt;

	@Column(name = "created_at", insertable = false, updatable = false)
	private OffsetDateTime createdAt;

	public static NbExtraction create(String itemSeq, LocalDate drugChangeDate, String llmModel, String promptVersion) {
		NbExtraction extraction = new NbExtraction();
		extraction.itemSeq = itemSeq;
		extraction.drugChangeDate = drugChangeDate;
		extraction.llmModel = llmModel;
		extraction.promptVersion = promptVersion;
		return extraction;
	}

	public void refresh(
			Map<String, Object> extractionJson,
			boolean verified,
			Map<String, Object> verificationSummary,
			Integer tokenInput,
			Integer tokenOutput,
			BigDecimal costUsd
	) {
		this.extractionJson = extractionJson;
		this.verified = verified;
		this.verificationSummary = verificationSummary;
		this.tokenInput = tokenInput;
		this.tokenOutput = tokenOutput;
		this.costUsd = costUsd;
	}
}
