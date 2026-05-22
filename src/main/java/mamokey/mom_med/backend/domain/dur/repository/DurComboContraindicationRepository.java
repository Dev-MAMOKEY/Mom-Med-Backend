package mamokey.mom_med.backend.domain.dur.repository;

import java.util.List;

import mamokey.mom_med.backend.domain.dur.entity.DurComboContraindication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 병용금기 테이블 조회 Repository입니다.
 *
 * <p>두 성분은 CSV에 A/B 방향으로 저장되지만 사용자는 어떤 순서로 약을 추가할지 알 수 없습니다.
 * 그래서 양방향을 모두 {@code =} 조건으로 조회하되, LIKE 검색은 절대 사용하지 않습니다.</p>
 */
public interface DurComboContraindicationRepository extends JpaRepository<DurComboContraindication, Long> {

	@Query("""
			select combo
			from DurComboContraindication combo
			where (combo.ingredientNormA = :ingredientA and combo.ingredientNormB = :ingredientB)
			   or (combo.ingredientNormA = :ingredientB and combo.ingredientNormB = :ingredientA)
			""")
	List<DurComboContraindication> findExactPair(
			@Param("ingredientA") String ingredientA,
			@Param("ingredientB") String ingredientB
	);
}
