package mamokey.mom_med.backend.parent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import mamokey.mom_med.backend.global.exception.CustomException;
import mamokey.mom_med.backend.global.exception.ErrorCode;
import mamokey.mom_med.backend.parent.domain.EmergencyCard;
import mamokey.mom_med.backend.parent.dto.EmergencyCardResponse;
import mamokey.mom_med.backend.parent.event.ParentDataChangedEvent;
import mamokey.mom_med.backend.parent.repository.EmergencyCardRepository;
import mamokey.mom_med.backend.parent.repository.HospitalEmergencyInfoRepository;
import mamokey.mom_med.backend.parent.repository.ParentHospitalRepository;
import mamokey.mom_med.backend.parent.repository.ParentPharmacyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.Period;
import java.util.*;

/**
 * 응급카드 서비스 (Slice 08).
 *
 * <p>약장·알레르기·기저질환 변경 후 {@link ParentDataChangedEvent}를 수신하면
 * snapshot을 자동 갱신합니다 (qr_token 유지).
 * 자녀가 명시적으로 재발급 요청할 때만 qr_token이 교체됩니다.</p>
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class EmergencyCardService {

    private static final Logger log = LoggerFactory.getLogger(EmergencyCardService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final EmergencyCardRepository cardRepository;
    private final ParentHospitalRepository hospitalRepository;
    private final HospitalEmergencyInfoRepository erInfoRepository;
    private final ParentPharmacyRepository pharmacyRepository;
    private final ParentService parentService;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    @Value("${app.emergency-card.base-url:http://localhost:8080/em}")
    private String baseUrl;

    // ─── 조회 ─────────────────────────────────────────────────────────────

    /**
     * QR 토큰으로 snapshot을 반환합니다 (공개 — 인증 없음).
     * access_count는 DB에서 원자적으로 증가합니다.
     */
    @Transactional
    public Map<String, Object> getSnapshotByToken(String token) {
        EmergencyCard card = cardRepository.findByQrTokenAndRevokedAtIsNull(token)
                .orElseThrow(() -> new CustomException(ErrorCode.EMERGENCY_CARD_NOT_FOUND));

        if (card.isExpired()) {
            throw new CustomException(ErrorCode.EMERGENCY_CARD_EXPIRED);
        }

        cardRepository.incrementAccessCount(token);

        return parseSnapshot(card.getSnapshot());
    }

    // ─── 재발급 ───────────────────────────────────────────────────────────

    /**
     * 응급카드 재발급.
     *
     * <p>카드가 없으면 생성, 있으면 qr_token 교체 + snapshot 갱신.</p>
     */
    @Transactional
    public EmergencyCardResponse regenerate(UUID parentId) {
        parentService.findOrThrow(parentId);

        String snapshot = buildSnapshotJson(parentId);
        String newToken = generateToken();

        EmergencyCard card = cardRepository.findByParentId(parentId)
                .map(existing -> { existing.regenerate(newToken, snapshot); return existing; })
                .orElseGet(() -> EmergencyCard.create(parentId, newToken, snapshot));

        cardRepository.save(card);

        return toResponse(card);
    }

    // ─── Revoke ───────────────────────────────────────────────────────────

    @Transactional
    public void revoke(UUID parentId) {
        EmergencyCard card = cardRepository.findByParentId(parentId)
                .orElseThrow(() -> new CustomException(ErrorCode.EMERGENCY_CARD_NOT_FOUND));
        card.revoke();
    }

    /** rate limit 초과 시 토큰 자동 무효화 */
    @Transactional
    public void revokeByToken(String token) {
        cardRepository.findByQrTokenAndRevokedAtIsNull(token).ifPresent(EmergencyCard::revoke);
    }

    // ─── 자동 snapshot 갱신 (이벤트 리스너) ────────────────────────────────

    /**
     * 약장·알레르기·기저질환 변경 TX 커밋 후 snapshot 자동 갱신.
     *
     * <p>카드가 없는 부모는 아무것도 하지 않습니다.
     * snapshot 갱신 실패는 응급카드 미발급 상태와 동일하므로 예외를 삼킵니다.</p>
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onParentDataChanged(ParentDataChangedEvent event) {
        try {
            refreshSnapshotIfExists(event.parentId());
        } catch (Exception e) {
            log.warn("응급카드 snapshot 자동 갱신 실패. parentId={}", event.parentId(), e);
        }
    }

    public void refreshSnapshotIfExists(UUID parentId) {
        cardRepository.findByParentId(parentId).ifPresent(card -> {
            if (!card.isRevoked()) {
                card.refreshSnapshot(buildSnapshotJson(parentId));
                cardRepository.save(card);
            }
        });
    }

    // ─── Snapshot 빌드 ─────────────────────────────────────────────────────

    /**
     * 부모 전체 의료정보를 모아 snapshot JSON 문자열을 생성합니다.
     *
     * <p>정렬 기준:
     * <ul>
     *   <li>allergies — severity: severe(4) > moderate(3) > mild(2) > unknown(1)</li>
     *   <li>medications — ATC B01A*(항응고제) 최상단, 이후 등록일 내림차순</li>
     * </ul>
     */
    private String buildSnapshotJson(UUID parentId) {
        var profile = parentService.findOrThrow(parentId);
        int age = Period.between(profile.getBirthdate(), LocalDate.now()).getYears();

        // 1. 알레르기 (severity 내림차순)
        List<Map<String, Object>> allergies = jdbc.queryForList("""
                SELECT allergen_type, allergen_name, severity, notes
                  FROM app.patient_allergies
                 WHERE parent_id = ? AND deleted_at IS NULL
                 ORDER BY CASE severity
                   WHEN 'severe'   THEN 4
                   WHEN 'moderate' THEN 3
                   WHEN 'mild'     THEN 2
                   ELSE 1 END DESC
                """, parentId);

        // 2. 약장 (drugs_master JOIN, 항응고제 최상단)
        List<Map<String, Object>> medications = jdbc.queryForList("""
                SELECT pm.id, pm.item_seq, pm.created_at AS added_at,
                       dm.item_name, dm.main_ingr_en, dm.atc_code, dm.specialty_type,
                       (dm.atc_code LIKE 'B01A%%') AS is_anticoagulant
                  FROM app.patient_medications pm
                  JOIN ref.drugs_master dm ON pm.item_seq = dm.item_seq
                 WHERE pm.parent_id = ? AND pm.deleted_at IS NULL
                 ORDER BY (dm.atc_code LIKE 'B01A%%') DESC, pm.created_at DESC
                """, parentId);

        List<Map<String, Object>> criticalDrugs = medications.stream()
                .filter(m -> Boolean.TRUE.equals(m.get("is_anticoagulant")))
                .map(m -> Map.<String, Object>of(
                        "item_name",    m.get("item_name"),
                        "main_ingr_en", m.get("main_ingr_en"),
                        "atc_code",     m.get("atc_code"),
                        "warning",      "ANTICOAGULANT - 수술·시술 시 출혈 위험"
                ))
                .toList();

        // 3. 기저질환 (disease_master JOIN)
        List<Map<String, Object>> conditions = jdbc.queryForList("""
                SELECT pc.kcd_code, COALESCE(dm.sick_nm, pc.condition_name) AS sick_nm
                  FROM app.patient_conditions pc
                  LEFT JOIN ref.disease_master dm ON pc.kcd_code = dm.sick_cd
                 WHERE pc.parent_id = ? AND pc.deleted_at IS NULL
                """, parentId);

        // 4. 병원 (응급실 정보 포함)
        List<Map<String, Object>> hospitals = hospitalRepository
                .findByParentIdOrderByRegularDescLastVisitedDesc(parentId)
                .stream()
                .map(h -> {
                    var er = erInfoRepository.findById(h.getYkiho()).orElse(null);
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("ykiho",              h.getYkiho());
                    m.put("yadm_nm",            h.getYadmNm());
                    m.put("cl_cd_nm",           h.getClCdNm());
                    m.put("addr",               h.getAddr());
                    m.put("telno",              h.getTelno());
                    m.put("is_regular",         h.isRegular());
                    m.put("night_er_available", er != null ? er.getNightErAvailable() : null);
                    m.put("night_er_phone",     er != null ? er.getNightErPhone1()    : null);
                    m.put("day_er_available",   er != null ? er.getDayErAvailable()   : null);
                    m.put("day_er_phone",       er != null ? er.getDayErPhone1()      : null);
                    return m;
                })
                .toList();

        // 5. 약국 (최대 3개)
        List<Map<String, Object>> pharmacies = pharmacyRepository
                .findTop3ByParentIdOrderByRegularDescVisitCountDesc(parentId)
                .stream()
                .map(p -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("ykiho",      p.getYkiho());
                    m.put("yadm_nm",    p.getYadmNm());
                    m.put("addr",       p.getAddr());
                    m.put("telno",      p.getTelno());
                    m.put("is_regular", p.isRegular());
                    return m;
                })
                .toList();

        // 6. snapshot 조립
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("patient", Map.of(
                "name",    profile.getDisplayName(),
                "age",     age,
                "sex",     profile.getSex(),
                "address", buildAddress(profile.getAddressSido(), profile.getAddressSigungu())
        ));
        snapshot.put("allergies",          allergies);
        snapshot.put("critical_drugs",     criticalDrugs);
        snapshot.put("conditions",         conditions);
        snapshot.put("medications",        medications);
        snapshot.put("hospitals",          hospitals);
        snapshot.put("pharmacies",         pharmacies);
        snapshot.put("emergency_contacts", List.of());

        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("응급카드 snapshot JSON 직렬화 실패", e);
        }
    }

    // ─── 내부 유틸 ────────────────────────────────────────────────────────

    private static String generateToken() {
        byte[] bytes = new byte[36];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String buildAddress(String sido, String sigungu) {
        if (sido == null) return "";
        return sigungu != null ? sido + " " + sigungu : sido;
    }

    private Map<String, Object> parseSnapshot(String json) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = objectMapper.readValue(json, Map.class);
            return result;
        } catch (JsonProcessingException e) {
            log.error("snapshot JSON 파싱 실패", e);
            return Map.of();
        }
    }

    private EmergencyCardResponse toResponse(EmergencyCard card) {
        return new EmergencyCardResponse(
                card.getParentId(),
                card.getQrToken(),
                baseUrl + "/" + card.getQrToken(),
                card.getSnapshotAt(),
                card.getValidUntil()
        );
    }
}
