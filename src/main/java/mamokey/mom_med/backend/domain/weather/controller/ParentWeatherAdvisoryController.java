package mamokey.mom_med.backend.domain.weather.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

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
public class ParentWeatherAdvisoryController {

	private final ParentWeatherAdvisoryService parentWeatherAdvisoryService;

	public ParentWeatherAdvisoryController(ParentWeatherAdvisoryService parentWeatherAdvisoryService) {
		this.parentWeatherAdvisoryService = parentWeatherAdvisoryService;
	}

	@GetMapping
	public ResponseEntity<RsData<ParentWeatherAdvisoryResponse>> getWeatherAdvisory(
			@PathVariable UUID parentId,
			@RequestParam(required = false) LocalDate date,
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
