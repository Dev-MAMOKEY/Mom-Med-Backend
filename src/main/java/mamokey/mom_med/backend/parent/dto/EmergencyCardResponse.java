package mamokey.mom_med.backend.parent.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 응급카드 재발급/조회 응답.
 *
 * <p>qrUrl은 프론트가 QR 이미지로 렌더링할 URL입니다.
 * snapshot은 GET /em/{token} 직접 접근 시 반환되며,
 * 이 응답에서는 메타정보만 포함합니다.</p>
 */
public record EmergencyCardResponse(
        UUID parentId,
        String qrToken,
        String qrUrl,
        LocalDateTime snapshotAt,
        LocalDateTime validUntil
) {}
