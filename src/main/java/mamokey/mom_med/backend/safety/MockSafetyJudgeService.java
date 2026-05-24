package mamokey.mom_med.backend.safety;

import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * SafetyJudgeService 임시 구현체 — 항상 ALLOW를 반환합니다.
 *
 * <p>Slice 02 (DUR 병용금기 검사) + Slice 03 (NB 추출 기반 검사)가 완성되면
 * 실제 구현체로 교체합니다. 교체 시에는:
 * <ol>
 *   <li>실제 구현체 클래스에 {@code @Primary} 또는 이 Mock에 {@code @ConditionalOnMissingBean} 적용</li>
 *   <li>이 파일 삭제 또는 {@code @Profile("!prod")}로 한정</li>
 * </ol>
 * </p>
 *
 * <p>TODO: Slice 02 + 03 완성 후 교체 — Track A (동환이)와 협의</p>
 */
@Service
public class MockSafetyJudgeService implements SafetyJudgeService {

    @Override
    public Verdict judge(UUID parentId, String newDrugItemSeq) {
        // Slice 02/03 미구현 동안 모든 약 추가를 허용합니다.
        return Verdict.allow();
    }
}
