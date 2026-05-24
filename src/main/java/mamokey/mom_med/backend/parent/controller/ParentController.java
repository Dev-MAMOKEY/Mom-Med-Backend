package mamokey.mom_med.backend.parent.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mamokey.mom_med.backend.global.rsdata.RsData;
import mamokey.mom_med.backend.parent.dto.CreateParentRequest;
import mamokey.mom_med.backend.parent.dto.ParentProfileResponse;
import mamokey.mom_med.backend.parent.dto.UpdateParentRequest;
import mamokey.mom_med.backend.parent.service.ParentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 부모 프로파일 CRUD API.
 *
 * <p>POST /v1/parents         — 부모 등록
 * GET  /v1/parents/{id}       — 프로파일 조회
 * PATCH /v1/parents/{id}      — 프로파일 수정
 * DELETE /v1/parents/{id}     — 프로파일 삭제 (cascade)</p>
 */
@RestController
@RequestMapping("/v1/parents")
@RequiredArgsConstructor
@Tag(name = "Parent", description = "부모 프로파일 관리")
public class ParentController {

    private final ParentService parentService;

    @PostMapping
    @Operation(summary = "부모 프로파일 등록")
    public ResponseEntity<RsData<ParentProfileResponse>> createParent(
            @Valid @RequestBody CreateParentRequest request
    ) {
        ParentProfileResponse response = parentService.createProfile(request);
        return ResponseEntity.status(201).body(RsData.created(response));
    }

    @GetMapping("/{parentId}")
    @Operation(summary = "부모 프로파일 조회")
    public ResponseEntity<RsData<ParentProfileResponse>> getParent(
            @PathVariable UUID parentId
    ) {
        ParentProfileResponse response = parentService.getProfile(parentId);
        return ResponseEntity.ok(RsData.ok(response));
    }

    @PatchMapping("/{parentId}")
    @Operation(summary = "부모 프로파일 수정")
    public ResponseEntity<RsData<ParentProfileResponse>> updateParent(
            @PathVariable UUID parentId,
            @Valid @RequestBody UpdateParentRequest request
    ) {
        ParentProfileResponse response = parentService.updateProfile(parentId, request);
        return ResponseEntity.ok(RsData.ok(response));
    }

    @DeleteMapping("/{parentId}")
    @Operation(summary = "부모 프로파일 삭제 (연관 데이터 cascade)")
    public ResponseEntity<Void> deleteParent(
            @PathVariable UUID parentId
    ) {
        parentService.deleteProfile(parentId);
        return ResponseEntity.noContent().build();
    }
}
