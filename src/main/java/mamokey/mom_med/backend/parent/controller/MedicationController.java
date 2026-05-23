package mamokey.mom_med.backend.parent.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mamokey.mom_med.backend.global.rsdata.RsData;
import mamokey.mom_med.backend.parent.dto.AddMedicationRequest;
import mamokey.mom_med.backend.parent.dto.MedicationListResponse;
import mamokey.mom_med.backend.parent.dto.MedicationResponse;
import mamokey.mom_med.backend.parent.service.MedicationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 부모 약장 API (Slice 04).
 *
 * <pre>
 * POST   /v1/parents/{parentId}/medications          — 약 추가 (DUR 안전검사 포함)
 * GET    /v1/parents/{parentId}/medications          — 약장 목록 (복용중 + 이력)
 * DELETE /v1/parents/{parentId}/medications/{medId} — 약 삭제 (soft delete)
 * </pre>
 *
 * <p>POST 응답 코드:
 * <ul>
 *   <li>201: 약 추가 성공 (ALLOW 또는 WARN)</li>
 *   <li>404: 약 마스터에 없는 품목기준코드</li>
 *   <li>409 (error=duplicate): 이미 복용 중인 약</li>
 *   <li>409 (error=block): DUR 병용금기 차단 — verdict 포함</li>
 * </ul></p>
 */
@RestController
@RequestMapping("/v1/parents/{parentId}/medications")
@RequiredArgsConstructor
@Tag(name = "Medication", description = "부모 약장 관리")
public class MedicationController {

    private final MedicationService medicationService;

    @PostMapping
    @Operation(summary = "약 추가 (DUR 병용금기 안전검사 포함)",
               description = "BLOCK 판정 시 HTTP 409, error='block', verdict 포함 응답")
    public ResponseEntity<RsData<MedicationResponse>> addMedication(
            @PathVariable UUID parentId,
            @Valid @RequestBody AddMedicationRequest request
    ) {
        MedicationResponse response = medicationService.addMedication(parentId, request);
        return ResponseEntity.status(201).body(RsData.created(response));
    }

    @GetMapping
    @Operation(summary = "약장 목록 조회 (복용중 + 이력)")
    public ResponseEntity<RsData<MedicationListResponse>> listMedications(
            @PathVariable UUID parentId
    ) {
        MedicationListResponse response = medicationService.listMedications(parentId);
        return ResponseEntity.ok(RsData.ok(response));
    }

    @DeleteMapping("/{medicationId}")
    @Operation(summary = "약 삭제 (soft delete — 이력 보존)")
    public ResponseEntity<Void> deleteMedication(
            @PathVariable UUID parentId,
            @PathVariable Long medicationId
    ) {
        medicationService.deleteMedication(parentId, medicationId);
        return ResponseEntity.noContent().build();
    }
}
