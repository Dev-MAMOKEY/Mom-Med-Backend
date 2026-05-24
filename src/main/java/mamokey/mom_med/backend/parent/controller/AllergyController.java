package mamokey.mom_med.backend.parent.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mamokey.mom_med.backend.global.rsdata.RsData;
import mamokey.mom_med.backend.parent.dto.AllergyListResponse;
import mamokey.mom_med.backend.parent.dto.AllergyResponse;
import mamokey.mom_med.backend.parent.dto.CreateAllergyRequest;
import mamokey.mom_med.backend.parent.service.AllergyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 알레르기 CRUD API.
 *
 * <p>POST   /v1/parents/{id}/allergies       — 알레르기 등록
 * GET    /v1/parents/{id}/allergies       — 알레르기 목록 (active + history)
 * DELETE /v1/parents/{id}/allergies/{aid} — 알레르기 삭제 (soft delete)</p>
 */
@RestController
@RequestMapping("/v1/parents/{parentId}/allergies")
@RequiredArgsConstructor
@Tag(name = "Allergy", description = "부모 알레르기 관리")
public class AllergyController {

    private final AllergyService allergyService;

    @PostMapping
    @Operation(summary = "알레르기 등록 (drug 타입은 allergen_norm 자동 정규화)")
    public ResponseEntity<RsData<AllergyResponse>> createAllergy(
            @PathVariable UUID parentId,
            @Valid @RequestBody CreateAllergyRequest request
    ) {
        AllergyResponse response = allergyService.createAllergy(parentId, request);
        return ResponseEntity.status(201).body(RsData.created(response));
    }

    @GetMapping
    @Operation(summary = "알레르기 목록 조회 (활성 + 이력)")
    public ResponseEntity<RsData<AllergyListResponse>> listAllergies(
            @PathVariable UUID parentId
    ) {
        AllergyListResponse response = allergyService.listAllergies(parentId);
        return ResponseEntity.ok(RsData.ok(response));
    }

    @DeleteMapping("/{allergyId}")
    @Operation(summary = "알레르기 삭제 (soft delete — 이력 보존)")
    public ResponseEntity<Void> deleteAllergy(
            @PathVariable UUID parentId,
            @PathVariable Long allergyId
    ) {
        allergyService.deleteAllergy(parentId, allergyId);
        return ResponseEntity.noContent().build();
    }
}
