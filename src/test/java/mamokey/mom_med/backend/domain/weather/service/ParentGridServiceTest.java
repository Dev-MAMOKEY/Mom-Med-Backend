package mamokey.mom_med.backend.domain.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import mamokey.mom_med.backend.domain.weather.entity.RegionGrid;
import mamokey.mom_med.backend.domain.weather.repository.RegionGridRepository;
import mamokey.mom_med.backend.parent.domain.PatientProfile;
import mamokey.mom_med.backend.parent.repository.PatientProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 부모 주소를 기상청 nx/ny로 변환할 때 동 우선 매칭과 시/구 fallback이 동작하는지 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class ParentGridServiceTest {

	@Mock
	PatientProfileRepository patientProfileRepository;

	@Mock
	RegionGridRepository regionGridRepository;

	@Test
	void exactDongMatchWinsBeforeFallback() {
		UUID parentId = UUID.randomUUID();
		PatientProfile profile = profile(parentId, "대구광역시", "중구", "성내동");
		RegionGrid exact = RegionGrid.create("27110517", "대구광역시", "중구", "성내동",
				(short) 89, (short) 90, null, null);
		when(patientProfileRepository.findById(parentId)).thenReturn(Optional.of(profile));
		when(regionGridRepository.findFirstBySidoAndSigunguAndEupMyeonDong("대구광역시", "중구", "성내동"))
				.thenReturn(Optional.of(exact));

		ParentGridService service = new ParentGridService(patientProfileRepository, regionGridRepository);
		Optional<RegionGrid> result = service.updateParentGrid(parentId);

		assertThat(result).contains(exact);
		assertThat(profile.getNx()).isEqualTo((short) 89);
		assertThat(profile.getNy()).isEqualTo((short) 90);
		verify(regionGridRepository, never()).findFallbackBySidoAndSigungu("대구광역시", "중구");
	}

	@Test
	void fallbackUsesSidoSigunguWhenDongIsMissing() {
		UUID parentId = UUID.randomUUID();
		PatientProfile profile = profile(parentId, "서울특별시", "종로구", null);
		RegionGrid fallback = RegionGrid.create("11110000", "서울특별시", "종로구", null,
				(short) 60, (short) 127, null, null);
		when(patientProfileRepository.findById(parentId)).thenReturn(Optional.of(profile));
		when(regionGridRepository.findFallbackBySidoAndSigungu("서울특별시", "종로구"))
				.thenReturn(Optional.of(fallback));

		ParentGridService service = new ParentGridService(patientProfileRepository, regionGridRepository);
		service.updateParentGrid(parentId);

		assertThat(profile.getNx()).isEqualTo((short) 60);
		assertThat(profile.getNy()).isEqualTo((short) 127);
	}

	private static PatientProfile profile(UUID parentId, String sido, String sigungu, String dong) {
		PatientProfile profile = PatientProfile.create(
				"어머니",
				LocalDate.of(1954, 1, 1),
				"F",
				sido,
				sigungu,
				dong,
				false,
				true
		);
		ReflectionTestUtils.setField(profile, "parentId", parentId);
		return profile;
	}
}
