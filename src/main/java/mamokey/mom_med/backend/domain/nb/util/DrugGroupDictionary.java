package mamokey.mom_med.backend.domain.nb.util;

import java.util.List;
import java.util.Map;

/**
 * NB 문서에 등장하는 약물군/식품명을 실제 약물 ATC prefix로 확장하기 위한 사전입니다.
 *
 * <p>LLM은 "이트라코나졸" 같은 단일 약물뿐 아니라 "CYP3A4 저해제" 같은 약물군도 추출합니다.
 * 단일 약물은 partner_drug_norm과 main_ingr_norm을 비교하고, 약물군은 이 사전의 ATC prefix로
 * 기존/신규 약의 ATC 코드가 포함되는지 검사합니다.</p>
 */
public final class DrugGroupDictionary {

	public static final Map<String, List<String>> GROUP_TO_ATC_PREFIX = Map.ofEntries(
			Map.entry("CYP3A4 저해제", List.of("J02AC", "J01FA09")),
			Map.entry("CYP3A4 유도제", List.of("J04AB02", "N03AF01")),
			Map.entry("비스테로이드성 소염제", List.of("M01A")),
			Map.entry("마크로라이드계 항생제", List.of("J01FA")),
			Map.entry("아졸계 항진균제", List.of("J02AC")),
			Map.entry("mTOR 억제제", List.of("L04AA10", "L04AA18")),
			Map.entry("베타차단제", List.of("C07")),
			Map.entry("이뇨제", List.of("C03")),
			Map.entry("항응고제", List.of("B01A")),
			Map.entry("항혈소판제", List.of("B01AC")),
			Map.entry("자몽", List.of()),
			Map.entry("알코올", List.of()),
			Map.entry("비타민 K", List.of())
	);

	private DrugGroupDictionary() {
	}

	public static boolean isDrugGroupName(String partnerKo) {
		return partnerKo != null && GROUP_TO_ATC_PREFIX.containsKey(partnerKo.strip());
	}

	public static List<String> prefixesFor(String partnerKo) {
		if (partnerKo == null) {
			return List.of();
		}
		return GROUP_TO_ATC_PREFIX.getOrDefault(partnerKo.strip(), List.of());
	}
}
