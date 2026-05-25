package mamokey.mom_med.backend.parent.service;

import lombok.RequiredArgsConstructor;
import mamokey.mom_med.backend.global.exception.CustomException;
import mamokey.mom_med.backend.global.exception.ErrorCode;
import mamokey.mom_med.backend.parent.domain.EmergencyContact;
import mamokey.mom_med.backend.parent.dto.CreateEmergencyContactRequest;
import mamokey.mom_med.backend.parent.dto.EmergencyContactResponse;
import mamokey.mom_med.backend.parent.event.ParentDataChangedEvent;
import mamokey.mom_med.backend.parent.repository.EmergencyContactRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 비상연락처 CRUD 서비스 (Slice 08).
 *
 * <p>등록·수정·삭제 시 응급카드 snapshot 자동 갱신을 위해
 * {@link ParentDataChangedEvent}를 발행합니다.</p>
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class EmergencyContactService {

    private final EmergencyContactRepository contactRepository;
    private final ParentService parentService;
    private final ApplicationEventPublisher eventPublisher;

    // ─── 조회 ─────────────────────────────────────────────────────────────

    public List<EmergencyContactResponse> listContacts(UUID parentId) {
        parentService.findOrThrow(parentId);
        return contactRepository
                .findByParentIdOrderByPriorityAscCreatedAtAsc(parentId)
                .stream()
                .map(EmergencyContactResponse::from)
                .toList();
    }

    // ─── 등록 ─────────────────────────────────────────────────────────────

    @Transactional
    public EmergencyContactResponse addContact(UUID parentId, CreateEmergencyContactRequest req) {
        parentService.findOrThrow(parentId);

        EmergencyContact contact = EmergencyContact.create(
                parentId,
                req.name(),
                req.relationship(),
                req.phone(),
                req.priority()
        );
        contactRepository.save(contact);
        eventPublisher.publishEvent(new ParentDataChangedEvent(parentId));
        return EmergencyContactResponse.from(contact);
    }

    // ─── 수정 ─────────────────────────────────────────────────────────────

    @Transactional
    public EmergencyContactResponse updateContact(UUID parentId, Long contactId,
                                                   CreateEmergencyContactRequest req) {
        EmergencyContact contact = contactRepository
                .findByIdAndParentId(contactId, parentId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "비상연락처를 찾을 수 없습니다."));

        contact.update(req.name(), req.relationship(), req.phone(), req.priority());
        eventPublisher.publishEvent(new ParentDataChangedEvent(parentId));
        return EmergencyContactResponse.from(contact);
    }

    // ─── 삭제 ─────────────────────────────────────────────────────────────

    @Transactional
    public void deleteContact(UUID parentId, Long contactId) {
        EmergencyContact contact = contactRepository
                .findByIdAndParentId(contactId, parentId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "비상연락처를 찾을 수 없습니다."));

        contactRepository.delete(contact);
        eventPublisher.publishEvent(new ParentDataChangedEvent(parentId));
    }
}
