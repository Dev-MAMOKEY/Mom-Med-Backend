package mamokey.mom_med.backend.parent.repository;

import mamokey.mom_med.backend.parent.domain.HospitalEmergencyInfo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HospitalEmergencyInfoRepository extends JpaRepository<HospitalEmergencyInfo, String> {
}
