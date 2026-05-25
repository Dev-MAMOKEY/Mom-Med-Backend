package mamokey.mom_med.backend.parent.service;

import lombok.RequiredArgsConstructor;
import mamokey.mom_med.backend.external.hira.HiraPharmacyClient;
import mamokey.mom_med.backend.external.hira.HiraPharmacyItem;
import mamokey.mom_med.backend.global.exception.CustomException;
import mamokey.mom_med.backend.global.exception.ErrorCode;
import mamokey.mom_med.backend.parent.domain.ParentPharmacy;
import mamokey.mom_med.backend.parent.dto.AddPharmacyRequest;
import mamokey.mom_med.backend.parent.dto.PharmacyListResponse;
import mamokey.mom_med.backend.parent.dto.PharmacyResponse;
import mamokey.mom_med.backend.parent.event.ParentDataChangedEvent;
import mamokey.mom_med.backend.parent.repository.ParentPharmacyRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 부모 약국 CRUD 서비스 (Slice 08).
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PharmacyService {

    private final ParentPharmacyRepository pharmacyRepository;
    private final HiraPharmacyClient hiraPharmacyClient;
    private final ParentService parentService;
    private final ApplicationEventPublisher eventPublisher;

    // ─── 조회 ─────────────────────────────────────────────────────────────

    public PharmacyListResponse listPharmacies(UUID parentId) {
        parentService.findOrThrow(parentId);

        List<PharmacyResponse> pharmacies = pharmacyRepository
                .findByParentIdOrderByRegularDescVisitCountDesc(parentId)
                .stream()
                .map(PharmacyResponse::from)
                .toList();

        return new PharmacyListResponse(pharmacies);
    }

    // ─── 등록 ─────────────────────────────────────────────────────────────

    /**
     * 약국 등록.
     *
     * <p>ykiho 또는 yadmNm 중 하나 필수.
     * HIRA에서 조회한 정보를 그대로 저장합니다.</p>
     */
    @Transactional
    public PharmacyResponse addPharmacy(UUID parentId, AddPharmacyRequest request) {
        parentService.findOrThrow(parentId);

        if ((request.ykiho() == null || request.ykiho().isBlank())
                && (request.yadmNm() == null || request.yadmNm().isBlank())) {
            throw new CustomException(ErrorCode.INVALID_INPUT, "ykiho 또는 yadmNm 중 하나는 필수입니다.");
        }

        List<HiraPharmacyItem> items;
        if (request.ykiho() != null && !request.ykiho().isBlank()) {
            // ykiho 직접 지정: 이름 검색으로 대체 (약국 API는 ykiho 단건 조회 미지원)
            items = hiraPharmacyClient.searchByName(request.ykiho(), 1);
        } else {
            items = hiraPharmacyClient.searchByName(request.yadmNm(), 10);
        }

        if (items.isEmpty()) {
            throw new CustomException(ErrorCode.NOT_FOUND, "HIRA에서 해당 약국을 찾을 수 없습니다.");
        }

        HiraPharmacyItem hiraItem = items.getFirst();

        if (pharmacyRepository.existsByParentIdAndYkiho(parentId, hiraItem.ykiho())) {
            throw new CustomException(ErrorCode.INVALID_INPUT, "이미 등록된 약국입니다.");
        }

        ParentPharmacy pharmacy = ParentPharmacy.create(
                parentId,
                hiraItem.ykiho(),
                hiraItem.yadmNm(),
                hiraItem.addr(),
                hiraItem.telno(),
                hiraItem.xPos(),
                hiraItem.yPos(),
                request.regular()
        );
        pharmacyRepository.save(pharmacy);

        eventPublisher.publishEvent(new ParentDataChangedEvent(parentId));

        return PharmacyResponse.from(pharmacy);
    }

    // ─── 방문 기록 ────────────────────────────────────────────────────────

    @Transactional
    public PharmacyResponse recordVisit(UUID parentId, Long pharmacyId) {
        ParentPharmacy pharmacy = pharmacyRepository
                .findByIdAndParentId(pharmacyId, parentId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "약국 정보를 찾을 수 없습니다."));

        pharmacy.recordVisit();
        return PharmacyResponse.from(pharmacy);
    }

    // ─── 단골 토글 ────────────────────────────────────────────────────────

    @Transactional
    public PharmacyResponse updateRegular(UUID parentId, Long pharmacyId, boolean regular) {
        ParentPharmacy pharmacy = pharmacyRepository
                .findByIdAndParentId(pharmacyId, parentId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "약국 정보를 찾을 수 없습니다."));

        pharmacy.markRegular(regular);
        eventPublisher.publishEvent(new ParentDataChangedEvent(parentId));
        return PharmacyResponse.from(pharmacy);
    }

    // ─── 삭제 ─────────────────────────────────────────────────────────────

    @Transactional
    public void deletePharmacy(UUID parentId, Long pharmacyId) {
        ParentPharmacy pharmacy = pharmacyRepository
                .findByIdAndParentId(pharmacyId, parentId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "약국 정보를 찾을 수 없습니다."));

        pharmacyRepository.delete(pharmacy);
        eventPublisher.publishEvent(new ParentDataChangedEvent(parentId));
    }
}
