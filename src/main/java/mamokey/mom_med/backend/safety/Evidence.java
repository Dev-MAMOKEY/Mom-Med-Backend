package mamokey.mom_med.backend.safety;

/**
 * 안전 검사 근거 단위.
 *
 * @param type        근거 유형 (예: "dur_interaction", "nb_contraindication", "allergy", "age_caution")
 * @param description 사용자에게 보여줄 설명 (한국어)
 * @param source      근거 출처 (예: "HIRA DUR", "NB amlodipine.txt")
 */
public record Evidence(
        String type,
        String description,
        String source
) {
}
