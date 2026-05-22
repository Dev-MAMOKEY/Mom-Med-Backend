package mamokey.mom_med.backend.domain.drug.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import mamokey.mom_med.backend.domain.drug.dto.DrugCandidateResponse;
import mamokey.mom_med.backend.domain.drug.dto.DrugCandidatesResponse;
import mamokey.mom_med.backend.domain.drug.dto.DrugIdentifyResponse;
import mamokey.mom_med.backend.domain.drug.dto.DrugNotFoundResponse;
import mamokey.mom_med.backend.domain.drug.dto.PillVisualResponse;
import mamokey.mom_med.backend.domain.drug.entity.DrugMaster;
import mamokey.mom_med.backend.domain.drug.entity.PillVisual;
import mamokey.mom_med.backend.domain.drug.repository.DrugMasterRepository;
import mamokey.mom_med.backend.domain.drug.repository.PillVisualRepository;
import mamokey.mom_med.backend.external.mfds.MfdsClient;
import mamokey.mom_med.backend.external.mfds.MfdsDrugDetailItem;
import mamokey.mom_med.backend.external.mfds.MfdsDrugListItem;
import mamokey.mom_med.backend.external.mfds.MfdsDrugListResponse;
import mamokey.mom_med.backend.external.mfds.MfdsPillVisualItem;
import mamokey.mom_med.backend.global.util.DrugNameNormalizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Slice 01의 핵심 유스케이스인 "약 이름으로 ITEM_SEQ 식별"을 담당하는 서비스입니다.
 *
 * <p>처리 흐름은 DB exact 캐시 확인, 식약처 목록 API 조회, 동명이품 분기, 상세 API와
 * 낱알식별 API 보강, ref schema upsert 순서입니다. 이 결과로 저장된
 * {@code ref.drugs_master.main_ingr_norm}은 Slice 02 DUR 정확 매칭의 조인 키가 됩니다.</p>
 */
@Service
@Transactional(readOnly = true)
public class DrugIdentifyService {

	private static final int MAX_CANDIDATE_COUNT = 5;
	private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.BASIC_ISO_DATE;
	private static final DateTimeFormatter DASHED_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

	private final DrugMasterRepository drugMasterRepository;
	private final PillVisualRepository pillVisualRepository;
	private final MfdsClient mfdsClient;
	private final Clock clock;

	@Autowired
	public DrugIdentifyService(
			DrugMasterRepository drugMasterRepository,
			PillVisualRepository pillVisualRepository,
			MfdsClient mfdsClient
	) {
		this(drugMasterRepository, pillVisualRepository, mfdsClient, Clock.system(ZoneId.of("Asia/Seoul")));
	}

	DrugIdentifyService(
			DrugMasterRepository drugMasterRepository,
			PillVisualRepository pillVisualRepository,
			MfdsClient mfdsClient,
			Clock clock
	) {
		this.drugMasterRepository = drugMasterRepository;
		this.pillVisualRepository = pillVisualRepository;
		this.mfdsClient = mfdsClient;
		this.clock = clock;
	}

	/**
	 * 사용자 입력 약 이름을 식약처 ITEM_SEQ 기준으로 식별합니다.
	 *
	 * <p>같은 제품명을 다시 조회하면 DB 캐시를 먼저 반환하므로 외부 API 호출 수를 줄일 수 있습니다.
	 * 캐시에 없거나 TTL이 지난 경우에만 식약처 API를 호출합니다.</p>
	 */
	@Transactional
	public DrugIdentifyResult identify(String inputName) {
		String trimmedInput = normalizeInput(inputName);
		if (trimmedInput.isBlank()) {
			return notFound(trimmedInput);
		}

		Optional<DrugMaster> exactCached = drugMasterRepository.findFirstByItemNameIgnoreCase(trimmedInput)
				.filter(drugMaster -> drugMaster.isFresh(null, clock));
		if (exactCached.isPresent()) {
			DrugMaster drugMaster = exactCached.get();
			return identified(drugMaster, pillVisualRepository.findById(drugMaster.getItemSeq()).orElse(null));
		}

		MfdsDrugListResponse listResponse = mfdsClient.searchDrugProducts(trimmedInput);
		if (listResponse.totalCount() == 0 || listResponse.items().isEmpty()) {
			return notFound(trimmedInput);
		}

		Optional<MfdsDrugListItem> selected = selectSingleCandidate(trimmedInput, listResponse);
		if (selected.isEmpty()) {
			return candidates(listResponse.items());
		}

		MfdsDrugListItem listItem = selected.get();
		LocalDate listChangeDate = parseDate(listItem.changeDate());
		Optional<DrugMaster> cached = drugMasterRepository.findById(listItem.itemSeq());
		if (cached.isPresent() && cached.get().isFresh(listChangeDate, clock)) {
			return identified(cached.get(), pillVisualRepository.findById(listItem.itemSeq()).orElse(null));
		}

		DrugMaster refreshedDrug = refreshDrugMaster(listItem);
		PillVisual refreshedVisual = refreshPillVisual(refreshedDrug, listItem).orElse(null);
		return identified(refreshedDrug, refreshedVisual);
	}

	private DrugMaster refreshDrugMaster(MfdsDrugListItem listItem) {
		MfdsDrugDetailItem detail = mfdsClient.getDrugDetail(listItem.itemSeq());
		DrugMaster drugMaster = drugMasterRepository.findById(listItem.itemSeq())
				.orElseGet(() -> DrugMaster.create(listItem.itemSeq()));

		String mainIngrEn = firstNonBlank(detail.mainIngrEng(), listItem.itemIngrName());
		drugMaster.refresh(new DrugMaster.DrugMasterRefreshValues(
				firstNonBlank(detail.itemName(), listItem.itemName()),
				trimToNull(detail.itemNameEng()),
				firstNonBlank(detail.entpName(), listItem.entpName()),
				trimToNull(detail.entpNo()),
				parseDate(firstNonBlank(detail.itemPermitDate(), listItem.itemPermitDate())),
				firstNonBlank(detail.specialtyType(), listItem.specialtyType()),
				firstNonBlank(detail.ediCode(), listItem.ediCode()),
				trimToNull(detail.atcCode()),
				trimToNull(mainIngrEn),
				DrugNameNormalizer.normalize(mainIngrEn),
				trimToNull(detail.chart()),
				trimToNull(detail.nbDocData()),
				trimToNull(detail.eeDocData()),
				trimToNull(detail.udDocData()),
				parseDate(firstNonBlank(detail.changeDate(), listItem.changeDate()))
		), clock);

		return drugMasterRepository.save(drugMaster);
	}

	private Optional<PillVisual> refreshPillVisual(DrugMaster drugMaster, MfdsDrugListItem listItem) {
		String searchName = firstNonBlank(drugMaster.getItemName(), listItem.itemName());
		return mfdsClient.searchPillVisuals(searchName).stream()
				.filter(item -> Objects.equals(item.itemSeq(), drugMaster.getItemSeq()))
				.filter(item -> trimToNull(item.itemImage()) != null)
				.findFirst()
				.map(item -> savePillVisual(drugMaster.getItemSeq(), item));
	}

	private PillVisual savePillVisual(String itemSeq, MfdsPillVisualItem item) {
		PillVisual pillVisual = pillVisualRepository.findById(itemSeq)
				.orElseGet(() -> PillVisual.create(itemSeq));
		pillVisual.refresh(new PillVisual.PillVisualRefreshValues(
				trimToNull(item.itemImage()),
				trimToNull(item.drugShape()),
				trimToNull(item.colorPrimary()),
				trimToNull(item.colorSecondary()),
				trimToNull(item.printFront()),
				trimToNull(item.printBack()),
				trimToNull(item.lineFront()),
				trimToNull(item.lineBack()),
				parseDecimal(item.lengthLong()),
				parseDecimal(item.lengthShort()),
				parseDecimal(item.thickness()),
				trimToNull(item.formName()),
				trimToNull(item.chart()),
				parseDate(item.changeDate())
		), clock);
		return pillVisualRepository.save(pillVisual);
	}

	private Optional<MfdsDrugListItem> selectSingleCandidate(String inputName, MfdsDrugListResponse listResponse) {
		if (listResponse.totalCount() == 1 && !listResponse.items().isEmpty()) {
			return Optional.of(listResponse.items().getFirst());
		}

		List<MfdsDrugListItem> exactMatches = listResponse.items().stream()
				.filter(item -> comparableName(item.itemName()).equals(comparableName(inputName)))
				.toList();
		if (exactMatches.size() == 1) {
			return Optional.of(exactMatches.getFirst());
		}
		return Optional.empty();
	}

	private DrugIdentifyResult identified(DrugMaster drugMaster, PillVisual pillVisual) {
		return new DrugIdentifyResult.Identified(new DrugIdentifyResponse(
				drugMaster.getItemSeq(),
				drugMaster.getItemName(),
				drugMaster.getMainIngrEn(),
				drugMaster.getMainIngrNorm(),
				drugMaster.getAtcCode(),
				drugMaster.getEdiCode(),
				drugMaster.getSpecialtyType(),
				toVisualResponse(pillVisual)
		));
	}

	private PillVisualResponse toVisualResponse(PillVisual pillVisual) {
		if (pillVisual == null) {
			return null;
		}
		return new PillVisualResponse(
				pillVisual.getImageUrl(),
				pillVisual.getDrugShape(),
				pillVisual.getColorPrimary(),
				pillVisual.getColorSecondary(),
				pillVisual.getPrintFront(),
				pillVisual.getPrintBack(),
				Arrays.asList(pillVisual.getLengthLongMm(), pillVisual.getLengthShortMm(), pillVisual.getThicknessMm()),
				pillVisual.getFormName()
		);
	}

	private DrugIdentifyResult candidates(List<MfdsDrugListItem> items) {
		List<DrugCandidateResponse> candidates = items.stream()
				.limit(MAX_CANDIDATE_COUNT)
				.map(item -> new DrugCandidateResponse(
						item.itemSeq(),
						item.itemName(),
						item.entpName(),
						item.specialtyType(),
						item.ediCode()
				))
				.toList();
		return new DrugIdentifyResult.Candidates(new DrugCandidatesResponse(candidates));
	}

	private DrugIdentifyResult notFound(String inputName) {
		return new DrugIdentifyResult.NotFound(DrugNotFoundResponse.of(inputName));
	}

	private static String normalizeInput(String inputName) {
		return inputName == null ? "" : inputName.strip();
	}

	private static String comparableName(String value) {
		String source = value == null ? "" : value;
		return source.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
	}

	private static String firstNonBlank(String first, String fallback) {
		String normalizedFirst = trimToNull(first);
		if (normalizedFirst != null) {
			return normalizedFirst;
		}
		String normalizedFallback = trimToNull(fallback);
		return normalizedFallback == null ? "" : normalizedFallback;
	}

	private static String trimToNull(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.strip();
	}

	private static LocalDate parseDate(String value) {
		String normalized = trimToNull(value);
		if (normalized == null) {
			return null;
		}
		try {
			return normalized.contains("-")
					? LocalDate.parse(normalized, DASHED_DATE)
					: LocalDate.parse(normalized, BASIC_DATE);
		}
		catch (DateTimeParseException ignored) {
			return null;
		}
	}

	private static BigDecimal parseDecimal(String value) {
		String normalized = trimToNull(value);
		if (normalized == null) {
			return null;
		}
		try {
			return new BigDecimal(normalized.replace(",", ""));
		}
		catch (NumberFormatException ignored) {
			return null;
		}
	}
}
