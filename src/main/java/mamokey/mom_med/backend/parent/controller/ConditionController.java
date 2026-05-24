package mamokey.mom_med.backend.parent.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mamokey.mom_med.backend.global.rsdata.RsData;
import mamokey.mom_med.backend.parent.dto.ConditionListResponse;
import mamokey.mom_med.backend.parent.dto.ConditionResponse;
import mamokey.mom_med.backend.parent.dto.CreateConditionRequest;
import mamokey.mom_med.backend.parent.service.ConditionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 기저질환 CRUD API (Slice 05).
 *
 * <pre>
 * POST   /v1/parents/{parentId}/conditions               — 기저질환 등록
 * GET    /v1/parents/{parentId}/conditions               — 기저질환 목록 (활성 + 이력)
 * DELETE /v1/parents/{parentId}/conditions/{conditionId} — 기저질환 삭제 (soft delete)
 * </pre>
 */
@RestController
@RequestMapping("/v1/parents/{parentId}/conditions")
@RequiredArgsConstructor
@Tag(name = "Condition", description = "부모 기저질환 관리 (Slice 05)")
public class ConditionController {

    private final ConditionService conditionService;

    @PostMapping
    @Operation(summary = "기저질환 등록",
               description = "동일 질환명 중복 등록 시 409 반환. kcd_code는 선택사항.")
    public ResponseEntity<RsData<ConditionResponse>> addCondition(
            @PathVariable UUID parentId,
            @Valid @RequestBody CreateConditionRequest request
    ) {
        ConditionResponse response = conditionService.addCondition(parentId, request);
        return ResponseEntity.status(201).body(RsData.created(response));
    }

    @GetMapping
    @Operation(summary = "기저질환 목록 조회 (활성 + 이력)")
    public ResponseEntity<RsData<ConditionListResponse>> listConditions(
            @PathVariable UUID parentId
    ) {
        ConditionListResponse response = conditionService.listConditions(parentId);
        return ResponseEntity.ok(RsData.ok(response));
    }

    @DeleteMapping("/{conditionId}")
    @Operation(summary = "기저질환 삭제 (soft delete — 이력 보존)")
    public ResponseEntity<Void> deleteCondition(
            @PathVariable UUID parentId,
            @PathVariable Long conditionId
    ) {
        conditionService.deleteCondition(parentId, conditionId);
        return ResponseEntity.noContent().build();
    }
}
