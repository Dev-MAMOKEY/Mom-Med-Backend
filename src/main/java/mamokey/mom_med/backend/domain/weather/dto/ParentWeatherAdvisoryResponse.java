package mamokey.mom_med.backend.domain.weather.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * GET /v1/parents/{parentId}/weather-advisory 응답 (Slice 07 v2).
 *
 * <p>grid / observed / active_alerts 는 실제 KMA 데이터가 있을 때만 채워집니다.
 * simulate_alert 요청이면 observed 는 null입니다.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ParentWeatherAdvisoryResponse(
        @JsonProperty("parent_id") UUID parentId,
        LocalDate date,
        @JsonProperty("grid") GridInfo grid,
        @JsonProperty("observed") ObservedTemps observed,
        @JsonProperty("active_alerts") List<String> activeAlerts,
        List<WeatherAdvisoryResponse> advisories
) {

    public record GridInfo(Integer nx, Integer ny) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ObservedTemps(Double tmx, Double tmn) {}
}
