package mamokey.mom_med.backend.parent.dto;

import mamokey.mom_med.backend.parent.domain.DeviceToken;

import java.time.LocalDateTime;

/**
 * 디바이스 토큰 응답 — plain token은 절대 포함하지 않습니다.
 */
public record DeviceTokenResponse(
        Long id,
        String platform,
        LocalDateTime lastUsedAt,
        LocalDateTime createdAt
) {

    public static DeviceTokenResponse from(DeviceToken t) {
        return new DeviceTokenResponse(
                t.getId(),
                t.getPlatform(),
                t.getLastUsedAt(),
                t.getCreatedAt()
        );
    }
}
