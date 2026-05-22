package mamokey.mom_med.backend.domain.drug.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;

import mamokey.mom_med.backend.domain.drug.dto.DrugCandidateResponse;
import mamokey.mom_med.backend.domain.drug.dto.DrugCandidatesResponse;
import mamokey.mom_med.backend.domain.drug.dto.DrugIdentifyResponse;
import mamokey.mom_med.backend.domain.drug.dto.DrugNotFoundResponse;
import mamokey.mom_med.backend.domain.drug.service.DrugIdentifyResult;
import mamokey.mom_med.backend.domain.drug.service.DrugIdentifyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * DrugController가 서비스 결과를 올바른 HTTP 상태코드로 바꾸는지 확인합니다.
 *
 * <p>외부 API나 DB는 서비스 테스트에서 검증하고, 여기서는 웹 계층의 얇은 책임인
 * status mapping만 빠르게 확인합니다.</p>
 */
@ExtendWith(MockitoExtension.class)
class DrugControllerTest {

	@Mock
	DrugIdentifyService drugIdentifyService;

	@InjectMocks
	DrugController drugController;

	@Test
	void returns200WhenDrugIsIdentified() {
		when(drugIdentifyService.identify("타이레놀")).thenReturn(new DrugIdentifyResult.Identified(
				new DrugIdentifyResponse("202106092", "타이레놀정500밀리그람(아세트아미노펜)",
						"Acetaminophen", "acetaminophen", "N02BE01", null, "일반의약품", null)
		));

		ResponseEntity<?> response = drugController.identify("타이레놀");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	@Test
	void returns300WhenDrugNameIsAmbiguous() {
		when(drugIdentifyService.identify("노바스크")).thenReturn(new DrugIdentifyResult.Candidates(
				new DrugCandidatesResponse(List.of(
						new DrugCandidateResponse("200610660", "노바스크정5밀리그람", "한국화이자", "전문의약품", "073400360")
				))
		));

		ResponseEntity<?> response = drugController.identify("노바스크");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.MULTIPLE_CHOICES);
	}

	@Test
	void returns404WhenDrugIsNotFound() {
		when(drugIdentifyService.identify("없는약")).thenReturn(new DrugIdentifyResult.NotFound(
				DrugNotFoundResponse.of("없는약")
		));

		ResponseEntity<?> response = drugController.identify("없는약");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}
}
