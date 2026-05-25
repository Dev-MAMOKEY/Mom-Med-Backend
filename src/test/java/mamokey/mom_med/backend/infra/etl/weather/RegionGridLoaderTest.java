package mamokey.mom_med.backend.infra.etl.weather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import mamokey.mom_med.backend.domain.weather.repository.RegionGridRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 기상청 xlsx의 핵심 컬럼이 ref.region_grid 엔티티로 변환되는지 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class RegionGridLoaderTest {

	@Mock
	RegionGridRepository regionGridRepository;

	@TempDir
	Path tempDir;

	@Test
	void loadRegionGridFromXlsx() throws Exception {
		Path xlsx = tempDir.resolve("grid.xlsx");
		writeWorkbook(xlsx);
		when(regionGridRepository.findFirstBySidoAndSigunguAndEupMyeonDong("대구광역시", "중구", "성내동"))
				.thenReturn(Optional.empty());

		RegionGridLoader loader = new RegionGridLoader(regionGridRepository, 0.001);
		RegionGridLoadResult result = loader.load(xlsx);

		assertThat(result.inserted()).isEqualTo(1);
		assertThat(result.total()).isEqualTo(1);
		assertThat(result.skipped()).isZero();
		verify(regionGridRepository).save(any());
	}

	private static void writeWorkbook(Path xlsx) throws Exception {
		Files.createDirectories(xlsx.getParent());
		try (Workbook workbook = new XSSFWorkbook()) {
			var sheet = workbook.createSheet("grid");
			Row header = sheet.createRow(0);
			header.createCell(0).setCellValue("구분");
			header.createCell(1).setCellValue("행정구역코드");
			header.createCell(2).setCellValue("1단계");
			header.createCell(3).setCellValue("2단계");
			header.createCell(4).setCellValue("3단계");
			header.createCell(5).setCellValue("격자 X");
			header.createCell(6).setCellValue("격자 Y");
			Row row = sheet.createRow(1);
			row.createCell(1).setCellValue("27110517");
			row.createCell(2).setCellValue("대구광역시");
			row.createCell(3).setCellValue("중구");
			row.createCell(4).setCellValue("성내동");
			row.createCell(5).setCellValue(89);
			row.createCell(6).setCellValue(90);
			try (var outputStream = Files.newOutputStream(xlsx)) {
				workbook.write(outputStream);
			}
		}
	}
}
