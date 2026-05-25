package mamokey.mom_med.backend.parent.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import mamokey.mom_med.backend.global.rsdata.RsData;
import mamokey.mom_med.backend.parent.dto.EmergencyCardResponse;
import mamokey.mom_med.backend.parent.service.EmergencyCardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 응급카드 관리 API — 인증 필요 (Slice 08).
 *
 * <p>POST /v1/parents/{parentId}/emergency-card/regenerate — 재발급 (qr_token 교체)
 * POST /v1/parents/{parentId}/emergency-card/revoke     — 즉시 무효화</p>
 */
@RestController
@RequestMapping("/v1/parents/{parentId}/emergency-card")
@RequiredArgsConstructor
@Tag(name = "EmergencyCard", description = "응급카드 관리")
public class EmergencyCardController {

    private final EmergencyCardService emergencyCardService;

    @PostMapping("/regenerate")
    @Operation(summary = "응급카드 재발급 (새 QR 토큰 발급, 기존 즉시 무효)")
    public ResponseEntity<RsData<EmergencyCardResponse>> regenerate(
            @PathVariable UUID parentId
    ) {
        EmergencyCardResponse response = emergencyCardService.regenerate(parentId);
        return ResponseEntity.ok(RsData.ok(response));
    }

    @PostMapping("/revoke")
    @Operation(summary = "응급카드 즉시 무효화")
    public ResponseEntity<Void> revoke(
            @PathVariable UUID parentId
    ) {
        emergencyCardService.revoke(parentId);
        return ResponseEntity.noContent().build();
    }
}
