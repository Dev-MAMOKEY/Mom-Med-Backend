package mamokey.mom_med.backend.domain.weather.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/** POST /v1/admin/weather/etl-run 응답 DTO */
@Schema(description = "날씨 ETL 실행 결과 통계")
public record EtlRunStats(
        @JsonProperty("started_at") Instant startedAt,
        @Schema(description = "KMA API를 신규 호출한 격자 수 (캐시 히트 격자는 제외)")
        @JsonProperty("grids_fetched") int gridsFetched,
        @Schema(description = "FCM 푸시 발송 성공 건수")
        @JsonProperty("advisories_sent") int advisoriesSent,
        @Schema(description = "알람 피로도 중복으로 스킵된 건수 (당일 동일 룰 이미 발송)")
        @JsonProperty("fatigue_skipped") int fatigueSkipped,
        @Schema(description = "의료진 미승인 룰로 스킵된 건수")
        @JsonProperty("review_skipped") int reviewSkipped,
        @Schema(description = "실행 상태. completed | skipped_duplicate_run")
        String status
) {
    public static EtlRunStats skippedDuplicate(Instant startedAt) {
        return new EtlRunStats(startedAt, 0, 0, 0, 0, "skipped_duplicate_run");
    }
}
