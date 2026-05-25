package mamokey.mom_med.backend.domain.weather.dto;

import java.util.List;

/**
 * GET /v1/admin/weather-rules 응답입니다.
 */
public record WeatherRuleAdminListResponse(
		int total,
		List<WeatherRuleAdminResponse> rules
) {
}
