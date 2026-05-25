package mamokey.mom_med.backend.parent.repository;

import mamokey.mom_med.backend.parent.domain.EmergencyContact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmergencyContactRepository extends JpaRepository<EmergencyContact, Long> {

    List<EmergencyContact> findByParentIdOrderByPriorityAscCreatedAtAsc(UUID parentId);

    Optional<EmergencyContact> findByIdAndParentId(Long id, UUID parentId);
}
