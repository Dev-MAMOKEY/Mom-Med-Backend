package mamokey.mom_med.backend.domain.weather.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** POST /v1/admin/weather/etl-run 요청 DTO */
public record EtlRunRequest(
        @JsonProperty("base_date") String baseDate,                       // 선택. YYYYMMDD 형식. 미지정 시 오늘
        @JsonProperty("simulate_alerts") List<String> simulateAlerts      // 테스트용. 지정 시 KMA API 대신 이 특보를 주입
) {
    public List<String> effectiveSimulateAlerts() {
        return simulateAlerts != null ? simulateAlerts : List.of();
    }
}
