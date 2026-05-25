package mamokey.mom_med.backend.domain.weather.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * GET /v1/parents/{parentId}/weather-advisory 응답입니다.
 *
 * <p>Slice 06은 외부 기상청 호출을 하지 않으므로 weatherAlertsToday는 simulate_alert 또는 Slice 07 입력값을 그대로 반영합니다.</p>
 */
public record ParentWeatherAdvisoryResponse(
		@JsonProperty("parent_id") UUID parentId,
		LocalDate date,
		@JsonProperty("weather_alerts_today") List<String> weatherAlertsToday,
		List<WeatherAdvisoryResponse> advisories
) {
}
