package mamokey.mom_med.backend.parent.dto;

import mamokey.mom_med.backend.parent.domain.EmergencyContact;

import java.util.UUID;

public record EmergencyContactResponse(
        Long id,
        UUID parentId,
        String name,
        String relationship,
        String phone,
        int priority
) {
    public static EmergencyContactResponse from(EmergencyContact c) {
        return new EmergencyContactResponse(
                c.getId(),
                c.getParentId(),
                c.getName(),
                c.getRelationship(),
                c.getPhone(),
                c.getPriority()
        );
    }
}
