package mamokey.mom_med.backend.parent.dto;

import java.util.List;

/**
 * GET /v1/parents/{parent_id}/allergies 응답 바디.
 * active: 현재 활성 알레르기 (deleted_at IS NULL)
 * history: 삭제된 알레르기 이력 (deleted_at IS NOT NULL)
 */
public record AllergyListResponse(
        List<AllergyResponse> active,
        List<AllergyResponse> history
) {
}
