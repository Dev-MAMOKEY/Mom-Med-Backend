package mamokey.mom_med.backend.parent.dto;

import mamokey.mom_med.backend.parent.domain.HospitalEmergencyInfo;
import mamokey.mom_med.backend.parent.domain.ParentHospital;

import java.math.BigDecimal;
import java.time.LocalDate;

public record HospitalResponse(
        Long id,
        String ykiho,
        String yadmNm,
        String clCdNm,
        String addr,
        String telno,
        BigDecimal xPos,
        BigDecimal yPos,
        boolean regular,
        LocalDate lastVisited,
        String addedBy,
        EmergencyInfo emergencyInfo
) {
    public record EmergencyInfo(
            String nightErAvailable,
            String nightErPhone1,
            String dayErAvailable,
            String dayErPhone1
    ) {
        public static EmergencyInfo from(HospitalEmergencyInfo info) {
            if (info == null) return null;
            return new EmergencyInfo(
                    info.getNightErAvailable(),
                    info.getNightErPhone1(),
                    info.getDayErAvailable(),
                    info.getDayErPhone1()
            );
        }
    }

    public static HospitalResponse from(ParentHospital hospital, HospitalEmergencyInfo erInfo) {
        return new HospitalResponse(
                hospital.getId(),
                hospital.getYkiho(),
                hospital.getYadmNm(),
                hospital.getClCdNm(),
                hospital.getAddr(),
                hospital.getTelno(),
                hospital.getXPos(),
                hospital.getYPos(),
                hospital.isRegular(),
                hospital.getLastVisited(),
                hospital.getAddedBy(),
                EmergencyInfo.from(erInfo)
        );
    }
}
