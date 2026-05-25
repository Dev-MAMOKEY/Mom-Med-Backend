package mamokey.mom_med.backend.parent.service;

import lombok.RequiredArgsConstructor;
import mamokey.mom_med.backend.external.hira.HiraHospitalClient;
import mamokey.mom_med.backend.external.hira.HiraHospitalDetailClient;
import mamokey.mom_med.backend.external.hira.HiraHospitalItem;
import mamokey.mom_med.backend.global.exception.CustomException;
import mamokey.mom_med.backend.global.exception.ErrorCode;
import mamokey.mom_med.backend.parent.domain.HospitalEmergencyInfo;
import mamokey.mom_med.backend.parent.domain.ParentHospital;
import mamokey.mom_med.backend.parent.dto.AddHospitalRequest;
import mamokey.mom_med.backend.parent.dto.HospitalListResponse;
import mamokey.mom_med.backend.parent.dto.HospitalResponse;
import mamokey.mom_med.backend.parent.repository.HospitalEmergencyInfoRepository;
import mamokey.mom_med.backend.parent.repository.ParentHospitalRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import mamokey.mom_med.backend.parent.event.ParentDataChangedEvent;

import java.util.List;
import java.util.UUID;

/**
 * 부모 병원 CRUD 서비스 (Slice 08).
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class HospitalService {

    private final ParentHospitalRepository hospitalRepository;
    private final HospitalEmergencyInfoRepository erInfoRepository;
    private final HiraHospitalClient hiraHospitalClient;
    private final HiraHospitalDetailClient hiraHospitalDetailClient;
    private final ParentService parentService;
    private final ApplicationEventPublisher eventPublisher;

    // ─── 조회 ─────────────────────────────────────────────────────────────

    public HospitalListResponse listHospitals(UUID parentId) {
        parentService.findOrThrow(parentId);

        List<HospitalResponse> hospitals = hospitalRepository
                .findByParentIdOrderByRegularDescLastVisitedDesc(parentId)
                .stream()
                .map(h -> HospitalResponse.from(h, erInfoRepository.findById(h.getYkiho()).orElse(null)))
                .toList();

        return new HospitalListResponse(hospitals);
    }

    // ─── 등록 ─────────────────────────────────────────────────────────────

    /**
     * 병원 등록.
     *
     * <ol>
     *   <li>ykiho가 있으면 HIRA 단건 조회, 없으면 yadmNm으로 검색 후 첫 결과 사용</li>
     *   <li>HIRA 상세 API로 응급실 정보 조회 → hospital_emergency_info upsert</li>
     *   <li>parent_hospitals에 저장</li>
     *   <li>응급카드 snapshot 갱신 이벤트 발행</li>
     * </ol>
     */
    @Transactional
    public HospitalResponse addHospital(UUID parentId, AddHospitalRequest request) {
        parentService.findOrThrow(parentId);

        if ((request.ykiho() == null || request.ykiho().isBlank())
                && (request.yadmNm() == null || request.yadmNm().isBlank())) {
            throw new CustomException(ErrorCode.INVALID_INPUT, "ykiho 또는 yadmNm 중 하나는 필수입니다.");
        }

        // HIRA에서 병원 정보 조회
        HiraHospitalItem hiraItem = resolveHospital(request);

        // 중복 등록 방지
        if (hospitalRepository.existsByParentIdAndYkiho(parentId, hiraItem.ykiho())) {
            throw new CustomException(ErrorCode.INVALID_INPUT, "이미 등록된 병원입니다.");
        }

        // 응급실 상세 정보 upsert (캐시)
        HospitalEmergencyInfo erInfo = upsertEmergencyInfo(hiraItem.ykiho());

        // 병원 저장
        ParentHospital hospital = ParentHospital.create(
                parentId,
                hiraItem.ykiho(),
                hiraItem.yadmNm(),
                hiraItem.clCdNm(),
                hiraItem.addr(),
                hiraItem.telno(),
                hiraItem.xPos(),
                hiraItem.yPos(),
                request.regular(),
                request.addedBy() != null ? request.addedBy() : "child"
        );
        hospitalRepository.save(hospital);

        // snapshot 갱신 이벤트
        eventPublisher.publishEvent(new ParentDataChangedEvent(parentId));

        return HospitalResponse.from(hospital, erInfo);
    }

    // ─── 삭제 ─────────────────────────────────────────────────────────────

    @Transactional
    public void deleteHospital(UUID parentId, Long hospitalId) {
        ParentHospital hospital = hospitalRepository
                .findByIdAndParentId(hospitalId, parentId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "병원 정보를 찾을 수 없습니다."));

        hospitalRepository.delete(hospital);
        eventPublisher.publishEvent(new ParentDataChangedEvent(parentId));
    }

    // ─── 단골 토글 ────────────────────────────────────────────────────────

    @Transactional
    public HospitalResponse updateRegular(UUID parentId, Long hospitalId, boolean regular) {
        ParentHospital hospital = hospitalRepository
                .findByIdAndParentId(hospitalId, parentId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "병원 정보를 찾을 수 없습니다."));

        hospital.markRegular(regular);
        eventPublisher.publishEvent(new ParentDataChangedEvent(parentId));
        return HospitalResponse.from(hospital,
                erInfoRepository.findById(hospital.getYkiho()).orElse(null));
    }

    // ─── 내부 유틸 ────────────────────────────────────────────────────────

    private HiraHospitalItem resolveHospital(AddHospitalRequest request) {
        List<HiraHospitalItem> items;
        if (request.ykiho() != null && !request.ykiho().isBlank()) {
            items = hiraHospitalClient.searchByYkiho(request.ykiho());
        } else {
            items = hiraHospitalClient.searchByName(request.yadmNm(), 10);
        }
        if (items.isEmpty()) {
            throw new CustomException(ErrorCode.NOT_FOUND, "HIRA에서 해당 병원을 찾을 수 없습니다.");
        }
        return items.getFirst();
    }

    private HospitalEmergencyInfo upsertEmergencyInfo(String ykiho) {
        return erInfoRepository.findById(ykiho)
                .map(existing -> {
                    hiraHospitalDetailClient.fetchDetail(ykiho).ifPresent(detail ->
                            existing.refresh(
                                    detail.nightErAvailable(),
                                    detail.nightErPhone1(),
                                    detail.nightErPhone2(),
                                    detail.dayErAvailable(),
                                    detail.dayErPhone1(),
                                    detail.dayErPhone2(),
                                    detail.weeklyHoursJson()
                            )
                    );
                    return existing;
                })
                .orElseGet(() -> {
                    HospitalEmergencyInfo info = hiraHospitalDetailClient.fetchDetail(ykiho)
                            .map(d -> HospitalEmergencyInfo.create(ykiho, null,
                                    d.nightErAvailable(), d.nightErPhone1(), d.nightErPhone2(),
                                    d.dayErAvailable(), d.dayErPhone1(), d.dayErPhone2(),
                                    d.weeklyHoursJson(), null, null))
                            .orElseGet(() -> HospitalEmergencyInfo.create(
                                    ykiho, null, null, null, null, null, null, null, null, null, null));
                    return erInfoRepository.save(info);
                });
    }
}
