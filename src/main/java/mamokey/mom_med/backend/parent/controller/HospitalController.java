package mamokey.mom_med.backend.parent.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mamokey.mom_med.backend.global.rsdata.RsData;
import mamokey.mom_med.backend.parent.dto.AddHospitalRequest;
import mamokey.mom_med.backend.parent.dto.HospitalListResponse;
import mamokey.mom_med.backend.parent.dto.HospitalResponse;
import mamokey.mom_med.backend.parent.service.HospitalService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 부모 단골 병원 CRUD API (Slice 08).
 *
 * <pre>
 * POST   /v1/parents/{parentId}/hospitals          — 병원 등록 (HIRA 검색 + 응급실 정보 캐시)
 * GET    /v1/parents/{parentId}/hospitals          — 병원 목록 (단골 우선, 최근 방문 순)
 * DELETE /v1/parents/{parentId}/hospitals/{id}    — 병원 삭제
 * </pre>
 */
@RestController
@RequestMapping("/v1/parents/{parentId}/hospitals")
@RequiredArgsConstructor
@Tag(name = "Hospital", description = "부모 단골 병원 관리 (Slice 08)")
public class HospitalController {

    private final HospitalService hospitalService;

    @PostMapping
    @Operation(
            summary = "병원 등록",
            description = """
                    yadmNm(병원명) 또는 ykiho(요양기관기호) 중 하나로 HIRA에서 공식 정보를 조회해 저장합니다.
                    등록 시 응급실 운영 여부·전화번호·진료시간을 자동으로 캐시합니다 (sno 12101).
                    같은 병원을 여러 부모가 등록해도 응급실 정보는 1건만 유지됩니다.
                    등록 완료 후 응급카드 snapshot이 자동 갱신됩니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "병원 등록 성공"),
            @ApiResponse(responseCode = "400", description = "ykiho·yadmNm 모두 미입력"),
            @ApiResponse(responseCode = "404", description = "HIRA에서 해당 병원을 찾을 수 없음"),
            @ApiResponse(responseCode = "502", description = "HIRA API 호출 실패")
    })
    public ResponseEntity<RsData<HospitalResponse>> addHospital(
            @Parameter(description = "부모 프로필 UUID", required = true)
            @PathVariable UUID parentId,
            @Valid @RequestBody AddHospitalRequest request
    ) {
        HospitalResponse response = hospitalService.addHospital(parentId, request);
        return ResponseEntity.status(201).body(RsData.created(response));
    }

    @GetMapping
    @Operation(
            summary = "병원 목록 조회",
            description = "단골(is_regular=true) 병원이 먼저 정렬되고, 그 다음 최근 방문일 내림차순으로 반환됩니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "목록 반환 (없으면 빈 배열)"),
            @ApiResponse(responseCode = "404", description = "부모 프로필 없음")
    })
    public ResponseEntity<RsData<HospitalListResponse>> listHospitals(
            @Parameter(description = "부모 프로필 UUID", required = true)
            @PathVariable UUID parentId
    ) {
        return ResponseEntity.ok(RsData.ok(hospitalService.listHospitals(parentId)));
    }

    @PostMapping("/{hospitalId}/regular")
    @Operation(
            summary = "병원 단골 여부 수정",
            description = "단골 여부(is_regular)를 변경합니다. 단골 병원은 목록 최상단에 표시됩니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "404", description = "병원 정보 없음 또는 다른 부모 소유")
    })
    public ResponseEntity<RsData<HospitalResponse>> updateRegular(
            @Parameter(description = "부모 프로필 UUID", required = true)
            @PathVariable UUID parentId,
            @Parameter(description = "병원 레코드 ID", required = true)
            @PathVariable Long hospitalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "단골 여부")
            @RequestBody java.util.Map<String, Boolean> body
    ) {
        boolean regular = Boolean.TRUE.equals(body.get("regular"));
        return ResponseEntity.ok(RsData.ok(hospitalService.updateRegular(parentId, hospitalId, regular)));
    }

    @DeleteMapping("/{hospitalId}")
    @Operation(
            summary = "병원 삭제",
            description = "병원을 목록에서 제거합니다. 응급카드 snapshot이 자동 갱신됩니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "삭제 성공"),
            @ApiResponse(responseCode = "404", description = "병원 정보 없음 또는 다른 부모 소유")
    })
    public ResponseEntity<Void> deleteHospital(
            @Parameter(description = "부모 프로필 UUID", required = true)
            @PathVariable UUID parentId,
            @Parameter(description = "병원 레코드 ID (parent_hospitals.id)", required = true)
            @PathVariable Long hospitalId
    ) {
        hospitalService.deleteHospital(parentId, hospitalId);
        return ResponseEntity.noContent().build();
    }
}
