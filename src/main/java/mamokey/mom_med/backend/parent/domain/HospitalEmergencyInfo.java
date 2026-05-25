package mamokey.mom_med.backend.parent.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * 병원 응급실 상세 정보 캐시 (Slice 08).
 *
 * <p>app.hospital_emergency_info 테이블과 매핑됩니다.
 * HIRA 의료기관별상세정보서비스(sno 12101) 응답을 파싱해 적재합니다.
 * ykiho를 PK로 사용하며 여러 부모가 같은 병원을 등록해도 1건만 보관합니다.</p>
 */
@Entity
@Table(name = "hospital_emergency_info", schema = "app")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HospitalEmergencyInfo {

    @Id
    @Column(length = 500)
    private String ykiho;

    @Column(name = "yadm_nm", length = 200)
    private String yadmNm;

    /** 야간 응급실 운영 여부 (Y/N) — VARCHAR(1), V020 마이그레이션으로 CHAR(1)에서 변환 */
    @Column(name = "night_er_available", length = 1)
    private String nightErAvailable;

    @Column(name = "night_er_phone_1", length = 50)
    private String nightErPhone1;

    @Column(name = "night_er_phone_2", length = 50)
    private String nightErPhone2;

    /** 주간 응급실 운영 여부 (Y/N) — VARCHAR(1), V020 마이그레이션으로 CHAR(1)에서 변환 */
    @Column(name = "day_er_available", length = 1)
    private String dayErAvailable;

    @Column(name = "day_er_phone_1", length = 50)
    private String dayErPhone1;

    @Column(name = "day_er_phone_2", length = 50)
    private String dayErPhone2;

    /** 요일별 진료 시간 (JSONB) — {"mon":{"start":"0900","end":"1800"}, ...} */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "weekly_hours", columnDefinition = "jsonb")
    private String weeklyHours;

    @Column(name = "sun_closed", length = 100)
    private String sunClosed;

    @Column(name = "holi_closed", length = 100)
    private String holiClosed;

    @Column(name = "refreshed_at", nullable = false,
            columnDefinition = "TIMESTAMPTZ DEFAULT NOW()")
    private LocalDateTime refreshedAt = LocalDateTime.now();

    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "TIMESTAMPTZ DEFAULT NOW()")
    private LocalDateTime createdAt = LocalDateTime.now();

    // ─── 생성 / 갱신 ──────────────────────────────────────────────────────

    public static HospitalEmergencyInfo create(
            String ykiho,
            String yadmNm,
            String nightErAvailable,
            String nightErPhone1,
            String nightErPhone2,
            String dayErAvailable,
            String dayErPhone1,
            String dayErPhone2,
            String weeklyHours,
            String sunClosed,
            String holiClosed
    ) {
        HospitalEmergencyInfo info = new HospitalEmergencyInfo();
        info.ykiho = ykiho;
        info.yadmNm = yadmNm;
        info.nightErAvailable = nightErAvailable;
        info.nightErPhone1 = nightErPhone1;
        info.nightErPhone2 = nightErPhone2;
        info.dayErAvailable = dayErAvailable;
        info.dayErPhone1 = dayErPhone1;
        info.dayErPhone2 = dayErPhone2;
        info.weeklyHours = weeklyHours;
        info.sunClosed = sunClosed;
        info.holiClosed = holiClosed;
        return info;
    }

    public void refresh(
            String nightErAvailable,
            String nightErPhone1,
            String nightErPhone2,
            String dayErAvailable,
            String dayErPhone1,
            String dayErPhone2,
            String weeklyHours
    ) {
        this.nightErAvailable = nightErAvailable;
        this.nightErPhone1 = nightErPhone1;
        this.nightErPhone2 = nightErPhone2;
        this.dayErAvailable = dayErAvailable;
        this.dayErPhone1 = dayErPhone1;
        this.dayErPhone2 = dayErPhone2;
        this.weeklyHours = weeklyHours;
        this.refreshedAt = LocalDateTime.now();
    }
}
