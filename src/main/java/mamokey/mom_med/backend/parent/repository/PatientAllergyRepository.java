package mamokey.mom_med.backend.parent.repository;

import mamokey.mom_med.backend.parent.domain.PatientAllergy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * app.patient_allergies JPA 리포지토리.
 */
public interface PatientAllergyRepository extends JpaRepository<PatientAllergy, Long> {

    /** 부모의 활성 알레르기 목록 (soft delete 제외) */
    List<PatientAllergy> findByParentIdAndDeletedAtIsNull(UUID parentId);

    /** 부모의 삭제된 알레르기 목록 (이력) */
    List<PatientAllergy> findByParentIdAndDeletedAtIsNotNull(UUID parentId);

    /** 부모의 특정 알레르기 조회 (soft delete 무관) */
    Optional<PatientAllergy> findByIdAndParentId(Long id, UUID parentId);
}
