package mamokey.mom_med.backend.domain.weather.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** POST /v1/admin/weather/etl-run 요청 DTO */
@Schema(description = "날씨 ETL 수동 실행 요청")
public record EtlRunRequest(
        @Schema(description = "기준일 (YYYYMMDD). 미지정 시 오늘 KST.", example = "20260526")
        @JsonProperty("base_date") String baseDate,
        @Schema(description = "테스트용 특보 주입. 지정 시 KMA API 생략. 예: [\"폭염경보\", \"한파주의보\"]")
        @JsonProperty("simulate_alerts") List<String> simulateAlerts
) {
    public List<String> effectiveSimulateAlerts() {
        return simulateAlerts != null ? simulateAlerts : List.of();
    }
}
