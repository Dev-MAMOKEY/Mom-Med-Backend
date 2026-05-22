package mamokey.mom_med.backend.domain.drug.repository;

import mamokey.mom_med.backend.domain.drug.entity.DrugMaster;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * ref.drugs_master 접근을 담당하는 Repository입니다.
 */
public interface DrugMasterRepository extends JpaRepository<DrugMaster, String> {
}
