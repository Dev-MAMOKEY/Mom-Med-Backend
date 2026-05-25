package mamokey.mom_med.backend.parent.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mamokey.mom_med.backend.global.rsdata.RsData;
import mamokey.mom_med.backend.parent.dto.AddPharmacyRequest;
import mamokey.mom_med.backend.parent.dto.PharmacyListResponse;
import mamokey.mom_med.backend.parent.dto.PharmacyResponse;
import mamokey.mom_med.backend.parent.service.PharmacyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 부모 단골 약국 CRUD API (Slice 08).
 *
 * <pre>
 * POST   /v1/parents/{parentId}/pharmacies         — 약국 등록 (HIRA 검색)
 * GET    /v1/parents/{parentId}/pharmacies         — 약국 목록 (단골 우선, 방문 횟수 순)
 * DELETE /v1/parents/{parentId}/pharmacies/{id}   — 약국 삭제
 * </pre>
 */
@RestController
@RequestMapping("/v1/parents/{parentId}/pharmacies")
@RequiredArgsConstructor
@Tag(name = "Pharmacy", description = "부모 단골 약국 관리 (Slice 08)")
public class PharmacyController {

    private final PharmacyService pharmacyService;

    @PostMapping
    @Operation(
            summary = "약국 등록",
            description = """
                    yadmNm(약국명) 또는 ykiho(요양기관기호)로 HIRA에서 공식 정보를 조회해 저장합니다.
                    등록 완료 후 응급카드 snapshot이 자동 갱신됩니다.
                    같은 약국을 중복 등록하면 400을 반환합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "약국 등록 성공"),
            @ApiResponse(responseCode = "400", description = "ykiho·yadmNm 모두 미입력 또는 이미 등록된 약국"),
            @ApiResponse(responseCode = "404", description = "HIRA에서 해당 약국을 찾을 수 없음"),
            @ApiResponse(responseCode = "502", description = "HIRA API 호출 실패")
    })
    public ResponseEntity<RsData<PharmacyResponse>> addPharmacy(
            @Parameter(description = "부모 프로필 UUID", required = true)
            @PathVariable UUID parentId,
            @Valid @RequestBody AddPharmacyRequest request
    ) {
        PharmacyResponse response = pharmacyService.addPharmacy(parentId, request);
        return ResponseEntity.status(201).body(RsData.created(response));
    }

    @GetMapping
    @Operation(
            summary = "약국 목록 조회",
            description = "단골(is_regular=true) 약국이 먼저 정렬되고, 그 다음 방문 횟수 내림차순으로 반환됩니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "목록 반환 (없으면 빈 배열)"),
            @ApiResponse(responseCode = "404", description = "부모 프로필 없음")
    })
    public ResponseEntity<RsData<PharmacyListResponse>> listPharmacies(
            @Parameter(description = "부모 프로필 UUID", required = true)
            @PathVariable UUID parentId
    ) {
        return ResponseEntity.ok(RsData.ok(pharmacyService.listPharmacies(parentId)));
    }

    @PostMapping("/{pharmacyId}/visit")
    @Operation(
            summary = "약국 방문 기록",
            description = "방문 횟수(visit_count)를 1 증가시키고 최근 방문일(last_visited)을 오늘로 갱신합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "방문 기록 성공"),
            @ApiResponse(responseCode = "404", description = "약국 정보 없음 또는 다른 부모 소유")
    })
    public ResponseEntity<RsData<PharmacyResponse>> recordVisit(
            @Parameter(description = "부모 프로필 UUID", required = true)
            @PathVariable UUID parentId,
            @Parameter(description = "약국 레코드 ID", required = true)
            @PathVariable Long pharmacyId
    ) {
        return ResponseEntity.ok(RsData.ok(pharmacyService.recordVisit(parentId, pharmacyId)));
    }

    @PostMapping("/{pharmacyId}/regular")
    @Operation(
            summary = "약국 단골 여부 수정",
            description = "단골 여부(is_regular)를 변경합니다. 단골 약국은 목록 최상단에 표시됩니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "404", description = "약국 정보 없음 또는 다른 부모 소유")
    })
    public ResponseEntity<RsData<PharmacyResponse>> updateRegular(
            @Parameter(description = "부모 프로필 UUID", required = true)
            @PathVariable UUID parentId,
            @Parameter(description = "약국 레코드 ID", required = true)
            @PathVariable Long pharmacyId,
            @RequestBody java.util.Map<String, Boolean> body
    ) {
        boolean regular = Boolean.TRUE.equals(body.get("regular"));
        return ResponseEntity.ok(RsData.ok(pharmacyService.updateRegular(parentId, pharmacyId, regular)));
    }

    @DeleteMapping("/{pharmacyId}")
    @Operation(
            summary = "약국 삭제",
            description = "약국을 목록에서 제거합니다. 응급카드 snapshot이 자동 갱신됩니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "삭제 성공"),
            @ApiResponse(responseCode = "404", description = "약국 정보 없음 또는 다른 부모 소유")
    })
    public ResponseEntity<Void> deletePharmacy(
            @Parameter(description = "부모 프로필 UUID", required = true)
            @PathVariable UUID parentId,
            @Parameter(description = "약국 레코드 ID (parent_pharmacies.id)", required = true)
            @PathVariable Long pharmacyId
    ) {
        pharmacyService.deletePharmacy(parentId, pharmacyId);
        return ResponseEntity.noContent().build();
    }
}
