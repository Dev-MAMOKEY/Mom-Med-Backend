package mamokey.mom_med.backend.parent.dto;

import java.util.List;

/**
 * 기저질환 목록 응답 DTO.
 *
 * <p>active: 현재 활성 기저질환 (deleted_at IS NULL)
 * history: soft delete된 이력 (deleted_at IS NOT NULL)</p>
 */
public record ConditionListResponse(
        List<ConditionResponse> active,
        List<ConditionResponse> history
) {
}
