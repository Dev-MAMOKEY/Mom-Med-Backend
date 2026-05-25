package mamokey.mom_med.backend.parent.controller;

import io.swagger.v3.oas.annotations.Operation;
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
 * 부모 병원 CRUD API (Slice 08).
 *
 * <p>POST /v1/parents/{parentId}/hospitals     — 병원 등록 (HIRA 검색 + 응급실 정보 캐시)
 * GET  /v1/parents/{parentId}/hospitals     — 병원 목록
 * DELETE /v1/parents/{parentId}/hospitals/{id} — 병원 삭제</p>
 */
@RestController
@RequestMapping("/v1/parents/{parentId}/hospitals")
@RequiredArgsConstructor
@Tag(name = "Hospital", description = "부모 단골 병원 관리")
public class HospitalController {

    private final HospitalService hospitalService;

    @PostMapping
    @Operation(summary = "병원 등록 (HIRA 검색 + 응급실 정보 자동 조회)")
    public ResponseEntity<RsData<HospitalResponse>> addHospital(
            @PathVariable UUID parentId,
            @Valid @RequestBody AddHospitalRequest request
    ) {
        HospitalResponse response = hospitalService.addHospital(parentId, request);
        return ResponseEntity.status(201).body(RsData.created(response));
    }

    @GetMapping
    @Operation(summary = "병원 목록 조회 (단골 우선, 최근 방문 순)")
    public ResponseEntity<RsData<HospitalListResponse>> listHospitals(
            @PathVariable UUID parentId
    ) {
        return ResponseEntity.ok(RsData.ok(hospitalService.listHospitals(parentId)));
    }

    @DeleteMapping("/{hospitalId}")
    @Operation(summary = "병원 삭제")
    public ResponseEntity<Void> deleteHospital(
            @PathVariable UUID parentId,
            @PathVariable Long hospitalId
    ) {
        hospitalService.deleteHospital(parentId, hospitalId);
        return ResponseEntity.noContent().build();
    }
}
