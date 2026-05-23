package mamokey.mom_med.backend.domain.dur.repository;

import java.util.List;

import mamokey.mom_med.backend.domain.dur.entity.DurElderlyNsaidCaution;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * NSAID 노인주의 DUR 데이터를 성분 정규화 키로 조회하는 Repository입니다.
 */
public interface DurElderlyNsaidCautionRepository extends JpaRepository<DurElderlyNsaidCaution, Long> {

	List<DurElderlyNsaidCaution> findByIngredientNorm(String ingredientNorm);
}
