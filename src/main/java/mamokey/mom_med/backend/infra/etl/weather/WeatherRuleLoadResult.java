package mamokey.mom_med.backend.infra.etl.weather;

/**
 * weather_rules seed 적재 결과입니다.
 *
 * <p>운영자가 seed를 다시 적재했을 때 새로 추가된 행과 갱신된 행을 구분해 로그로 확인할 수 있게 합니다.</p>
 */
public record WeatherRuleLoadResult(int inserted, int updated, int total) {
}
