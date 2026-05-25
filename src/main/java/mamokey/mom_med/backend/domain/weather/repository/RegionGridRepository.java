package mamokey.mom_med.backend.domain.weather.repository;

import java.util.Optional;

import mamokey.mom_med.backend.domain.weather.entity.RegionGrid;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * ref.region_grid 조회 Repository입니다.
 *
 * <p>동 단위 정확 매칭을 먼저 시도하고, 실패하면 시/군/구 fallback을 사용합니다.
 * 도농복합지역은 같은 시/군/구 안에서도 격자가 달라질 수 있어 동 우선순위가 중요합니다.</p>
 */
public interface RegionGridRepository extends JpaRepository<RegionGrid, Long> {

	Optional<RegionGrid> findFirstBySidoAndSigunguAndEupMyeonDong(
			String sido,
			String sigungu,
			String eupMyeonDong
	);

	@Query(value = """
			SELECT *
			FROM ref.region_grid
			WHERE sido = :sido
			  AND sigungu = :sigungu
			ORDER BY
			  CASE WHEN eup_myeon_dong IS NULL THEN 0 ELSE 1 END,
			  id ASC
			LIMIT 1
			""", nativeQuery = true)
	Optional<RegionGrid> findFallbackBySidoAndSigungu(
			@Param("sido") String sido,
			@Param("sigungu") String sigungu
	);
}
