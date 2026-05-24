package mamokey.mom_med.backend.parent.repository;

import mamokey.mom_med.backend.parent.domain.PatientProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * app.patient_profiles JPA 리포지토리.
 */
public interface PatientProfileRepository extends JpaRepository<PatientProfile, UUID> {
}
