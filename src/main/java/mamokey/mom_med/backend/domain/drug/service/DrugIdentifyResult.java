package mamokey.mom_med.backend.domain.drug.service;

import mamokey.mom_med.backend.domain.drug.dto.DrugCandidatesResponse;
import mamokey.mom_med.backend.domain.drug.dto.DrugIdentifyResponse;
import mamokey.mom_med.backend.domain.drug.dto.DrugNotFoundResponse;

/**
 * 약 식별 결과를 HTTP와 분리해서 표현하는 서비스 계층 결과 타입입니다.
 *
 * <p>서비스는 "성공/동명이품/없음"이라는 비즈니스 결과만 반환하고,
 * Controller가 이를 200/300/404 상태코드로 변환합니다. 이렇게 나누면 서비스 테스트가
 * 웹 계층에 묶이지 않고, 후속 Slice 04에서 내부 호출로 재사용하기도 쉽습니다.</p>
 */
public sealed interface DrugIdentifyResult permits
		DrugIdentifyResult.Identified,
		DrugIdentifyResult.Candidates,
		DrugIdentifyResult.NotFound {

	record Identified(DrugIdentifyResponse response) implements DrugIdentifyResult {
	}

	record Candidates(DrugCandidatesResponse response) implements DrugIdentifyResult {
	}

	record NotFound(DrugNotFoundResponse response) implements DrugIdentifyResult {
	}
}
