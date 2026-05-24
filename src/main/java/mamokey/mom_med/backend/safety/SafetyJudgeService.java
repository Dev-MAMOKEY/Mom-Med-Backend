package mamokey.mom_med.backend.safety;

import java.util.UUID;

/**
 * 약물 안전 검사 인터페이스.
 *
 * <p>현재 구현체: {@link MockSafetyJudgeService} (항상 ALLOW 반환).
 * Slice 02 (DUR) + Slice 03 (NB 추출) 완성 후 실제 구현체로 교체 예정.</p>
 *
 * <p>이 인터페이스만 의존하도록 작성하면 Track A(동환이)가 실제 구현체를 제공할 때
 * 서비스 코드 변경 없이 Bean만 교체하면 됩니다.</p>
 */
public interface SafetyJudgeService {

    /**
     * 부모의 현재 약장에 새 약을 추가할 때 안전 여부를 판정합니다.
     *
     * @param parentId       부모 프로파일 UUID
     * @param newDrugItemSeq 추가하려는 약의 품목기준코드 (식약처 item_seq)
     * @return 판정 결과 (BLOCK/WARN/INFO/ALLOW + 근거 목록)
     */
    Verdict judge(UUID parentId, String newDrugItemSeq);
}
