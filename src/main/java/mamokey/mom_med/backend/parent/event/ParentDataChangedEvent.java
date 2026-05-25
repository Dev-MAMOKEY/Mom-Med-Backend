package mamokey.mom_med.backend.parent.event;

import java.util.UUID;

/**
 * 부모의 약장·알레르기·기저질환이 변경될 때 발행하는 이벤트 (Slice 08).
 *
 * <p>EmergencyCardService가 이 이벤트를 수신해 snapshot을 자동 갱신합니다.
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)}으로 수신하므로
 * 메인 TX 커밋 이후에 독립적으로 처리됩니다.</p>
 */
public record ParentDataChangedEvent(UUID parentId) {}
