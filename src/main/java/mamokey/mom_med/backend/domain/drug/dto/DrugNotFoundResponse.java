package mamokey.mom_med.backend.domain.drug.dto;

/**
 * 식약처 검색 결과가 없을 때 반환하는 404 응답 DTO입니다.
 *
 * <p>전역 예외 포맷과 별개로 Slice 01 API 계약에서 요구한
 * {@code {"error":"drug_not_found","input":"..."}} 형태를 맞추기 위해 별도 DTO로 둡니다.</p>
 */
public record DrugNotFoundResponse(
		String error,
		String input
) {

	public static DrugNotFoundResponse of(String input) {
		return new DrugNotFoundResponse("drug_not_found", input);
	}
}
