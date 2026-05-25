package mamokey.mom_med.backend.infra.etl.weather;

/**
 * region_grid xlsx 적재 결과입니다.
 *
 * <p>기상청 파일은 행 수가 많으므로 전체 처리 건수와 신규/갱신 건수를 나누어 남깁니다.</p>
 */
public record RegionGridLoadResult(int inserted, int updated, int skipped, int total) {
}
