package mamokey.mom_med.backend.parent.service;

import lombok.RequiredArgsConstructor;
import mamokey.mom_med.backend.global.exception.CustomException;
import mamokey.mom_med.backend.global.exception.ErrorCode;
import mamokey.mom_med.backend.parent.domain.DeviceToken;
import mamokey.mom_med.backend.parent.dto.DeviceTokenResponse;
import mamokey.mom_med.backend.parent.dto.RegisterDeviceTokenRequest;
import mamokey.mom_med.backend.parent.repository.DeviceTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 디바이스 토큰 서비스.
 *
 * <p>토큰 저장 시 pgp_sym_encrypt를 사용하여 plain token이 DB에 절대 저장되지 않도록 합니다.
 * NamedParameterJdbcTemplate으로 RETURNING id를 받아 처리합니다.</p>
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DeviceTokenService {

    private final DeviceTokenRepository tokenRepository;
    private final ParentService parentService;
    private final NamedParameterJdbcTemplate namedJdbc;

    @Value("${app.encryption-key:}")
    private String encryptionKey;

    // ─── 조회 ─────────────────────────────────────────────────────────────

    public List<DeviceTokenResponse> listTokens(UUID parentId) {
        parentService.findOrThrow(parentId);
        return tokenRepository.findByParentIdAndRevokedAtIsNull(parentId)
                .stream()
                .map(DeviceTokenResponse::from)
                .toList();
    }

    // ─── 등록 ─────────────────────────────────────────────────────────────

    /**
     * 디바이스 토큰 등록.
     * pgp_sym_encrypt로 암호화하여 token_encrypted(BYTEA)에 저장합니다.
     * plain token은 DB에 절대 노출되지 않습니다.
     */
    @Transactional
    public DeviceTokenResponse registerToken(UUID parentId, RegisterDeviceTokenRequest req) {
        parentService.findOrThrow(parentId);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("parentId", parentId)
                .addValue("platform", req.platform())
                .addValue("token", req.token())
                .addValue("encKey", encryptionKey);

        namedJdbc.update("""
                INSERT INTO app.device_tokens (parent_id, platform, token_encrypted, created_at)
                VALUES (:parentId, :platform, pgp_sym_encrypt(:token, :encKey), NOW())
                """, params, keyHolder, new String[]{"id"});

        Long newId = keyHolder.getKey() != null ? keyHolder.getKey().longValue() : null;
        if (newId == null) {
            throw new IllegalStateException("device_tokens INSERT 후 id를 얻지 못했습니다.");
        }

        DeviceToken saved = tokenRepository.findById(newId)
                .orElseThrow(() -> new IllegalStateException("방금 저장한 device_token을 찾을 수 없습니다."));
        return DeviceTokenResponse.from(saved);
    }

    // ─── 취소 ─────────────────────────────────────────────────────────────

    /**
     * 디바이스 토큰 취소 (revoked_at = NOW()).
     */
    @Transactional
    public void revokeToken(UUID parentId, Long tokenId) {
        DeviceToken token = tokenRepository
                .findByIdAndParentId(tokenId, parentId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));

        if (token.isRevoked()) {
            throw new CustomException(ErrorCode.NOT_FOUND);
        }

        token.revoke();
    }
}
