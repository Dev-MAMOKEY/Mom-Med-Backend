package mamokey.mom_med.backend.infra.etl.weather;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import mamokey.mom_med.backend.domain.weather.entity.RegionGrid;
import mamokey.mom_med.backend.domain.weather.repository.RegionGridRepository;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기상청 격자 xlsx를 ref.region_grid에 적재하는 ETL 서비스입니다.
 *
 * <p>원본 파일의 핵심 컬럼은 행정구역코드, 1단계(시도), 2단계(시군구), 3단계(읍면동), 격자 X/Y입니다.
 * 적재 후 ParentGridService가 이 데이터를 사용해 부모 주소를 nx/ny로 변환합니다.</p>
 */
@Service
public class RegionGridLoader {

	private static final int BATCH_SIZE = 1_000;

	private final RegionGridRepository regionGridRepository;
	private final DataFormatter formatter = new DataFormatter();

	public RegionGridLoader(RegionGridRepository regionGridRepository) {
		this.regionGridRepository = regionGridRepository;
	}

	@Transactional
	public RegionGridLoadResult load(Path xlsxPath) {
		try (InputStream inputStream = Files.newInputStream(xlsxPath);
			 Workbook workbook = WorkbookFactory.create(inputStream)) {
			Sheet sheet = workbook.getSheetAt(0);
			Map<String, Integer> header = headerIndex(sheet.getRow(0));
			Counters counters = new Counters();

			for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
				Row row = sheet.getRow(rowIndex);
				if (row == null) {
					counters.skipped++;
					continue;
				}
				RegionGridRow gridRow = toGridRow(row, header);
				if (gridRow == null) {
					counters.skipped++;
					continue;
				}
				upsert(gridRow, counters);
				if (counters.total % BATCH_SIZE == 0) {
					regionGridRepository.flush();
				}
			}

			return new RegionGridLoadResult(counters.inserted, counters.updated, counters.skipped, counters.total);
		}
		catch (IOException exception) {
			throw new IllegalStateException("기상청 격자 xlsx 파일을 읽을 수 없습니다: " + xlsxPath, exception);
		}
	}

	private void upsert(RegionGridRow row, Counters counters) {
		RegionGrid grid = regionGridRepository
				.findFirstBySidoAndSigunguAndEupMyeonDong(row.sido(), row.sigungu(), row.eupMyeonDong())
				.orElse(null);
		if (grid == null) {
			grid = RegionGrid.create(row.adminCode(), row.sido(), row.sigungu(), row.eupMyeonDong(),
					row.nx(), row.ny(), row.longitude(), row.latitude());
			counters.inserted++;
		}
		else {
			grid.refresh(row.nx(), row.ny(), row.longitude(), row.latitude(), row.adminCode());
			counters.updated++;
		}
		regionGridRepository.save(grid);
		counters.total++;
	}

	private RegionGridRow toGridRow(Row row, Map<String, Integer> header) {
		String adminCode = value(row, header, "행정구역코드", "admin_code", 1);
		String sido = value(row, header, "1단계", "sido", 2);
		String sigungu = blankToNull(value(row, header, "2단계", "sigungu", 3));
		String dong = blankToNull(value(row, header, "3단계", "eup_myeon_dong", 4));
		Short nx = shortValue(value(row, header, "격자 X", "nx", 5));
		Short ny = shortValue(value(row, header, "격자 Y", "ny", 6));
		BigDecimal longitude = decimalValue(value(row, header, "경도(초/100)", "longitude", -1));
		BigDecimal latitude = decimalValue(value(row, header, "위도(초/100)", "latitude", -1));

		if (sido == null || sido.isBlank() || nx == null || ny == null) {
			return null;
		}
		return new RegionGridRow(adminCode, sido, sigungu, dong, nx, ny, longitude, latitude);
	}

	private Map<String, Integer> headerIndex(Row headerRow) {
		Map<String, Integer> header = new HashMap<>();
		if (headerRow == null) {
			return header;
		}
		for (Cell cell : headerRow) {
			String name = formatter.formatCellValue(cell).trim();
			if (!name.isBlank()) {
				header.put(name, cell.getColumnIndex());
			}
		}
		return header;
	}

	private String value(Row row, Map<String, Integer> header, String koreanName, String englishName, int fallbackIndex) {
		Integer index = header.get(koreanName);
		if (index == null) {
			index = header.get(englishName);
		}
		if (index == null && fallbackIndex >= 0) {
			index = fallbackIndex;
		}
		if (index == null) {
			return null;
		}
		return formatter.formatCellValue(row.getCell(index)).trim();
	}

	private static Short shortValue(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return (short) Double.parseDouble(value);
	}

	private static BigDecimal decimalValue(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return new BigDecimal(value);
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value;
	}

	private record RegionGridRow(
			String adminCode,
			String sido,
			String sigungu,
			String eupMyeonDong,
			Short nx,
			Short ny,
			BigDecimal longitude,
			BigDecimal latitude
	) {
	}

	private static class Counters {
		private int inserted;
		private int updated;
		private int skipped;
		private int total;
	}
}
