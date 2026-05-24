package mamokey.mom_med.backend.parent.dto;

import java.util.List;

/**
 * GET /v1/parents/{parentId}/medications 응답 바디.
 *
 * <p>active: 현재 복용 중인 약 목록 (deleted_at IS NULL)
 * history: 삭제된 약 목록 (이력, deleted_at IS NOT NULL)</p>
 */
public record MedicationListResponse(
        List<MedicationResponse> active,
        List<MedicationResponse> history
) {
}
