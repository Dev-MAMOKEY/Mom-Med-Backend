package mamokey.mom_med.backend.domain.weather.entity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
 * ref.weather_rules 테이블과 매핑되는 날씨-질환 룰 엔티티입니다.
 *
 * <p>Slice 07은 매일 생성한 기상특보와 부모의 기저질환 코드를 이용해 이 테이블을 정확 매칭합니다.
 * 그래서 diseaseCode + weatherAlert가 핵심 조회 키이고, id(rule_id)는 푸시 중복 방지 키로 내려갑니다.</p>
 */
@Entity
@Table(name = "weather_rules", schema = "ref")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WeatherRule {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "rule_version", nullable = false, length = 20)
	private String ruleVersion;

	@Column(name = "disease_code", nullable = false, length = 10)
	private String diseaseCode;

	@Column(name = "disease_name", nullable = false, length = 100)
	private String diseaseName;

	@Column(name = "weather_alert", nullable = false, length = 50)
	private String weatherAlert;

	@Column(nullable = false, length = 20)
	private String severity;

	@Column(nullable = false, length = 100)
	private String title;

	@Column(name = "message_template", nullable = false, columnDefinition = "TEXT")
	private String messageTemplate;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "specific_drugs_to_note", nullable = false, columnDefinition = "jsonb")
	private List<String> specificDrugsToNote = List.of();

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "patient_actions", nullable = false, columnDefinition = "jsonb")
	private List<String> patientActions = List.of();

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "source_citations", nullable = false, columnDefinition = "jsonb")
	private List<Map<String, Object>> sourceCitations = List.of();

	@Column(columnDefinition = "TEXT")
	private String rationale;

	@Column(name = "general_knowledge_used", nullable = false)
	private boolean generalKnowledgeUsed;

	@Column(name = "approved_by", length = 100)
	private String approvedBy;

	@Column(name = "approved_at")
	private LocalDateTime approvedAt;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
	private LocalDateTime updatedAt;

	public static WeatherRule create(
			String ruleVersion,
			String diseaseCode,
			String diseaseName,
			String weatherAlert,
			String severity,
			String title,
			String messageTemplate,
			List<String> specificDrugsToNote,
			List<String> patientActions,
			List<Map<String, Object>> sourceCitations,
			String rationale,
			boolean generalKnowledgeUsed
	) {
		WeatherRule rule = new WeatherRule();
		rule.ruleVersion = ruleVersion;
		rule.diseaseCode = diseaseCode;
		rule.diseaseName = diseaseName;
		rule.weatherAlert = weatherAlert;
		rule.refresh(severity, title, messageTemplate, specificDrugsToNote, patientActions,
				sourceCitations, rationale, generalKnowledgeUsed);
		return rule;
	}

	/**
	 * seed 재적재 시 기존 rule_id를 유지한 채 본문만 갱신합니다.
	 * rule_id는 Slice 07 dedup 키이므로 불필요하게 새 행을 만들지 않는 것이 중요합니다.
	 */
	public void refresh(
			String severity,
			String title,
			String messageTemplate,
			List<String> specificDrugsToNote,
			List<String> patientActions,
			List<Map<String, Object>> sourceCitations,
			String rationale,
			boolean generalKnowledgeUsed
	) {
		this.severity = severity;
		this.title = title;
		this.messageTemplate = messageTemplate;
		this.specificDrugsToNote = specificDrugsToNote == null ? List.of() : specificDrugsToNote;
		this.patientActions = patientActions == null ? List.of() : patientActions;
		this.sourceCitations = sourceCitations == null ? List.of() : sourceCitations;
		this.rationale = rationale;
		this.generalKnowledgeUsed = generalKnowledgeUsed;
	}

	/**
	 * 의료진 검수 완료 처리입니다.
	 * adjustedMessage가 있으면 실제 사용자에게 내려갈 메시지 템플릿도 함께 보정합니다.
	 */
	public void approve(String approvedBy, String adjustedMessage) {
		this.approvedBy = approvedBy;
		this.approvedAt = LocalDateTime.now();
		if (adjustedMessage != null && !adjustedMessage.isBlank()) {
			this.messageTemplate = adjustedMessage;
		}
	}

	public boolean requiresReview() {
		return generalKnowledgeUsed && approvedAt == null;
	}
}
