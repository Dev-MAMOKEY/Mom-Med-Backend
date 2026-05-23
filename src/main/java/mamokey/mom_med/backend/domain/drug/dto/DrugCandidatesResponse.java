package mamokey.mom_med.backend.domain.drug.dto;

import java.util.List;

/**
 * 약 이름 검색 결과가 여러 건일 때 반환하는 300 응답 DTO입니다.
 *
 * <p>Slice 01에서는 사용자 선택 UI를 만들지 않기 때문에, 서버는 최대 5개 후보만
 * 응답하고 실제 선택 흐름은 프론트엔드 또는 후속 API에서 이어받습니다.</p>
 */
public record DrugCandidatesResponse(
		List<DrugCandidateResponse> candidates
) {
}
