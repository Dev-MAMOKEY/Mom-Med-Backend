package mamokey.mom_med.backend.domain.weather.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * logs.weather_observations_daily 파티션 테이블 접근 (Slice 07 v2).
 *
 * <p>파티션 테이블의 복합 PK(observed_date, id) 제약으로 JPA 대신 JdbcTemplate을 사용합니다.</p>
 */
@Repository
public class WeatherObservationDailyJdbcRepository {

    private static final Logger log = LoggerFactory.getLogger(WeatherObservationDailyJdbcRepository.class);
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public WeatherObservationDailyJdbcRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    /** 오늘 날짜 + 격자로 이미 캐시된 관측 정보를 조회합니다. */
    public Optional<ObsResult> findByDateAndGrid(LocalDate date, short nx, short ny) {
        String sql = """
                SELECT id, tmx, tmn, derived_alerts
                FROM logs.weather_observations_daily
                WHERE observed_date = :date AND nx = :nx AND ny = :ny
                LIMIT 1
                """;
        var params = new MapSqlParameterSource()
                .addValue("date", Date.valueOf(date))
                .addValue("nx", nx)
                .addValue("ny", ny);

        return jdbc.query(sql, params, rs -> {
            if (!rs.next()) return Optional.empty();
            long id = rs.getLong("id");
            Double tmx = rs.getObject("tmx") != null ? rs.getDouble("tmx") : null;
            Double tmn = rs.getObject("tmn") != null ? rs.getDouble("tmn") : null;
            List<String> alerts = parseAlerts(rs.getString("derived_alerts"));
            return Optional.of(new ObsResult(id, tmx, tmn, alerts));
        });
    }

    /**
     * 새 관측 행을 삽입하고 생성된 id를 반환합니다.
     *
     * @param derivedAlerts 도출된 특보 목록 (JSON 배열로 직렬화)
     * @return 생성된 id
     */
    public long save(LocalDate observedDate, short nx, short ny,
                     Double tmx, Double tmn, List<String> derivedAlerts,
                     String baseDate, String baseTime) {
        String alertsJson = toJson(derivedAlerts);
        String sql = """
                INSERT INTO logs.weather_observations_daily
                    (observed_date, nx, ny, tmx, tmn, derived_alerts, base_date, base_time)
                VALUES (:observedDate, :nx, :ny, :tmx, :tmn, CAST(:alerts AS jsonb), :baseDate, :baseTime)
                ON CONFLICT (observed_date, nx, ny) DO NOTHING
                RETURNING id
                """;
        var params = new MapSqlParameterSource()
                .addValue("observedDate", Date.valueOf(observedDate))
                .addValue("nx", nx)
                .addValue("ny", ny)
                .addValue("tmx", tmx)
                .addValue("tmn", tmn)
                .addValue("alerts", alertsJson)
                .addValue("baseDate", baseDate)
                .addValue("baseTime", baseTime);

        var keyHolder = new GeneratedKeyHolder();
        jdbc.update(sql, params, keyHolder, new String[]{"id"});
        if (keyHolder.getKey() != null) {
            return keyHolder.getKey().longValue();
        }
        // ON CONFLICT DO NOTHING: 이미 존재하는 경우 기존 id를 조회
        return findByDateAndGrid(observedDate, nx, ny)
                .map(ObsResult::id)
                .orElse(-1L);
    }

    private List<String> parseAlerts(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, STRING_LIST);
        } catch (JsonProcessingException e) {
            log.warn("derived_alerts JSON 파싱 실패: {}", json);
            return List.of();
        }
    }

    private String toJson(List<String> list) {
        try {
            return objectMapper.writeValueAsString(list == null ? List.of() : list);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    /** 날씨 관측 캐시 조회 결과 DTO */
    public record ObsResult(long id, Double tmx, Double tmn, List<String> derivedAlerts) {
        public boolean hasAlerts() {
            return derivedAlerts != null && !derivedAlerts.isEmpty();
        }
    }
}
