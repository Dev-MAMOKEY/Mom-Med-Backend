package mamokey.mom_med.backend.domain.weather.controller;

import jakarta.validation.Valid;
import mamokey.mom_med.backend.domain.weather.dto.WeatherRuleAdminListResponse;
import mamokey.mom_med.backend.domain.weather.dto.WeatherRuleAdminResponse;
import mamokey.mom_med.backend.domain.weather.dto.WeatherRuleApproveRequest;
import mamokey.mom_med.backend.domain.weather.service.WeatherRuleAdminService;
import mamokey.mom_med.backend.global.rsdata.RsData;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 날씨 룰 의료진 검수 API입니다.
 */
@RestController
@RequestMapping("/v1/admin/weather-rules")
public class WeatherRuleAdminController {

	private final WeatherRuleAdminService weatherRuleAdminService;

	public WeatherRuleAdminController(WeatherRuleAdminService weatherRuleAdminService) {
		this.weatherRuleAdminService = weatherRuleAdminService;
	}

	@GetMapping
	public ResponseEntity<RsData<WeatherRuleAdminListResponse>> getRules(
			@RequestParam(required = false) String status
	) {
		return ResponseEntity.ok(RsData.ok(weatherRuleAdminService.getRules(status)));
	}

	@PostMapping("/{ruleId}/approve")
	public ResponseEntity<RsData<WeatherRuleAdminResponse>> approve(
			@PathVariable Long ruleId,
			@Valid @RequestBody WeatherRuleApproveRequest request
	) {
		return ResponseEntity.ok(RsData.ok(weatherRuleAdminService.approve(ruleId, request)));
	}
}
