package mamokey.mom_med.backend.parent.repository;

import mamokey.mom_med.backend.parent.domain.ParentHospital;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ParentHospitalRepository extends JpaRepository<ParentHospital, Long> {

    List<ParentHospital> findByParentIdOrderByRegularDescLastVisitedDesc(UUID parentId);

    Optional<ParentHospital> findByIdAndParentId(Long id, UUID parentId);

    boolean existsByParentIdAndYkiho(UUID parentId, String ykiho);
}
