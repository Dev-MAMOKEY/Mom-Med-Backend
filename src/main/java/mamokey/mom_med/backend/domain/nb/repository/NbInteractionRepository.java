package mamokey.mom_med.backend.domain.nb.repository;

import java.util.List;

import mamokey.mom_med.backend.domain.nb.entity.NbInteraction;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 검증된 NB interaction 행을 조회하는 Repository입니다.
 *
 * <p>SafetyJudge는 item_seq 기준으로 해당 약 라벨에서 추출된 interaction을 읽어
 * 현재 약장과 양방향 매칭합니다.</p>
 */
public interface NbInteractionRepository extends JpaRepository<NbInteraction, Long> {

	List<NbInteraction> findByItemSeqAndEntryTypeOrderByIdAsc(String itemSeq, String entryType);

	List<NbInteraction> findByExtractionIdOrderByIdAsc(Long extractionId);

	void deleteByExtractionId(Long extractionId);
}
