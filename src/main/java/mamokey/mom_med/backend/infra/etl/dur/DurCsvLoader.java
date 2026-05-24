package mamokey.mom_med.backend.infra.etl.dur;

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import mamokey.mom_med.backend.global.util.DrugNameNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * HIRA DUR CSV와 질병코드(disease_master) CSV를 PostgreSQL {@code ref} schema로 적재하는 로더입니다.
 *
 * <p>CSV 원본은 cp949 인코딩이며, 성분명은 저장 전에 {@link DrugNameNormalizer}로 정규화합니다.
 * 이후 DURRuleEngine은 정규화 컬럼만 사용해 {@code =} 정확 매칭을 수행하므로,
 * ETL 단계에서 정규화 값을 함께 저장하는 것이 핵심입니다.</p>
 */
@Component
public class DurCsvLoader {

	private static final Logger log = LoggerFactory.getLogger(DurCsvLoader.class);
	private static final Charset CP949 = Charset.forName("CP949");
	private static final int BATCH_SIZE = 1_000;

	private final JdbcTemplate jdbcTemplate;
	private final Path comboPath;
	private final Path elderlyPath;
	private final Path elderlyNsaidPath;
	private final Path agePath;
	private final Path pregnancyPath;
	private final Path diseaseMasterPath;

	public DurCsvLoader(
			JdbcTemplate jdbcTemplate,
			@Value("${app.etl.dur.combo-path:data/_downloads/11983_ex/의약품안전사용서비스(DUR)_병용금기 품목리스트 2025.6.csv}") String comboPath,
			@Value("${app.etl.dur.elderly-path:data/건강보험심사평가원_의약품안전사용서비스(DUR) 의약품 목록_20250601/의약품안전사용서비스(DUR)_노인주의 품목리스트 2025.6.csv}") String elderlyPath,
			@Value("${app.etl.dur.elderly-nsaid-path:data/건강보험심사평가원_의약품안전사용서비스(DUR) 의약품 목록_20250601/의약품안전사용서비스(DUR)_노인주의(해열진통소염제) 품목리스트 2025.6.csv}") String elderlyNsaidPath,
			@Value("${app.etl.dur.age-path:drug_csv/의약품안전사용서비스(DUR)_연령금기 품목리스트 2025.6.csv}") String agePath,
			@Value("${app.etl.dur.pregnancy-path:drug_csv/의약품안전사용서비스(DUR)_임부금기 품목리스트 2025.6.csv}") String pregnancyPath,
			@Value("${app.etl.disease-master.path:data/_downloads/11984/질병코드.csv}") String diseaseMasterPath
	) {
		this.jdbcTemplate = jdbcTemplate;
		this.comboPath = resolvePath(Path.of(comboPath), Path.of(
				"data/건강보험심사평가원_의약품안전사용서비스(DUR) 의약품 목록_20250601/의약품안전사용서비스(DUR)_병용금기 품목리스트 2025.6.csv"));
		this.elderlyPath = Path.of(elderlyPath);
		this.elderlyNsaidPath = Path.of(elderlyNsaidPath);
		this.agePath = Path.of(agePath);
		this.pregnancyPath = Path.of(pregnancyPath);
		this.diseaseMasterPath = Path.of(diseaseMasterPath);
	}

	/**
	 * 병용금기, 노인주의, NSAID 노인주의, 연령금기, 임부금기, 질병코드 CSV를 순서대로 적재합니다.
	 */
	@Transactional
	public void loadDefaults() {
		loadCombo(comboPath);
		loadElderly(elderlyPath);
		loadElderlyNsaid(elderlyNsaidPath);
		loadAge(agePath);
		loadPregnancy(pregnancyPath);
		loadDiseaseMaster(diseaseMasterPath);
	}

	public int loadCombo(Path path) {
		String sql = """
				INSERT INTO ref.dur_combo_contraindications (
				  source_row_hash,
				  ingredient_name_a, ingredient_norm_a, ingredient_code_a, product_code_a, product_name_a, company_name_a, reimbursement_a,
				  ingredient_name_b, ingredient_norm_b, ingredient_code_b, product_code_b, product_name_b, company_name_b, reimbursement_b,
				  gazette_no, gazette_date, detail, note
				) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				ON CONFLICT (source_row_hash) DO NOTHING
				""";

		return load(path, "dur-combo", (header, row) -> {
			String ingredientA = value(header, row, "성분명A");
			String ingredientB = value(header, row, "성분명B");
			String normA = truncate(DrugNameNormalizer.normalize(ingredientA), 500);
			String normB = truncate(DrugNameNormalizer.normalize(ingredientB), 500);
			if (isBlank(normA) || isBlank(normB)) {
				return null;
			}

			// CSV 컬럼 A/B는 병용금기 약쌍의 양쪽 성분과 제품 정보를 의미합니다.
			// DB에는 원본 성분명과 정규화 성분명을 모두 저장하고, 안전 판정 조회는 정규화 컬럼만 사용합니다.
			return new Object[] {
					sha256("combo", row),
					ingredientA, normA, value(header, row, "성분코드A"), value(header, row, "제품코드A"),
					value(header, row, "제품명A"), value(header, row, "업체명A"), value(header, row, "급여여부A"),
					ingredientB, normB, value(header, row, "성분코드B"), value(header, row, "제품코드B"),
					value(header, row, "제품명B"), value(header, row, "업체명B"), value(header, row, "급여여부B"),
					value(header, row, "고시번호"), sqlDate(value(header, row, "고시일자")),
					value(header, row, "상세정보"), value(header, row, "비고")
			};
		}, sql);
	}

	public int loadElderly(Path path) {
		String sql = """
				INSERT INTO ref.dur_elderly_caution (
				  source_row_hash, ingredient_name, ingredient_norm, ingredient_code, product_code, product_name,
				  company_name, gazette_date, gazette_no, detail, note, reimbursement
				) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				ON CONFLICT (source_row_hash) DO NOTHING
				""";

		return load(path, "dur-elderly", (header, row) -> {
			String ingredient = value(header, row, "성분명");
			String norm = truncate(DrugNameNormalizer.normalize(ingredient), 500);
			if (isBlank(norm)) {
				return null;
			}
			return new Object[] {
					sha256("elderly", row),
					ingredient, norm, value(header, row, "성분코드"), value(header, row, "제품코드"),
					value(header, row, "제품명"), value(header, row, "업소명"), sqlDate(value(header, row, "공고일자")),
					value(header, row, "공고번호"), value(header, row, "약품상세정보"),
					value(header, row, "비고"), value(header, row, "급여여부")
			};
		}, sql);
	}

	public int loadElderlyNsaid(Path path) {
		String sql = """
				INSERT INTO ref.dur_elderly_nsaid_caution (
				  source_row_hash, ingredient_name, ingredient_norm, ingredient_code, product_code, product_name,
				  company_name, detail, reimbursement
				) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
				ON CONFLICT (source_row_hash) DO NOTHING
				""";

		return load(path, "dur-elderly-nsaid", (header, row) -> {
			String ingredient = value(header, row, "성분명");
			String norm = truncate(DrugNameNormalizer.normalize(ingredient), 500);
			if (isBlank(norm)) {
				return null;
			}
			return new Object[] {
					sha256("elderly-nsaid", row),
					ingredient, norm, value(header, row, "성분코드"), value(header, row, "제품코드"),
					value(header, row, "제품명"), value(header, row, "업소명"),
					value(header, row, "약품상세정보"), value(header, row, "급여여부")
			};
		}, sql);
	}

	public int loadAge(Path path) {
		String sql = """
				INSERT INTO ref.dur_age_contraindication (
				  source_row_hash, ingredient_name, ingredient_norm, ingredient_code, product_code, product_name,
				  age_limit, gazette_no, gazette_date, detail
				) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				ON CONFLICT (source_row_hash) DO NOTHING
				""";

		return load(path, "dur-age", (header, row) -> {
			String ingredient = value(header, row, "성분명");
			String norm = truncate(DrugNameNormalizer.normalize(ingredient), 500);
			if (isBlank(norm)) {
				return null;
			}
			// 특정연령 + 특정연령단위 + 연령처리조건을 조합 (예: "12세미만")
			String ageThreshold = value(header, row, "특정연령");
			String ageUnit = value(header, row, "특정연령단위");
			String condition = value(header, row, "연령처리조건");
			String ageLimit = buildAgeLimit(ageThreshold, ageUnit, condition);

			return new Object[] {
					sha256("age", row),
					ingredient, norm,
					value(header, row, "성분코드"),
					value(header, row, "제품코드"),
					value(header, row, "제품명"),
					ageLimit,
					value(header, row, "고시번호"),
					sqlDate(value(header, row, "고시일자")),
					value(header, row, "약품상세정보")
			};
		}, sql);
	}

	public int loadPregnancy(Path path) {
		String sql = """
				INSERT INTO ref.dur_pregnancy_contraindication (
				  source_row_hash, ingredient_name, ingredient_norm, ingredient_code, product_code, product_name,
				  pregnancy_grade, gazette_no, gazette_date, detail
				) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				ON CONFLICT (source_row_hash) DO NOTHING
				""";

		return load(path, "dur-pregnancy", (header, row) -> {
			String ingredient = value(header, row, "성분명");
			String norm = truncate(DrugNameNormalizer.normalize(ingredient), 500);
			if (isBlank(norm)) {
				return null;
			}
			return new Object[] {
					sha256("pregnancy", row),
					ingredient, norm,
					value(header, row, "성분코드"),
					value(header, row, "제품코드"),
					value(header, row, "제품명"),
					value(header, row, "임부금기등급"),
					value(header, row, "고시번호"),
					sqlDate(value(header, row, "고시일자")),
					value(header, row, "약품상세정보")
			};
		}, sql);
	}

	/**
	 * HIRA 11984 질병코드 CSV를 {@code ref.disease_master}에 적재합니다.
	 *
	 * <p>CSV 컬럼: 상병기호, 한글명, 영문명, 완전코드구분, 주상병사용구분, 법정감염병구분,
	 * 성별구분, 상한연령, 하한연령, 양한방구분</p>
	 */
	public int loadDiseaseMaster(Path path) {
		String sql = """
				INSERT INTO ref.disease_master (
				  sick_cd, sick_nm, sick_eng_nm,
				  complete_code_flag, main_diagnosis, infectious_grade,
				  sex_restriction, age_max, age_min, yang_han_type
				) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				ON CONFLICT (sick_cd) DO NOTHING
				""";

		return load(path, "disease-master", (header, row) -> {
			String sickCd = value(header, row, "상병기호");
			String sickNm = value(header, row, "한글명");
			if (isBlank(sickCd) || isBlank(sickNm)) {
				return null;
			}
			return new Object[] {
					sickCd,
					sickNm,
					value(header, row, "영문명"),
					singleChar(value(header, row, "완전코드구분")),
					singleChar(value(header, row, "주상병사용구분")),
					value(header, row, "법정감염병구분"),
					singleChar(value(header, row, "성별구분")),
					parseInt(value(header, row, "상한연령")),
					parseInt(value(header, row, "하한연령")),
					value(header, row, "양한방구분")
			};
		}, sql);
	}

	private static String truncate(String value, int max) {
		if (value == null || value.length() <= max) {
			return value;
		}
		return value.substring(0, max);
	}

	private static String singleChar(String value) {
		if (isBlank(value)) {
			return null;
		}
		return value.strip().substring(0, 1);
	}

	private static Integer parseInt(String value) {
		if (isBlank(value)) {
			return null;
		}
		try {
			return Integer.parseInt(value.strip());
		}
		catch (NumberFormatException exception) {
			return null;
		}
	}

	private static String buildAgeLimit(String threshold, String unit, String condition) {
		if (isBlank(threshold)) {
			return null;
		}
		StringBuilder sb = new StringBuilder(threshold.strip());
		if (!isBlank(unit)) {
			sb.append(unit.strip());
		}
		if (!isBlank(condition)) {
			sb.append(condition.strip());
		}
		return sb.toString();
	}

	private int load(Path path, String sourceName, RowMapper rowMapper, String sql) {
		return load(path, sourceName, rowMapper, sql, CP949);
	}

	private int load(Path path, String sourceName, RowMapper rowMapper, String sql, Charset charset) {
		if (!Files.exists(path)) {
			log.warn("DUR CSV file not found. source={}, path={}", sourceName, path.toAbsolutePath());
			return 0;
		}

		int insertedRows = 0;
		List<Object[]> batch = new ArrayList<>(BATCH_SIZE);
		try (BufferedReader reader = Files.newBufferedReader(path, charset)) {
			Map<String, Integer> header = header(CsvRowParser.parse(reader.readLine()));
			String line;
			while ((line = reader.readLine()) != null) {
				Object[] values = rowMapper.map(header, CsvRowParser.parse(line));
				if (values == null) {
					continue;
				}
				batch.add(values);
				if (batch.size() == BATCH_SIZE) {
					insertedRows += flush(sql, batch);
				}
			}
			insertedRows += flush(sql, batch);
			log.info("DUR CSV load completed. source={}, charset={}, insertedRows={}", sourceName, charset.name(), insertedRows);
			return insertedRows;
		}
		catch (MalformedInputException exception) {
			if (charset == CP949) {
				log.warn("CP949 decoding failed for source={}, retrying with UTF-8", sourceName);
				return load(path, sourceName, rowMapper, sql, StandardCharsets.UTF_8);
			}
			log.error("DUR CSV load failed (encoding mismatch). source={}, path={}", sourceName, path.toAbsolutePath(), exception);
			throw new IllegalStateException("DUR CSV load failed: " + sourceName, exception);
		}
		catch (IOException exception) {
			log.error("DUR CSV load failed. source={}, path={}", sourceName, path.toAbsolutePath(), exception);
			throw new IllegalStateException("DUR CSV load failed: " + sourceName, exception);
		}
	}

	private int flush(String sql, List<Object[]> batch) {
		if (batch.isEmpty()) {
			return 0;
		}
		int[] results = jdbcTemplate.batchUpdate(sql, batch);
		batch.clear();
		int affectedRows = 0;
		for (int result : results) {
			if (result > 0) {
				affectedRows += result;
			}
		}
		return affectedRows;
	}

	private static Map<String, Integer> header(List<String> columns) {
		Map<String, Integer> header = new HashMap<>();
		for (int index = 0; index < columns.size(); index++) {
			header.put(columns.get(index).strip(), index);
		}
		return header;
	}

	private static String value(Map<String, Integer> header, List<String> row, String column) {
		Integer index = header.get(column);
		if (index == null || index >= row.size()) {
			return null;
		}
		String value = row.get(index);
		return isBlank(value) ? null : value.strip();
	}

	private static Date sqlDate(String value) {
		if (isBlank(value)) {
			return null;
		}
		try {
			return Date.valueOf(LocalDate.parse(value.strip()));
		}
		catch (DateTimeParseException exception) {
			return null;
		}
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private static Path resolvePath(Path primary, Path fallback) {
		return Files.exists(primary) ? primary : fallback;
	}

	private static String sha256(String sourceName, List<String> row) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			StringJoiner joiner = new StringJoiner("|", sourceName + "|", "");
			row.forEach(value -> joiner.add(value == null ? "" : value));
			byte[] hash = digest.digest(joiner.toString().getBytes(CP949));
			return String.format("%064x", new BigInteger(1, hash));
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is not available", exception);
		}
	}

	@FunctionalInterface
	private interface RowMapper {
		Object[] map(Map<String, Integer> header, List<String> row);
	}
}
