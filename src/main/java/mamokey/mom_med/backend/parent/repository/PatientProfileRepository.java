package mamokey.mom_med.backend.parent.repository;

import mamokey.mom_med.backend.parent.domain.PatientProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

/**
 * app.patient_profiles JPA 리포지토리.
 */
public interface PatientProfileRepository extends JpaRepository<PatientProfile, UUID> {

    /** 데이터 공유 동의 + 기상청 격자 좌표가 모두 설정된 부모 목록을 반환합니다. */
    @Query("SELECT p FROM PatientProfile p WHERE p.consentDataShare = true AND p.nx IS NOT NULL AND p.ny IS NOT NULL")
    List<PatientProfile> findAllWithConsentAndGrid();
}
