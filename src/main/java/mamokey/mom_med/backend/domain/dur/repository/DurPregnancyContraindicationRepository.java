package mamokey.mom_med.backend.domain.dur.repository;

import java.util.List;

import mamokey.mom_med.backend.domain.dur.entity.DurPregnancyContraindication;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * ref.dur_pregnancy_contraindication 조회 Repository (Slice 05).
 *
 * <p>새 약 성분 정규화명으로 임부금기 행을 정확 조회합니다.
 * is_pregnant=true인 부모에게만 조회가 이루어집니다.</p>
 */
public interface DurPregnancyContraindicationRepository extends JpaRepository<DurPregnancyContraindication, Long> {

	/** 성분 정규화명으로 임부금기 행 전체 조회 */
	List<DurPregnancyContraindication> findByIngredientNorm(String ingredientNorm);
}
