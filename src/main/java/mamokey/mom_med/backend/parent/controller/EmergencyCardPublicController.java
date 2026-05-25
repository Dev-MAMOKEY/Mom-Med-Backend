package mamokey.mom_med.backend.parent.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
@Tag(name = "EmergencyCard", description = "응급카드 QR 관리 (Slice 08)")
public class EmergencyCardPublicController {

    private final EmergencyCardService emergencyCardService;
    private final EmergencyCardRateLimiter rateLimiter;

    @GetMapping("/{token}")
    @Operation(
            summary = "응급카드 QR 공개 조회 (인증 불필요 — 응급의료진용)",
            description = """
                    QR 코드 스캔 시 접근하는 공개 엔드포인트입니다. 인증 토큰 없이 접근 가능합니다.

                    **응답 포함 정보**
                    - 환자 기본 정보 (이름, 나이, 성별, 주소)
                    - 알레르기 (severity 내림차순)
                    - 항응고제 경고 목록 (ATC B01A% — 수술·시술 시 출혈 위험)
                    - 복용 중인 약 전체 (항응고제 최상단)
                    - 기저질환 목록
                    - 단골 병원 (응급실 운영 여부·전화번호 포함)
                    - 단골 약국 (최대 3개)

                    **Rate Limit**
                    - IP당 10회/분
                    - 토큰당 60회/시간
                    - 토큰당 200회/일 (초과 시 자동 revoke → 이후 접근 불가)

                    **캐시 금지**: 응답 헤더에 `Cache-Control: no-store` 포함
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "snapshot JSON 반환"),
            @ApiResponse(responseCode = "410", description = "만료(90일 초과)되었거나 revoke된 토큰"),
            @ApiResponse(responseCode = "429", description = "Rate Limit 초과")
    })
    public ResponseEntity<Map<String, Object>> getByToken(
            @Parameter(description = "응급카드 QR 토큰 (regenerate 응답의 qrToken)", required = true)
            @PathVariable String token,
            HttpServletRequest request
    ) {
        String ip = resolveClientIp(request);

        if (rateLimiter.isIpLimitExceeded(ip)) {
            throw new CustomException(ErrorCode.INVALID_INPUT, "요청 횟수를 초과했습니다. 잠시 후 다시 시도해주세요.");
        }
        if (rateLimiter.isTokenHourlyLimitExceeded(token)) {
            throw new CustomException(ErrorCode.INVALID_INPUT, "토큰 시간당 요청 횟수를 초과했습니다.");
        }
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
