package mamokey.mom_med.backend.parent.repository;

import mamokey.mom_med.backend.parent.domain.ParentPharmacy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ParentPharmacyRepository extends JpaRepository<ParentPharmacy, Long> {

    List<ParentPharmacy> findTop3ByParentIdOrderByRegularDescVisitCountDesc(UUID parentId);

    List<ParentPharmacy> findByParentIdOrderByRegularDescVisitCountDesc(UUID parentId);

    Optional<ParentPharmacy> findByIdAndParentId(Long id, UUID parentId);

    boolean existsByParentIdAndYkiho(UUID parentId, String ykiho);
}
