package mamokey.mom_med.backend.parent.repository;

import mamokey.mom_med.backend.parent.domain.PatientCondition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 기저질환 레포지토리 (Slice 05).
 *
 * <p>deleted_at IS NULL = 활성, IS NOT NULL = 삭제(이력) 조건으로 분리 조회합니다.</p>
 */
public interface PatientConditionRepository extends JpaRepository<PatientCondition, Long> {

    /** 활성 기저질환 목록 */
    List<PatientCondition> findByParentIdAndDeletedAtIsNull(UUID parentId);

    /** 삭제된 기저질환 이력 */
    List<PatientCondition> findByParentIdAndDeletedAtIsNotNull(UUID parentId);

    /** 활성 상태에서 단건 조회 (삭제 권한 검증용) */
    Optional<PatientCondition> findByIdAndParentIdAndDeletedAtIsNull(Long id, UUID parentId);

    /** 중복 등록 방지 (동일 질환명 + 활성 상태) */
    boolean existsByParentIdAndConditionNameAndDeletedAtIsNull(UUID parentId, String conditionName);
}
