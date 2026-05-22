package mamokey.mom_med.backend.external.mfds;

import java.util.List;

/**
 * 식약처 제품 허가정보 목록 API의 정리된 응답입니다.
 */
public record MfdsDrugListResponse(
		int totalCount,
		List<MfdsDrugListItem> items
) {
}
