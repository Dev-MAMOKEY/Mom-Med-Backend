package mamokey.mom_med.backend.parent.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
 * <pre>
 * POST /v1/parents/{parentId}/emergency-card/regenerate — 재발급 (qr_token 교체)
 * POST /v1/parents/{parentId}/emergency-card/revoke     — 즉시 무효화
 * </pre>
 */
@RestController
@RequestMapping("/v1/parents/{parentId}/emergency-card")
@RequiredArgsConstructor
@Tag(name = "EmergencyCard", description = "응급카드 QR 관리 (Slice 08)")
public class EmergencyCardController {

    private final EmergencyCardService emergencyCardService;

    @GetMapping
    @Operation(
            summary = "응급카드 현재 상태 조회",
            description = "현재 발급된 응급카드의 토큰·URL·유효기간을 반환합니다. snapshot 내용은 포함되지 않습니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "카드 정보 반환"),
            @ApiResponse(responseCode = "404", description = "발급된 응급카드 없음")
    })
    public ResponseEntity<RsData<EmergencyCardResponse>> getCard(
            @Parameter(description = "부모 프로필 UUID", required = true)
            @PathVariable UUID parentId
    ) {
        return ResponseEntity.ok(RsData.ok(emergencyCardService.getCard(parentId)));
    }

    @PostMapping("/regenerate")
    @Operation(
            summary = "응급카드 발급 / 재발급",
            description = """
                    카드가 없으면 최초 발급, 있으면 qr_token을 새로 교체합니다.
                    발급 시점의 약장·알레르기·기저질환·병원·약국 정보를 snapshot으로 저장합니다.
                    유효 기간은 발급일로부터 90일이며, 재발급 시 초기화됩니다.
                    이전 qr_token은 즉시 무효화됩니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "발급 성공 — qrToken, qrUrl, validUntil 반환"),
            @ApiResponse(responseCode = "404", description = "부모 프로필 없음")
    })
    public ResponseEntity<RsData<EmergencyCardResponse>> regenerate(
            @Parameter(description = "부모 프로필 UUID", required = true)
            @PathVariable UUID parentId
    ) {
        EmergencyCardResponse response = emergencyCardService.regenerate(parentId);
        return ResponseEntity.ok(RsData.ok(response));
    }

    @PostMapping("/revoke")
    @Operation(
            summary = "응급카드 즉시 무효화",
            description = """
                    현재 qr_token을 즉시 무효화합니다.
                    무효화 후 GET /em/{token} 접근 시 410 Gone이 반환됩니다.
                    재사용하려면 /regenerate로 새로 발급해야 합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "무효화 성공"),
            @ApiResponse(responseCode = "404", description = "발급된 응급카드 없음")
    })
    public ResponseEntity<Void> revoke(
            @Parameter(description = "부모 프로필 UUID", required = true)
            @PathVariable UUID parentId
    ) {
        emergencyCardService.revoke(parentId);
        return ResponseEntity.noContent().build();
    }
}
