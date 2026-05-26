package mamokey.mom_med.backend.domain.weather.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

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
@Schema(description = "부모 날씨 advisory 조회 응답")
public record ParentWeatherAdvisoryResponse(
        @JsonProperty("parent_id") UUID parentId,
        @Schema(description = "조회 기준일")
        LocalDate date,
        @Schema(description = "기상청 격자 좌표. 부모 주소가 등록되지 않은 경우 null.")
        @JsonProperty("grid") GridInfo grid,
        @Schema(description = "KMA 관측 기온. simulate_alert 요청이거나 데이터 없으면 null.")
        @JsonProperty("observed") ObservedTemps observed,
        @Schema(description = "활성 기상특보 목록. 예: [\"폭염경보\", \"한파주의보\"]")
        @JsonProperty("active_alerts") List<String> activeAlerts,
        @Schema(description = "매칭된 advisory 목록. 기저질환 × 특보 룰 정확 매칭 결과.")
        List<WeatherAdvisoryResponse> advisories
) {

    @Schema(description = "기상청 격자 좌표")
    public record GridInfo(
            @Schema(description = "격자 X 좌표") Integer nx,
            @Schema(description = "격자 Y 좌표") Integer ny
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "KMA 관측 기온")
    public record ObservedTemps(
            @Schema(description = "일 최고기온 (폭염 판정 기준, ℃)") Double tmx,
            @Schema(description = "내일 아침 최저기온 (한파 판정 기준, ℃)") Double tmn
    ) {}
}
