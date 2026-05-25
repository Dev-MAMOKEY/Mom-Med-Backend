package mamokey.mom_med.backend.parent.controller;

import io.swagger.v3.oas.annotations.Operation;
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
 * 부모 약국 CRUD API (Slice 08).
 *
 * <p>POST   /v1/parents/{parentId}/pharmacies      — 약국 등록
 * GET    /v1/parents/{parentId}/pharmacies      — 약국 목록
 * DELETE /v1/parents/{parentId}/pharmacies/{id} — 약국 삭제</p>
 */
@RestController
@RequestMapping("/v1/parents/{parentId}/pharmacies")
@RequiredArgsConstructor
@Tag(name = "Pharmacy", description = "부모 단골 약국 관리")
public class PharmacyController {

    private final PharmacyService pharmacyService;

    @PostMapping
    @Operation(summary = "약국 등록 (HIRA 검색)")
    public ResponseEntity<RsData<PharmacyResponse>> addPharmacy(
            @PathVariable UUID parentId,
            @Valid @RequestBody AddPharmacyRequest request
    ) {
        PharmacyResponse response = pharmacyService.addPharmacy(parentId, request);
        return ResponseEntity.status(201).body(RsData.created(response));
    }

    @GetMapping
    @Operation(summary = "약국 목록 조회 (단골 우선, 방문 횟수 순)")
    public ResponseEntity<RsData<PharmacyListResponse>> listPharmacies(
            @PathVariable UUID parentId
    ) {
        return ResponseEntity.ok(RsData.ok(pharmacyService.listPharmacies(parentId)));
    }

    @DeleteMapping("/{pharmacyId}")
    @Operation(summary = "약국 삭제")
    public ResponseEntity<Void> deletePharmacy(
            @PathVariable UUID parentId,
            @PathVariable Long pharmacyId
    ) {
        pharmacyService.deletePharmacy(parentId, pharmacyId);
        return ResponseEntity.noContent().build();
    }
}
