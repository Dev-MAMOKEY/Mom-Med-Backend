package mamokey.mom_med.backend.domain.nb.controller;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Slice 03 LLM 사용량을 Prometheus text 형식으로 노출하는 최소 metrics 엔드포인트입니다.
 *
 * <p>운영 관측성은 이후 Prometheus/Actuator로 확장할 수 있지만, 현재 슬라이스에서는
 * NB 추출 시 DB에 저장한 token_input, token_output, cost_usd를 바로 확인할 수 있으면 충분합니다.
 * Spring Boot + Spring Data JPA 환경에서는 JdbcTemplate이 항상 자동 등록되므로
 * 별도의 조건부 등록 없이 일반 컨트롤러로 노출합니다.</p>
 */
@RestController
public class NbMetricsController {

	private static final String NB_USAGE_QUERY = """
			SELECT
			    llm_model,
			    COALESCE(SUM(token_input), 0) AS token_input,
			    COALESCE(SUM(token_output), 0) AS token_output,
			    COALESCE(SUM(cost_usd), 0) AS cost_usd
			FROM derived.nb_extractions
			GROUP BY llm_model
			ORDER BY llm_model
			""";

	private final JdbcTemplate jdbcTemplate;

	public NbMetricsController(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	/**
	 * NB 추출 누적 토큰과 비용을 `/metrics`에서 조회합니다.
	 *
	 * <p>Prometheus가 바로 긁을 수 있도록 JSON이 아니라 text/plain을 반환합니다.
	 * model label은 LLM 모델별 비용 추적을 위해 남겨둡니다.</p>
	 */
	@GetMapping(value = "/metrics", produces = MediaType.TEXT_PLAIN_VALUE)
	public String metrics() {
		List<NbUsageMetric> rows = jdbcTemplate.query(
				NB_USAGE_QUERY,
				(rs, rowNum) -> new NbUsageMetric(
						rs.getString("llm_model"),
						rs.getLong("token_input"),
						rs.getLong("token_output"),
						rs.getBigDecimal("cost_usd")
				)
		);

		StringBuilder body = new StringBuilder();
		body.append("# HELP nb_extraction_token_input_total Total input tokens used by NB extraction.\n");
		body.append("# TYPE nb_extraction_token_input_total counter\n");
		for (NbUsageMetric row : rows) {
			body.append("nb_extraction_token_input_total{model=\"")
					.append(escapeLabel(row.model()))
					.append("\"} ")
					.append(row.inputTokens())
					.append('\n');
		}

		body.append("# HELP nb_extraction_token_output_total Total output tokens used by NB extraction.\n");
		body.append("# TYPE nb_extraction_token_output_total counter\n");
		for (NbUsageMetric row : rows) {
			body.append("nb_extraction_token_output_total{model=\"")
					.append(escapeLabel(row.model()))
					.append("\"} ")
					.append(row.outputTokens())
					.append('\n');
		}

		body.append("# HELP nb_extraction_cost_usd_total Total estimated USD cost used by NB extraction.\n");
		body.append("# TYPE nb_extraction_cost_usd_total counter\n");
		for (NbUsageMetric row : rows) {
			body.append("nb_extraction_cost_usd_total{model=\"")
					.append(escapeLabel(row.model()))
					.append("\"} ")
					.append(row.costUsd())
					.append('\n');
		}
		return body.toString();
	}

	private static String escapeLabel(String value) {
		if (value == null) {
			return "unknown";
		}
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	private record NbUsageMetric(
			String model,
			long inputTokens,
			long outputTokens,
			BigDecimal costUsd
	) {
	}
}
