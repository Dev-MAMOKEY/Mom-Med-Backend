package mamokey.mom_med.backend.domain.dur.repository;

import java.util.List;

import mamokey.mom_med.backend.domain.dur.entity.DurAgeContraindication;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * ref.dur_age_contraindication 조회 Repository (Slice 05).
 *
 * <p>새 약 성분 정규화명으로 연령금기 행을 정확 조회합니다.
 * DurRuleEngine이 age_limit을 파싱해 부모 나이와 대조합니다.</p>
 */
public interface DurAgeContraindicationRepository extends JpaRepository<DurAgeContraindication, Long> {

	/** 성분 정규화명으로 연령금기 행 전체 조회 */
	List<DurAgeContraindication> findByIngredientNorm(String ingredientNorm);
}
