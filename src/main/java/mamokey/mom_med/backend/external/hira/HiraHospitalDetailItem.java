package mamokey.mom_med.backend.external.hira;

/**
 * HIRA 의료기관별상세정보서비스(sno 12101) 응답 item.
 *
 * @param nightErAvailable  야간 응급실 운영 여부 (Y/N)
 * @param nightErPhone1     야간 응급 전화 1
 * @param nightErPhone2     야간 응급 전화 2
 * @param dayErAvailable    주간 응급실 운영 여부 (Y/N)
 * @param dayErPhone1       주간 응급 전화 1
 * @param dayErPhone2       주간 응급 전화 2
 * @param weeklyHoursJson   요일별 진료시간 JSON 문자열
 */
public record HiraHospitalDetailItem(
        String nightErAvailable,
        String nightErPhone1,
        String nightErPhone2,
        String dayErAvailable,
        String dayErPhone1,
        String dayErPhone2,
        String weeklyHoursJson
) {}
