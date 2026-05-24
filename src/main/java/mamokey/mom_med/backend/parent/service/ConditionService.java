package mamokey.mom_med.backend.parent.service;

import lombok.RequiredArgsConstructor;
import mamokey.mom_med.backend.external.hira.HiraClient;
import mamokey.mom_med.backend.external.hira.HiraDiseaseItem;
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
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ConditionService {

    private final PatientConditionRepository conditionRepository;
    private final ParentService parentService;
    private final HiraClient hiraClient;

    // ─── 조회 ─────────────────────────────────────────────────────────────

    public ConditionListResponse listConditions(UUID parentId) {
        parentService.findOrThrow(parentId);

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
     *   <li>kcdCode가 있으면 HIRA API로 유효성 검증 + 공식 한글명 조회</li>
     *   <li>동일 질환명 중복 등록 방지</li>
     *   <li>condition_norm: 질환명 소문자·공백 제거 정규화</li>
     *   <li>저장 후 ConditionResponse 반환</li>
     * </ol>
     */
    @Transactional
    public ConditionResponse addCondition(UUID parentId, CreateConditionRequest req) {
        parentService.findOrThrow(parentId);

        // kcdCode가 제공된 경우 HIRA API로 검증하고 공식 질병명 사용
        String conditionName = req.conditionName();
        String kcdCode = req.kcdCode();

        if (kcdCode != null && !kcdCode.isBlank()) {
            List<HiraDiseaseItem> items = hiraClient.searchByCode(kcdCode);
            if (items.isEmpty()) {
                throw new CustomException(ErrorCode.DISEASE_NOT_FOUND,
                        "HIRA에서 해당 KCD 코드를 찾을 수 없습니다: " + kcdCode);
            }
            // conditionName이 비어 있으면 HIRA 공식 한글명으로 자동 채움
            HiraDiseaseItem official = items.getFirst();
            if (conditionName == null || conditionName.isBlank()) {
                conditionName = official.sickNm();
            }
        }

        // 중복 등록 방지
        if (conditionRepository.existsByParentIdAndConditionNameAndDeletedAtIsNull(
                parentId, conditionName)) {
            throw new CustomException(ErrorCode.DUPLICATE_CONDITION);
        }

        // condition_norm: 정규화 (소문자 변환 + 공백·특수문자 제거)
        String conditionNorm = normalizeConditionName(conditionName);

        PatientCondition condition = PatientCondition.create(
                parentId,
                conditionName,
                conditionNorm,
                kcdCode,
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
