package mamokey.mom_med.backend.domain.drug.repository;

import java.util.Optional;

import mamokey.mom_med.backend.domain.drug.entity.DrugMaster;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * ref.drugs_master 접근을 담당하는 Repository입니다.
 */
public interface DrugMasterRepository extends JpaRepository<DrugMaster, String> {

	/**
	 * 같은 약 이름으로 다시 검색할 때 외부 API를 부르지 않고 DB 캐시를 먼저 확인하기 위한 exact lookup입니다.
	 */
	Optional<DrugMaster> findFirstByItemNameIgnoreCase(String itemName);
}
