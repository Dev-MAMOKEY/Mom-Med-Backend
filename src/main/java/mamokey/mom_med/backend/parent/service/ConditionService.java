package mamokey.mom_med.backend.parent.service;

import lombok.RequiredArgsConstructor;
import mamokey.mom_med.backend.global.exception.CustomException;
import mamokey.mom_med.backend.global.exception.ErrorCode;
import mamokey.mom_med.backend.parent.domain.PatientCondition;
import mamokey.mom_med.backend.parent.dto.ConditionListResponse;
import mamokey.mom_med.backend.parent.dto.ConditionResponse;
import mamokey.mom_med.backend.parent.dto.CreateConditionRequest;
import mamokey.mom_med.backend.parent.repository.PatientConditionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 부모 기저질환 관리 서비스 (Slice 05).
 *
 * <p>TODO: Slice 04 (PatientProfile / ParentService) merge 후
 * parentService.findOrThrow(parentId) 호출로 부모 존재 검증 추가 필요.
 * 현재는 ParentService 의존성 없이 조건 CRUD만 구현합니다.</p>
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ConditionService {

    private final PatientConditionRepository conditionRepository;

    // ─── 조회 ─────────────────────────────────────────────────────────────

    public ConditionListResponse listConditions(UUID parentId) {
        // TODO: parentService.findOrThrow(parentId) — Slice 04 merge 후 추가

        List<ConditionResponse> active = conditionRepository
                .findByParentIdAndDeletedAtIsNull(parentId)
                .stream()
                .map(ConditionResponse::from)
                .toList();

        List<ConditionResponse> history = conditionRepository
                .findByParentIdAndDeletedAtIsNotNull(parentId)
                .stream()
                .map(ConditionResponse::from)
                .toList();

        return new ConditionListResponse(active, history);
    }

    // ─── 생성 ─────────────────────────────────────────────────────────────

    /**
     * 기저질환 등록.
     *
     * <ol>
     *   <li>동일 질환명 중복 등록 방지</li>
     *   <li>condition_norm: 질환명 소문자·공백 제거 정규화 (추후 HIRA 코드 매핑으로 대체 가능)</li>
     *   <li>저장 후 ConditionResponse 반환</li>
     * </ol>
     */
    @Transactional
    public ConditionResponse addCondition(UUID parentId, CreateConditionRequest req) {
        // TODO: parentService.findOrThrow(parentId) — Slice 04 merge 후 추가

        // 중복 등록 방지
        if (conditionRepository.existsByParentIdAndConditionNameAndDeletedAtIsNull(
                parentId, req.conditionName())) {
            throw new CustomException(ErrorCode.DUPLICATE_CONDITION);
        }

        // condition_norm: 정규화 (소문자 변환 + 공백·특수문자 제거)
        String conditionNorm = normalizeConditionName(req.conditionName());

        PatientCondition condition = PatientCondition.create(
                parentId,
                req.conditionName(),
                conditionNorm,
                req.kcdCode(),
                req.severity(),
                req.diagnosedAt(),
                req.notes()
        );

        conditionRepository.save(condition);
        return ConditionResponse.from(condition);
    }

    // ─── 삭제 ─────────────────────────────────────────────────────────────

    /**
     * 기저질환 soft delete — 이력은 보존됩니다.
     */
    @Transactional
    public void deleteCondition(UUID parentId, Long conditionId) {
        PatientCondition condition = conditionRepository
                .findByIdAndParentIdAndDeletedAtIsNull(conditionId, parentId)
                .orElseThrow(() -> new CustomException(ErrorCode.CONDITION_NOT_FOUND));

        condition.softDelete();
    }

    // ─── 내부 유틸 ────────────────────────────────────────────────────────

    /**
     * 질환명 정규화: 소문자 변환 + 공백·괄호·특수문자 제거.
     * 예) "2형 당뇨병(인슐린 비의존성)" → "2형당뇨병"
     */
    private static String normalizeConditionName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return name.toLowerCase()
                .replaceAll("[\\s()\\[\\]·]", "")
                .replaceAll("[^가-힣a-z0-9]", "");
    }
}
