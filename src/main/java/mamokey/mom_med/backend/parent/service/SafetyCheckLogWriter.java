package mamokey.mom_med.backend.parent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import mamokey.mom_med.backend.domain.safety.model.SafetyVerdict;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 약 추가 시 안전 검사 결과를 logs.safety_check_log에 기록합니다.
 *
 * REQUIRES_NEW 전파 수준을 사용하므로, 상위 트랜잭션이 BLOCK 예외로 롤백되어도
 * 이 로그는 독립된 트랜잭션으로 커밋되어 보존됩니다.
 *
 * evidence_summary JSONB 컬럼에는 SQL 레벨의 ::jsonb 캐스팅으로 적재합니다.
 * PGobject는 runtimeOnly 의존성이라 컴파일 타임에 사용할 수 없습니다.
 */
@Component
public class SafetyCheckLogWriter {

    private final NamedParameterJdbcTemplate namedJdbc;
    private final ObjectMapper objectMapper;

    public SafetyCheckLogWriter(NamedParameterJdbcTemplate namedJdbc, ObjectMapper objectMapper) {
        this.namedJdbc = namedJdbc;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID parentId, String newDrugItemSeq, SafetyVerdict verdict) {
        String evidenceJson = toJson(verdict);
        namedJdbc.update("""
                INSERT INTO logs.safety_check_log
                    (parent_id, new_drug_item_seq, decision, evidence_summary)
                VALUES (:parentId, :itemSeq, :decision, (:evidence)::jsonb)
                """,
                new MapSqlParameterSource()
                        .addValue("parentId", parentId)
                        .addValue("itemSeq", newDrugItemSeq)
                        .addValue("decision", verdict.decision().name())
                        .addValue("evidence", evidenceJson)
        );
    }

    private String toJson(SafetyVerdict verdict) {
        try {
            return objectMapper.writeValueAsString(verdict);
        } catch (Exception e) {
            return "{\"decision\":\"" + verdict.decision().name() + "\",\"evidences\":[]}";
        }
    }
}
