package mamokey.mom_med.backend.domain.drug.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import mamokey.mom_med.backend.domain.drug.dto.DrugIdentifyResponse;
import mamokey.mom_med.backend.domain.drug.entity.DrugMaster;
import mamokey.mom_med.backend.domain.drug.entity.PillVisual;
import mamokey.mom_med.backend.domain.drug.repository.DrugMasterRepository;
import mamokey.mom_med.backend.domain.drug.repository.PillVisualRepository;
import mamokey.mom_med.backend.external.mfds.MfdsClient;
import mamokey.mom_med.backend.external.mfds.MfdsDrugDetailItem;
import mamokey.mom_med.backend.external.mfds.MfdsDrugListItem;
import mamokey.mom_med.backend.external.mfds.MfdsDrugListResponse;
import mamokey.mom_med.backend.external.mfds.MfdsPillVisualItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 약 식별 서비스의 분기와 캐시 정책을 검증하는 단위 테스트입니다.
 *
 * <p>실제 식약처 API를 호출하지 않고 {@link MfdsClient}를 mock으로 대체합니다.
 * 이렇게 하면 API 키 없이도 CI에서 식별 흐름, 정규화, upsert 호출 여부를 안정적으로 검증할 수 있습니다.</p>
 */
@ExtendWith(MockitoExtension.class)
class DrugIdentifyServiceTest {

	private static final Clock FIXED_CLOCK = Clock.fixed(
			Instant.parse("2026-05-22T00:00:00Z"),
			ZoneId.of("Asia/Seoul")
	);
	private static final String TYLENOL_ITEM_SEQ = "202106092";
	private static final String TYLENOL_NAME = "타이레놀정500밀리그람(아세트아미노펜)";

	@Mock
	DrugMasterRepository drugMasterRepository;

	@Mock
	PillVisualRepository pillVisualRepository;

	@Mock
	MfdsClient mfdsClient;

	DrugIdentifyService drugIdentifyService;

	@BeforeEach
	void setUp() {
		drugIdentifyService = new DrugIdentifyService(
				drugMasterRepository,
				pillVisualRepository,
				mfdsClient,
				FIXED_CLOCK
		);
	}

	@Test
	void identifiesSingleDrugAndStoresPillVisual() {
		when(drugMasterRepository.save(any(DrugMaster.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(pillVisualRepository.save(any(PillVisual.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(drugMasterRepository.findFirstByItemNameIgnoreCase(TYLENOL_NAME)).thenReturn(Optional.empty());
		when(mfdsClient.searchDrugProducts(TYLENOL_NAME))
				.thenReturn(new MfdsDrugListResponse(1, List.of(tylenolListItem())));
		when(drugMasterRepository.findById(TYLENOL_ITEM_SEQ)).thenReturn(Optional.empty());
		when(mfdsClient.getDrugDetail(TYLENOL_ITEM_SEQ)).thenReturn(tylenolDetailItem());
		when(pillVisualRepository.findById(TYLENOL_ITEM_SEQ)).thenReturn(Optional.empty());
		when(mfdsClient.searchPillVisuals(TYLENOL_NAME)).thenReturn(List.of(tylenolPillVisualItem()));

		DrugIdentifyResult result = drugIdentifyService.identify(TYLENOL_NAME);

		assertThat(result).isInstanceOf(DrugIdentifyResult.Identified.class);
		DrugIdentifyResponse response = ((DrugIdentifyResult.Identified) result).response();
		assertThat(response.itemSeq()).isEqualTo(TYLENOL_ITEM_SEQ);
		assertThat(response.mainIngrEn()).isEqualTo("Acetaminophen");
		assertThat(response.mainIngrNorm()).isEqualTo("acetaminophen");
		assertThat(response.visual().printFront()).isEqualTo("TYLENOL");
		assertThat(response.visual().printBack()).isEqualTo("500");
		verify(drugMasterRepository).save(any(DrugMaster.class));
		verify(pillVisualRepository).save(any(PillVisual.class));
	}

	@Test
	void usesFreshExactNameCacheWithoutCallingExternalApi() {
		DrugMaster cached = DrugMaster.create(TYLENOL_ITEM_SEQ);
		cached.refresh(new DrugMaster.DrugMasterRefreshValues(
				TYLENOL_NAME,
				null,
				"켄뷰코리아판매유한회사",
				null,
				null,
				"일반의약품",
				null,
				"N02BE01",
				"Acetaminophen",
				"acetaminophen",
				"흰색의 장방형 필름코팅정제",
				null,
				null,
				null,
				null
		), FIXED_CLOCK);
		when(drugMasterRepository.findFirstByItemNameIgnoreCase(TYLENOL_NAME)).thenReturn(Optional.of(cached));
		when(pillVisualRepository.findById(TYLENOL_ITEM_SEQ)).thenReturn(Optional.empty());

		DrugIdentifyResult result = drugIdentifyService.identify(TYLENOL_NAME);

		assertThat(result).isInstanceOf(DrugIdentifyResult.Identified.class);
		verifyNoInteractions(mfdsClient);
		verify(drugMasterRepository, never()).save(any());
	}

	@Test
	void returnsCandidatesWhenSearchResultIsAmbiguous() {
		when(drugMasterRepository.findFirstByItemNameIgnoreCase("노바스크")).thenReturn(Optional.empty());
		when(mfdsClient.searchDrugProducts("노바스크")).thenReturn(new MfdsDrugListResponse(2, List.of(
				new MfdsDrugListItem("200610660", "노바스크정5밀리그람", "한국화이자", "20060101", "전문의약품",
						"Amlodipine Besylate", "073400360", "biz-1", "20260408"),
				new MfdsDrugListItem("200610661", "노바스크정10밀리그람", "한국화이자", "20060101", "전문의약품",
						"Amlodipine Besylate", "073400361", "biz-2", "20260408")
		)));

		DrugIdentifyResult result = drugIdentifyService.identify("노바스크");

		assertThat(result).isInstanceOf(DrugIdentifyResult.Candidates.class);
		assertThat(((DrugIdentifyResult.Candidates) result).response().candidates()).hasSize(2);
		verify(mfdsClient, never()).getDrugDetail(any());
		verify(mfdsClient, never()).searchPillVisuals(any());
	}

	@Test
	void returnsNotFoundWhenMfdsSearchIsEmpty() {
		when(drugMasterRepository.findFirstByItemNameIgnoreCase("없는약")).thenReturn(Optional.empty());
		when(mfdsClient.searchDrugProducts("없는약")).thenReturn(new MfdsDrugListResponse(0, List.of()));

		DrugIdentifyResult result = drugIdentifyService.identify("없는약");

		assertThat(result).isInstanceOf(DrugIdentifyResult.NotFound.class);
		assertThat(((DrugIdentifyResult.NotFound) result).response().error()).isEqualTo("drug_not_found");
	}

	private static MfdsDrugListItem tylenolListItem() {
		return new MfdsDrugListItem(
				TYLENOL_ITEM_SEQ,
				TYLENOL_NAME,
				"켄뷰코리아판매유한회사",
				"20210823",
				"일반의약품",
				"Acetaminophen",
				null,
				"1068649891",
				"20260408"
		);
	}

	private static MfdsDrugDetailItem tylenolDetailItem() {
		return new MfdsDrugDetailItem(
				TYLENOL_ITEM_SEQ,
				TYLENOL_NAME,
				null,
				"켄뷰코리아판매유한회사",
				null,
				"20210823",
				"일반의약품",
				null,
				"N02BE01",
				"Acetaminophen",
				"흰색의 장방형 필름코팅정제",
				"<DOC>주의사항</DOC>",
				null,
				null,
				"20260408"
		);
	}

	private static MfdsPillVisualItem tylenolPillVisualItem() {
		return new MfdsPillVisualItem(
				TYLENOL_ITEM_SEQ,
				"https://nedrug.mfds.go.kr/pbp/cmn/itemImageDownload/1OKRXo9l4D5",
				"장방형",
				"하양",
				null,
				"TYLENOL",
				"500",
				null,
				null,
				"17.6",
				"7.1",
				"5.7",
				"필름코팅정",
				"흰색의 장방형 필름코팅정제",
				"20260408"
		);
	}
}
