package mamokey.mom_med.backend.infra.etl.dur;

import java.util.ArrayList;
import java.util.List;

/**
 * HIRA DUR CSV 한 줄을 컬럼 단위로 분리하는 작은 파서입니다.
 *
 * <p>DUR CSV의 상세정보 컬럼에는 쉼표가 포함될 수 있어 단순 {@code split(",")}를 쓰면
 * 컬럼 위치가 밀립니다. 이 파서는 쌍따옴표 안의 쉼표는 값으로 보존하고, 바깥 쉼표만
 * 구분자로 처리해서 CSV 헤더와 데이터 컬럼 매핑이 깨지지 않도록 합니다.</p>
 */
final class CsvRowParser {

	private CsvRowParser() {
	}

	static List<String> parse(String line) {
		List<String> values = new ArrayList<>();
		if (line == null) {
			return values;
		}

		StringBuilder current = new StringBuilder();
		boolean quoted = false;
		for (int index = 0; index < line.length(); index++) {
			char ch = line.charAt(index);
			if (ch == '"') {
				if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
					current.append('"');
					index++;
				}
				else {
					quoted = !quoted;
				}
			}
			else if (ch == ',' && !quoted) {
				values.add(current.toString());
				current.setLength(0);
			}
			else {
				current.append(ch);
			}
		}
		values.add(current.toString());
		return values;
	}
}
