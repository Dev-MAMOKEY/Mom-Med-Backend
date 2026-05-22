package mamokey.mom_med.backend.parent.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mamokey.mom_med.backend.global.rsdata.RsData;
import mamokey.mom_med.backend.parent.dto.DeviceTokenResponse;
import mamokey.mom_med.backend.parent.dto.RegisterDeviceTokenRequest;
import mamokey.mom_med.backend.parent.service.DeviceTokenService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 디바이스 토큰 API.
 *
 * <p>POST   /v1/parents/{id}/device-tokens       — 토큰 등록 (서버에서 pgp_sym_encrypt)
 * GET    /v1/parents/{id}/device-tokens       — 활성 토큰 목록
 * DELETE /v1/parents/{id}/device-tokens/{tid} — 토큰 취소 (revoked_at = NOW())</p>
 *
 * <p>응답에는 plain token을 절대 포함하지 않습니다.</p>
 */
@RestController
@RequestMapping("/v1/parents/{parentId}/device-tokens")
@RequiredArgsConstructor
@Tag(name = "DeviceToken", description = "자녀 디바이스 토큰 관리 (Slice 07 푸시 알림용)")
public class DeviceTokenController {

    private final DeviceTokenService deviceTokenService;

    @PostMapping
    @Operation(summary = "디바이스 토큰 등록 (pgp_sym_encrypt 암호화 저장)")
    public ResponseEntity<RsData<DeviceTokenResponse>> registerToken(
            @PathVariable UUID parentId,
            @Valid @RequestBody RegisterDeviceTokenRequest request
    ) {
        DeviceTokenResponse response = deviceTokenService.registerToken(parentId, request);
        return ResponseEntity.status(201).body(RsData.created(response));
    }

    @GetMapping
    @Operation(summary = "활성 디바이스 토큰 목록 조회")
    public ResponseEntity<RsData<List<DeviceTokenResponse>>> listTokens(
            @PathVariable UUID parentId
    ) {
        List<DeviceTokenResponse> response = deviceTokenService.listTokens(parentId);
        return ResponseEntity.ok(RsData.ok(response));
    }

    @DeleteMapping("/{tokenId}")
    @Operation(summary = "디바이스 토큰 취소 (revoked_at 설정)")
    public ResponseEntity<Void> revokeToken(
            @PathVariable UUID parentId,
            @PathVariable Long tokenId
    ) {
        deviceTokenService.revokeToken(parentId, tokenId);
        return ResponseEntity.noContent().build();
    }
}
