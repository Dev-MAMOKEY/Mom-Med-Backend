package mamokey.mom_med.backend.domain.nb.repository;

import java.time.LocalDate;
import java.util.Optional;

import mamokey.mom_med.backend.domain.nb.entity.NbExtraction;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * NB 추출 캐시 조회 Repository입니다.
 */
public interface NbExtractionRepository extends JpaRepository<NbExtraction, Long> {

	Optional<NbExtraction> findByItemSeqAndDrugChangeDateAndLlmModelAndPromptVersion(
			String itemSeq,
			LocalDate drugChangeDate,
			String llmModel,
			String promptVersion
	);

	Optional<NbExtraction> findFirstByItemSeqAndVerifiedTrueOrderByIdDesc(String itemSeq);
}
