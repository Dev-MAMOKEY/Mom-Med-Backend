package mamokey.mom_med.backend.parent.repository;

import mamokey.mom_med.backend.parent.domain.PatientMedication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * app.patient_medications JPA 리포지토리.
 */
public interface PatientMedicationRepository extends JpaRepository<PatientMedication, Long> {

    /** 부모의 현재 복용 중인 약 목록 (soft delete 제외) */
    List<PatientMedication> findByParentIdAndDeletedAtIsNull(UUID parentId);

    /** 부모의 삭제된 약 목록 (이력) */
    List<PatientMedication> findByParentIdAndDeletedAtIsNotNull(UUID parentId);

    /** 부모의 특정 약 조회 (활성 레코드만) */
    Optional<PatientMedication> findByIdAndParentIdAndDeletedAtIsNull(Long id, UUID parentId);

    /** 중복 약 등록 여부 확인 (활성 레코드에 한해) */
    boolean existsByParentIdAndItemSeqAndDeletedAtIsNull(UUID parentId, String itemSeq);
}
