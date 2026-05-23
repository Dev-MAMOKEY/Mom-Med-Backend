package mamokey.mom_med.backend.domain.nb.service;

import java.util.List;

import mamokey.mom_med.backend.domain.nb.entity.NbExtraction;
import mamokey.mom_med.backend.domain.nb.entity.NbInteraction;

/**
 * NB 추출 실행 결과를 Controller와 테스트에서 다루기 위한 DTO입니다.
 */
public record NbExtractionResult(
		NbExtraction extraction,
		List<NbInteraction> interactions,
		boolean fromCache
) {
}
