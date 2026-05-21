package mamokey.mom_med.backend.global.util;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 식약처/DUR 원천 데이터의 약물명을 비교하기 위한 결정적 정규화 유틸입니다.
 * <p>
 * 의도적으로 닫힌 염(salt) 목록만 제거해 후속 슬라이스의 매칭 결과가 예측 가능하도록 둡니다.
 */
public final class DrugNameNormalizer {

	private static final Pattern PARENTHETICAL = Pattern.compile("\\s*\\([^)]*\\)\\s*");

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
	}

	public static String normalize(String name) {
		if (name == null) {
			return null;
		}

		String trimmed = name.strip();
		if (trimmed.isEmpty()) {
			return trimmed;
		}

		// 한글 단독 표기는 Slice 02의 fallback 매칭에서 그대로 사용할 수 있게 보존합니다.
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
		return value.chars().anyMatch(c -> (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z'));
	}
}
