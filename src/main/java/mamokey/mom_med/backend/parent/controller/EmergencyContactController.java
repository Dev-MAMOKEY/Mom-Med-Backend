package mamokey.mom_med.backend.parent.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mamokey.mom_med.backend.global.rsdata.RsData;
import mamokey.mom_med.backend.parent.dto.CreateEmergencyContactRequest;
import mamokey.mom_med.backend.parent.dto.EmergencyContactResponse;
import mamokey.mom_med.backend.parent.service.EmergencyContactService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 비상연락처 CRUD API (Slice 08).
 *
 * <pre>
 * POST   /v1/parents/{parentId}/emergency-contacts          — 등록
 * GET    /v1/parents/{parentId}/emergency-contacts          — 목록
 * PUT    /v1/parents/{parentId}/emergency-contacts/{id}     — 수정
 * DELETE /v1/parents/{parentId}/emergency-contacts/{id}     — 삭제
 * </pre>
 */
@RestController
@RequestMapping("/v1/parents/{parentId}/emergency-contacts")
@RequiredArgsConstructor
@Tag(name = "EmergencyCard", description = "응급카드 QR 관리 (Slice 08)")
public class EmergencyContactController {

    private final EmergencyContactService contactService;

    @PostMapping
    @Operation(
            summary = "비상연락처 등록",
            description = "등록 후 응급카드 snapshot에 자동 반영됩니다. priority가 낮을수록 먼저 표시됩니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "등록 성공"),
            @ApiResponse(responseCode = "404", description = "부모 프로필 없음")
    })
    public ResponseEntity<RsData<EmergencyContactResponse>> addContact(
            @Parameter(description = "부모 프로필 UUID", required = true)
            @PathVariable UUID parentId,
            @Valid @RequestBody CreateEmergencyContactRequest request
    ) {
        EmergencyContactResponse response = contactService.addContact(parentId, request);
        return ResponseEntity.status(201).body(RsData.created(response));
    }

    @GetMapping
    @Operation(summary = "비상연락처 목록 조회", description = "priority 오름차순, 등록일 오름차순으로 반환됩니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "목록 반환 (없으면 빈 배열)"),
            @ApiResponse(responseCode = "404", description = "부모 프로필 없음")
    })
    public ResponseEntity<RsData<List<EmergencyContactResponse>>> listContacts(
            @Parameter(description = "부모 프로필 UUID", required = true)
            @PathVariable UUID parentId
    ) {
        return ResponseEntity.ok(RsData.ok(contactService.listContacts(parentId)));
    }

    @PutMapping("/{contactId}")
    @Operation(
            summary = "비상연락처 수정",
            description = "이름·관계·연락처·우선순위를 수정합니다. 수정 후 응급카드 snapshot에 자동 반영됩니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "404", description = "비상연락처 없음 또는 다른 부모 소유")
    })
    public ResponseEntity<RsData<EmergencyContactResponse>> updateContact(
            @Parameter(description = "부모 프로필 UUID", required = true)
            @PathVariable UUID parentId,
            @Parameter(description = "비상연락처 ID", required = true)
            @PathVariable Long contactId,
            @Valid @RequestBody CreateEmergencyContactRequest request
    ) {
        EmergencyContactResponse response = contactService.updateContact(parentId, contactId, request);
        return ResponseEntity.ok(RsData.ok(response));
    }

    @DeleteMapping("/{contactId}")
    @Operation(summary = "비상연락처 삭제", description = "삭제 후 응급카드 snapshot에 자동 반영됩니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "삭제 성공"),
            @ApiResponse(responseCode = "404", description = "비상연락처 없음 또는 다른 부모 소유")
    })
    public ResponseEntity<Void> deleteContact(
            @Parameter(description = "부모 프로필 UUID", required = true)
            @PathVariable UUID parentId,
            @Parameter(description = "비상연락처 ID", required = true)
            @PathVariable Long contactId
    ) {
        contactService.deleteContact(parentId, contactId);
        return ResponseEntity.noContent().build();
    }
}
