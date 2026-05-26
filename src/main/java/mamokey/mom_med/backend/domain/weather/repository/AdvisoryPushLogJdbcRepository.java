package mamokey.mom_med.backend.domain.weather.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * logs.advisory_push_log 파티션 테이블 접근 (Slice 07 v2).
 *
 * <p>파티션 테이블의 복합 PK(sent_at, id) 제약으로 JPA 대신 JdbcTemplate을 사용합니다.</p>
 */
@Repository
public class AdvisoryPushLogJdbcRepository {

    private static final Logger log = LoggerFactory.getLogger(AdvisoryPushLogJdbcRepository.class);

    private final NamedParameterJdbcTemplate jdbc;

    public AdvisoryPushLogJdbcRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** (parent, rule, 날짜) 조합이 이미 오늘 sent/simulated 상태로 기록되었는지 확인합니다. */
    public boolean existsByParentRuleDate(UUID parentId, long ruleId, LocalDate date) {
        String sql = """
                SELECT COUNT(*) FROM logs.advisory_push_log
                WHERE parent_id = :parentId
                  AND rule_id   = :ruleId
                  AND (sent_at AT TIME ZONE 'Asia/Seoul')::date = :date
                  AND delivery_status IN ('sent', 'simulated')
                """;
        var params = new MapSqlParameterSource()
                .addValue("parentId", parentId)
                .addValue("ruleId", ruleId)
                .addValue("date", Date.valueOf(date));
        Integer count = jdbc.queryForObject(sql, params, Integer.class);
        return count != null && count > 0;
    }

    /** 가장 최근에 (parent, rule) 조합으로 sent/simulated 상태 로그가 기록된 시각을 반환합니다. */
    public Optional<Instant> findLatestSentAt(UUID parentId, long ruleId, LocalDate date) {
        String sql = """
                SELECT MAX(sent_at) FROM logs.advisory_push_log
                WHERE parent_id = :parentId
                  AND rule_id   = :ruleId
                  AND (sent_at AT TIME ZONE 'Asia/Seoul')::date = :date
                  AND delivery_status IN ('sent', 'simulated')
                """;
        var params = new MapSqlParameterSource()
                .addValue("parentId", parentId)
                .addValue("ruleId", ruleId)
                .addValue("date", Date.valueOf(date));
        Timestamp ts = jdbc.queryForObject(sql, params, Timestamp.class);
        return Optional.ofNullable(ts).map(Timestamp::toInstant);
    }

    /** Advisory 푸시 이력 1건을 삽입합니다. */
    public void save(UUID parentId, long ruleId, Long weatherObsId,
                     String pushPayloadJson, String deliveryStatus) {
        String sql = """
                INSERT INTO logs.advisory_push_log
                    (parent_id, rule_id, weather_obs_id, push_payload, delivery_status)
                VALUES (:parentId, :ruleId, :weatherObsId, CAST(:payload AS jsonb), :status)
                """;
        var params = new MapSqlParameterSource()
                .addValue("parentId", parentId)
                .addValue("ruleId", ruleId)
                .addValue("weatherObsId", weatherObsId)
                .addValue("payload", pushPayloadJson)
                .addValue("status", deliveryStatus);
        jdbc.update(sql, params);
    }

    /** 최근 N일간 푸시 이력을 최신순으로 조회합니다 (운영 모니터링용). */
    public List<RecentPushEntry> findRecentPushes(int days) {
        String sql = """
                SELECT id, parent_id, rule_id, weather_obs_id, delivery_status, sent_at
                FROM logs.advisory_push_log
                WHERE sent_at >= NOW() - (CAST(:days AS INT) * INTERVAL '1 day')
                  AND delivery_status IN ('sent', 'simulated', 'failed')
                ORDER BY sent_at DESC
                LIMIT 500
                """;
        return jdbc.query(sql, Map.of("days", days), (rs, rowNum) -> new RecentPushEntry(
                rs.getLong("id"),
                UUID.fromString(rs.getString("parent_id")),
                rs.getLong("rule_id"),
                rs.getObject("weather_obs_id") != null ? rs.getLong("weather_obs_id") : null,
                rs.getString("delivery_status"),
                rs.getTimestamp("sent_at").toInstant()
        ));
    }

    /** 관리자 최근 푸시 조회 응답 항목 */
    public record RecentPushEntry(
            long id,
            UUID parentId,
            long ruleId,
            Long weatherObsId,
            String deliveryStatus,
            Instant sentAt
    ) {}
}
