package mamokey.mom_med.backend.domain.weather.service;

import java.util.Optional;
import java.util.UUID;

import mamokey.mom_med.backend.domain.weather.entity.RegionGrid;
import mamokey.mom_med.backend.domain.weather.repository.RegionGridRepository;
import mamokey.mom_med.backend.global.exception.CustomException;
import mamokey.mom_med.backend.global.exception.ErrorCode;
import mamokey.mom_med.backend.parent.domain.PatientProfile;
import mamokey.mom_med.backend.parent.repository.PatientProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 부모 주소를 기상청 격자 좌표(nx, ny)로 변환하는 서비스입니다.
 *
 * <p>1순위는 시/군/구/동 정확 매칭입니다. 같은 시/군/구 안에서도 동마다 격자가 달라질 수 있기 때문입니다.
 * 동 매칭에 실패하면 2순위로 시/군/구 fallback을 사용해 최소한의 기상청 조회 좌표를 채웁니다.</p>
 */
@Service
public class ParentGridService {

	private final PatientProfileRepository patientProfileRepository;
	private final RegionGridRepository regionGridRepository;

	public ParentGridService(
			PatientProfileRepository patientProfileRepository,
			RegionGridRepository regionGridRepository
	) {
		this.patientProfileRepository = patientProfileRepository;
		this.regionGridRepository = regionGridRepository;
	}

	@Transactional
	public Optional<RegionGrid> updateParentGrid(UUID parentId) {
		PatientProfile profile = patientProfileRepository.findById(parentId)
				.orElseThrow(() -> new CustomException(ErrorCode.PARENT_NOT_FOUND));
		Optional<RegionGrid> grid = findBestGrid(profile);
		grid.ifPresent(matched -> profile.updateGrid(matched.getNx(), matched.getNy()));
		return grid;
	}

	private Optional<RegionGrid> findBestGrid(PatientProfile profile) {
		if (isBlank(profile.getAddressSido()) || isBlank(profile.getAddressSigungu())) {
			return Optional.empty();
		}

		if (!isBlank(profile.getAddressDong())) {
			Optional<RegionGrid> exact = regionGridRepository.findFirstBySidoAndSigunguAndEupMyeonDong(
					profile.getAddressSido(),
					profile.getAddressSigungu(),
					profile.getAddressDong()
			);
			if (exact.isPresent()) {
				return exact;
			}
		}

		return regionGridRepository.findFallbackBySidoAndSigungu(
				profile.getAddressSido(),
				profile.getAddressSigungu()
		);
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
