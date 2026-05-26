package mamokey.mom_med.backend.domain.weather.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/** POST /v1/admin/weather/etl-run 응답 DTO */
public record EtlRunStats(
        @JsonProperty("started_at") Instant startedAt,
        @JsonProperty("grids_fetched") int gridsFetched,
        @JsonProperty("advisories_sent") int advisoriesSent,
        @JsonProperty("fatigue_skipped") int fatigueSkipped,
        @JsonProperty("review_skipped") int reviewSkipped,
        String status
) {
    public static EtlRunStats skippedDuplicate(Instant startedAt) {
        return new EtlRunStats(startedAt, 0, 0, 0, 0, "skipped_duplicate_run");
    }
}
