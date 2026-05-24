package mamokey.mom_med.backend.parent.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import mamokey.mom_med.backend.global.audit.BaseTimeEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 부모(환자) 프로파일 엔티티.
 *
 * <p>app.patient_profiles 테이블과 매핑됩니다.
 * created_at / updated_at은 BaseTimeEntity(JPA Auditing)가 관리합니다.
 * DB 트리거(trg_patient_profiles_updated_at)는 JPA Auditing과 동일한 값을 설정하므로 공존 가능합니다.</p>
 */
@Entity
@Table(name = "patient_profiles", schema = "app")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PatientProfile extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "parent_id", updatable = false, nullable = false)
    private UUID parentId;

    @Column(name = "display_name", nullable = false, length = 50)
    private String displayName;

    @Column(nullable = false)
    private LocalDate birthdate;

    /** 성별: 'M' | 'F' */
    @Column(nullable = false, length = 1)
    private String sex;

    @Column(name = "address_sido", length = 50)
    private String addressSido;

    @Column(name = "address_sigungu", length = 50)
    private String addressSigungu;

    @Column(name = "address_dong", length = 50)
    private String addressDong;

    /** 기상청 격자 X 좌표 (Slice 06에서 address → nx/ny 자동 변환) */
    @Column(columnDefinition = "SMALLINT")
    private Short nx;

    /** 기상청 격자 Y 좌표 */
    @Column(columnDefinition = "SMALLINT")
    private Short ny;

    @Column(name = "is_pregnant", nullable = false)
    private boolean pregnant = false;

    @Column(name = "consent_data_share", nullable = false)
    private boolean consentDataShare = false;

    @Column(name = "consent_at")
    private LocalDateTime consentAt;

    // ─── 생성 ─────────────────────────────────────────────────────────────

    public static PatientProfile create(
            String displayName,
            LocalDate birthdate,
            String sex,
            String addressSido,
            String addressSigungu,
            String addressDong,
            boolean pregnant,
            boolean consentDataShare
    ) {
        PatientProfile p = new PatientProfile();
        p.displayName = displayName;
        p.birthdate = birthdate;
        p.sex = sex;
        p.addressSido = addressSido;
        p.addressSigungu = addressSigungu;
        p.addressDong = addressDong;
        p.pregnant = pregnant;
        p.consentDataShare = consentDataShare;
        if (consentDataShare) {
            p.consentAt = LocalDateTime.now();
        }
        return p;
    }

    // ─── 수정 ─────────────────────────────────────────────────────────────

    public void update(
            String displayName,
            LocalDate birthdate,
            String sex,
            String addressSido,
            String addressSigungu,
            String addressDong,
            Boolean pregnant,
            Boolean consentDataShare
    ) {
        if (displayName != null) this.displayName = displayName;
        if (birthdate != null) this.birthdate = birthdate;
        if (sex != null) this.sex = sex;
        if (addressSido != null) this.addressSido = addressSido;
        if (addressSigungu != null) this.addressSigungu = addressSigungu;
        if (addressDong != null) this.addressDong = addressDong;
        if (pregnant != null) this.pregnant = pregnant;
        if (consentDataShare != null) {
            boolean wasConsented = this.consentDataShare;
            this.consentDataShare = consentDataShare;
            if (consentDataShare && !wasConsented) {
                this.consentAt = LocalDateTime.now();
            }
        }
    }

    /** Lombok @Getter는 boolean 필드 'pregnant'에 대해 isPregnant()를 생성합니다. */
    public boolean isPregnant() {
        return pregnant;
    }
}
