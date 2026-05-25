package mamokey.mom_med.backend.parent.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import mamokey.mom_med.backend.external.hira.HiraHospitalClient;
import mamokey.mom_med.backend.external.hira.HiraHospitalItem;
import mamokey.mom_med.backend.external.hira.HiraPharmacyClient;
import mamokey.mom_med.backend.external.hira.HiraPharmacyItem;
import mamokey.mom_med.backend.global.rsdata.RsData;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * HIRA 병원·약국 검색 전용 API (Slice 08).
 *
 * <p>등록 전에 목록을 확인하고 선택할 수 있도록 제공합니다.
 * 실제 저장은 POST /v1/parents/{id}/hospitals or /pharmacies에서 합니다.</p>
 */
@RestController
@RequestMapping("/v1/hira")
@RequiredArgsConstructor
@Tag(name = "Hospital", description = "부모 단골 병원 관리 (Slice 08)")
public class HiraSearchController {

    private final HiraHospitalClient hiraHospitalClient;
    private final HiraPharmacyClient hiraPharmacyClient;

    @GetMapping("/hospitals")
    @Operation(
            summary = "HIRA 병원 검색",
            description = """
                    HIRA 병원정보서비스(sno 11999)에서 병원을 검색합니다.
                    등록 전에 목록을 확인하고 ykiho를 선택하는 용도로 사용합니다.
                    공식 기관명으로 검색해야 합니다 (예: 세브란스병원, 서울대학교병원).
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "검색 결과 반환 (없으면 빈 배열)"),
            @ApiResponse(responseCode = "502", description = "HIRA API 호출 실패")
    })
    public ResponseEntity<RsData<List<HiraHospitalItem>>> searchHospitals(
            @Parameter(description = "병원명 검색어 (예: 세브란스병원)", required = true, example = "세브란스병원")
            @RequestParam String q,
            @Parameter(description = "최대 결과 수 (기본값 10, 최대 20)", example = "10")
            @RequestParam(defaultValue = "10") int maxRows
    ) {
        int limit = Math.min(maxRows, 20);
        List<HiraHospitalItem> items = hiraHospitalClient.searchByName(q, limit);
        return ResponseEntity.ok(RsData.ok(items));
    }

    @GetMapping("/pharmacies")
    @Operation(
            summary = "HIRA 약국 검색",
            description = """
                    HIRA 약국정보서비스(sno 12100)에서 약국을 검색합니다.
                    등록 전에 목록을 확인하고 ykiho를 선택하는 용도로 사용합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "검색 결과 반환 (없으면 빈 배열)"),
            @ApiResponse(responseCode = "502", description = "HIRA API 호출 실패")
    })
    public ResponseEntity<RsData<List<HiraPharmacyItem>>> searchPharmacies(
            @Parameter(description = "약국명 검색어 (예: 온누리약국)", required = true, example = "온누리약국")
            @RequestParam String q,
            @Parameter(description = "최대 결과 수 (기본값 10, 최대 20)", example = "10")
            @RequestParam(defaultValue = "10") int maxRows
    ) {
        int limit = Math.min(maxRows, 20);
        List<HiraPharmacyItem> items = hiraPharmacyClient.searchByName(q, limit);
        return ResponseEntity.ok(RsData.ok(items));
    }
}
