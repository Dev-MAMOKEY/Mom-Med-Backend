package mamokey.mom_med.backend.parent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * POST /v1/parents/{parentId}/medications 요청 바디.
 *
 * <p>item_seq는 식약처 품목기준코드로, /v1/drugs?name={name} 검색으로 먼저 확인해야 합니다.
 * drug_name은 DrugMaster에서 조회해 덮어쓰지만, 마스터 미적재 상태 대비용으로 전달 받습니다.</p>
 */
public record AddMedicationRequest(

        @NotBlank(message = "품목기준코드(item_seq)는 필수입니다.")
        @Size(max = 20)
        String itemSeq,

        @Size(max = 300)
        String drugName,

        LocalDate startedOn,

        @Size(max = 500)
        String memo
) {
}
