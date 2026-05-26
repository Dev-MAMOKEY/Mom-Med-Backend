package mamokey.mom_med.backend.domain.weather.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import mamokey.mom_med.backend.domain.weather.dto.ParentWeatherAdvisoryResponse;
import mamokey.mom_med.backend.domain.weather.service.ParentWeatherAdvisoryService;
import mamokey.mom_med.backend.global.rsdata.RsData;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 부모 기저질환 × 활성 기상특보 룰 매칭 API입니다.
 *
 * <p>simulate_alert는 Slice 07이 아직 기상특보를 넣기 전 로컬 smoke test용 입력입니다.</p>
 */
@RestController
@RequestMapping("/v1/parents/{parentId}/weather-advisory")
@Tag(name = "Weather Advisory", description = "부모 날씨 건강 Advisory 조회 (Slice 07)")
public class ParentWeatherAdvisoryController {

	private final ParentWeatherAdvisoryService parentWeatherAdvisoryService;

	public ParentWeatherAdvisoryController(ParentWeatherAdvisoryService parentWeatherAdvisoryService) {
		this.parentWeatherAdvisoryService = parentWeatherAdvisoryService;
	}

	@GetMapping
	@Operation(
			summary = "날씨 advisory 조회",
			description = """
					부모의 기저질환(KCD 코드) × 활성 기상특보를 매칭해 advisory 목록을 반환합니다.
					- `date` 미지정 시 오늘(KST) 기준으로 조회합니다.
					- `simulate_alert` 지정 시 기상청 API 호출 없이 해당 특보를 강제 주입합니다 (로컬 smoke test용).
					  예: `simulate_alert=폭염경보&simulate_alert=한파주의보`
					- `requires_review=true` advisory는 의료진 미승인 룰로, 푸시 대상에서 제외됩니다.
					"""
	)
	public ResponseEntity<RsData<ParentWeatherAdvisoryResponse>> getWeatherAdvisory(
			@Parameter(description = "부모 UUID", example = "550e8400-e29b-41d4-a716-446655440000")
			@PathVariable UUID parentId,
			@Parameter(description = "조회 기준일 (ISO 8601, 예: 2026-05-26). 미지정 시 오늘 KST.")
			@RequestParam(required = false) LocalDate date,
			@Parameter(description = "테스트용 특보 주입 (예: 폭염경보, 한파주의보). 지정 시 KMA API 생략.")
			@RequestParam(name = "simulate_alert", required = false) List<String> simulateAlerts
	) {
		ParentWeatherAdvisoryResponse response = parentWeatherAdvisoryService.getAdvisories(
				parentId,
				date == null ? LocalDate.now() : date,
				simulateAlerts == null ? List.of() : simulateAlerts
		);
		return ResponseEntity.ok(RsData.ok(response));
	}
}
