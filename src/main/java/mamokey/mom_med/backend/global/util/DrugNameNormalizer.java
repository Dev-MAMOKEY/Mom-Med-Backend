package mamokey.mom_med.backend.global.util;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 식약처/해외 표기 약물명을 DUR 매칭에 쓰기 좋은 base name으로 바꾸는 공통 유틸입니다.
 *
 * <p>Slice 01은 식약처 약 마스터 데이터를 적재하고, Slice 02는 HIRA DUR 데이터를 매칭합니다.
 * 두 데이터의 약 이름 표기가 서로 다를 수 있으므로, 후속 슬라이스가 같은 규칙으로 이름을 비교하도록
 * 이 클래스에서 결정적인 정규화 규칙을 제공합니다.</p>
 *
 * <p>주의: 의학적 의미가 달라질 수 있는 임의 변환은 하지 않고, 닫힌 목록으로 관리하는 15개 salt suffix만
 * 제거합니다. 목록을 늘릴 때는 반드시 회귀 테스트를 함께 추가해야 합니다.</p>
 */
public final class DrugNameNormalizer {

	// "(as amlodipine)"처럼 괄호 안에 들어간 보조 설명을 제거하기 위한 패턴입니다.
	private static final Pattern PARENTHETICAL = Pattern.compile("\\s*\\([^)]*\\)\\s*");

	/**
	 * 제거 가능한 salt suffix의 닫힌 목록입니다.
	 * 목록 순서가 결과에 영향을 줄 수 있으므로, 새 suffix를 추가할 때 기존 테스트를 꼭 확인해야 합니다.
	 */
	public static final List<String> SALT_SUFFIXES = List.of(
			"besylate",
			"maleate",
			"camsylate",
			"mesylate",
			"tosylate",
			"hydrochloride",
			"hcl",
			"sulfate",
			"sulphate",
			"tartrate",
			"fumarate",
			"succinate",
			"acetate",
			"sodium",
			"potassium"
	);

	private DrugNameNormalizer() {
		// 상태가 없는 유틸 클래스이므로 인스턴스 생성을 막습니다.
	}

	/**
	 * 입력 약물명을 비교 가능한 base name으로 정규화합니다.
	 *
	 * <p>처리 흐름은 trim -> 한글 단독 표기 pass-through -> 소문자화 -> 괄호 설명 제거 -> salt suffix 제거입니다.
	 * null은 null로 돌려주어 호출자가 기존 null 처리 흐름을 유지할 수 있게 했습니다.</p>
	 */
	public static String normalize(String name) {
		if (name == null) {
			return null;
		}

		String trimmed = name.strip();
		if (trimmed.isEmpty()) {
			return trimmed;
		}

		// 한글 단독 표기는 Slice 02의 fallback 매칭에서 원문 그대로 사용할 수 있도록 보존합니다.
		if (!trimmed.isBlank() && !trimmed.chars().allMatch(c -> c < 128) && !hasLatinChar(trimmed)) {
			return trimmed;
		}

		String normalized = PARENTHETICAL.matcher(trimmed.toLowerCase(Locale.ROOT)).replaceAll(" ").strip();
		for (String suffix : SALT_SUFFIXES) {
			String saltSuffix = " " + suffix;
			if (normalized.endsWith(saltSuffix)) {
				return normalized.substring(0, normalized.length() - saltSuffix.length()).strip();
			}
		}

		return normalized;
	}

	private static boolean hasLatinChar(String value) {
		// 영문자가 하나라도 섞인 표기는 영문 매칭 후보로 보고 정규화 대상에 포함합니다.
		return value.chars().anyMatch(c -> (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z'));
	}
}
