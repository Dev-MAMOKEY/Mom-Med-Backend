package mamokey.mom_med.backend.parent.dto;

import mamokey.mom_med.backend.parent.domain.ParentPharmacy;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PharmacyResponse(
        Long id,
        String ykiho,
        String yadmNm,
        String addr,
        String telno,
        BigDecimal xPos,
        BigDecimal yPos,
        boolean regular,
        int visitCount,
        LocalDate lastVisited
) {
    public static PharmacyResponse from(ParentPharmacy pharmacy) {
        return new PharmacyResponse(
                pharmacy.getId(),
                pharmacy.getYkiho(),
                pharmacy.getYadmNm(),
                pharmacy.getAddr(),
                pharmacy.getTelno(),
                pharmacy.getXPos(),
                pharmacy.getYPos(),
                pharmacy.isRegular(),
                pharmacy.getVisitCount(),
                pharmacy.getLastVisited()
        );
    }
}
