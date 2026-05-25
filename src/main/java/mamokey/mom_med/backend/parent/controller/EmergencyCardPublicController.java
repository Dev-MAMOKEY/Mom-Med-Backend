package mamokey.mom_med.backend.parent.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import mamokey.mom_med.backend.global.exception.CustomException;
import mamokey.mom_med.backend.global.exception.ErrorCode;
import mamokey.mom_med.backend.parent.service.EmergencyCardRateLimiter;
import mamokey.mom_med.backend.parent.service.EmergencyCardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 응급카드 공개 QR 조회 엔드포인트 — 인증 없음 (Slice 08).
 *
 * <p>응급의료진이 QR 스캔 시 접근하는 URL입니다.
 * Rate Limit: IP당 10회/분 · 토큰당 60회/시 · 200회/일 (초과 시 자동 revoke)</p>
 */
@RestController
@RequestMapping("/em")
@RequiredArgsConstructor
@Tag(name = "EmergencyCard", description = "응급카드 관리")
public class EmergencyCardPublicController {

    private final EmergencyCardService emergencyCardService;
    private final EmergencyCardRateLimiter rateLimiter;

    /**
     * QR 토큰으로 snapshot을 반환합니다.
     *
     * <p>만료 → 410 Gone / 무효(revoked) → 410 Gone / rate limit → 429</p>
     */
    @GetMapping("/{token}")
    @Operation(summary = "응급카드 QR 조회 (인증 없음 — 응급의료진용, rate limit 적용)")
    public ResponseEntity<Map<String, Object>> getByToken(
            @PathVariable String token,
            HttpServletRequest request
    ) {
        String ip = resolveClientIp(request);

        // ① IP 한도
        if (rateLimiter.isIpLimitExceeded(ip)) {
            throw new CustomException(ErrorCode.INVALID_INPUT, "요청 횟수를 초과했습니다. 잠시 후 다시 시도해주세요.");
        }
        // ② 토큰 시간당 한도
        if (rateLimiter.isTokenHourlyLimitExceeded(token)) {
            throw new CustomException(ErrorCode.INVALID_INPUT, "토큰 시간당 요청 횟수를 초과했습니다.");
        }
        // ③ 토큰 일일 한도 — 초과 시 자동 revoke
        if (rateLimiter.isTokenDailyLimitExceeded(token)) {
            emergencyCardService.revokeByToken(token);
            throw new CustomException(ErrorCode.EMERGENCY_CARD_REVOKED,
                    "일일 접근 한도 초과로 응급카드가 자동 무효화되었습니다.");
        }

        Map<String, Object> snapshot = emergencyCardService.getSnapshotByToken(token);

        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .header("X-RateLimit-Policy", "IP:10/min token:60/hour token:200/day")
                .body(snapshot);
    }

    private static String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].strip();
        }
        return request.getRemoteAddr();
    }
}
