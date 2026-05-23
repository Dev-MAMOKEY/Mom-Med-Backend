package mamokey.mom_med.backend.domain.nb.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.StreamSupport;

import com.fasterxml.jackson.databind.ObjectMapper;
import mamokey.mom_med.backend.domain.drug.entity.DrugMaster;
import mamokey.mom_med.backend.domain.drug.repository.DrugMasterRepository;
import mamokey.mom_med.backend.domain.nb.entity.NbExtraction;
import mamokey.mom_med.backend.domain.nb.entity.NbInteraction;
import mamokey.mom_med.backend.domain.nb.prompt.NbExtractionPrompt;
import mamokey.mom_med.backend.domain.nb.repository.NbExtractionRepository;
import mamokey.mom_med.backend.domain.nb.repository.NbInteractionRepository;
import mamokey.mom_med.backend.infra.llm.GeminiClient;
import mamokey.mom_med.backend.infra.llm.LLMResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * NB 추출 서비스의 캐시, 환각 검증, interaction 변환 흐름을 검증합니다.
 *
 * <p>테스트에서는 GeminiClient를 mock으로 대체합니다. 이렇게 하면 실제 API 키나 네트워크 없이도
 * "캐시가 있으면 LLM을 호출하지 않는지", "검증된 source_quote만 interaction으로 저장되는지"를 확인할 수 있습니다.</p>
 */
@ExtendWith(MockitoExtension.class)
class NbExtractionServiceTest {

	private static final Clock FIXED_CLOCK = Clock.fixed(
			Instant.parse("2026-05-23T00:00:00Z"),
			ZoneId.of("Asia/Seoul")
	);

	@Mock
	DrugMasterRepository drugMasterRepository;

	@Mock
	NbExtractionRepository nbExtractionRepository;

	@Mock
	NbInteractionRepository nbInteractionRepository;

	@Mock
	GeminiClient geminiClient;

	NbExtractionService nbExtractionService;

	@BeforeEach
	void setUp() {
		nbExtractionService = new NbExtractionService(
				drugMasterRepository,
				nbExtractionRepository,
				nbInteractionRepository,
				geminiClient,
				new ObjectMapper()
		);
	}

	@Test
	void returnsVerifiedCacheWithoutCallingGemini() {
		DrugMaster drug = drugMaster("AMLO001", "amlodipine", "주의사항 원문");
		NbExtraction cached = NbExtraction.create(
				drug.getItemSeq(),
				drug.getSourceChangeDate(),
				"gemini-2.5-flash-lite",
				NbExtractionPrompt.PROMPT_VERSION
		);
		cached.refresh(
				Map.of("drug", "암로디핀", "interactions", List.of()),
				true,
				Map.of("hallucinated", 0),
				100,
				20,
				BigDecimal.ZERO
		);
		when(drugMasterRepository.findById("AMLO001")).thenReturn(Optional.of(drug));
		when(nbExtractionRepository.findByItemSeqAndDrugChangeDateAndLlmModelAndPromptVersion(
				drug.getItemSeq(),
				drug.getSourceChangeDate(),
				"gemini-2.5-flash-lite",
				NbExtractionPrompt.PROMPT_VERSION
		)).thenReturn(Optional.of(cached));
		when(nbInteractionRepository.findByExtractionIdOrderByIdAsc(cached.getId())).thenReturn(List.of());

		NbExtractionResult result = nbExtractionService.extract("AMLO001");

		assertThat(result.fromCache()).isTrue();
		verify(geminiClient, never()).callJsonExtraction(any(), any());
	}

	@Test
	@SuppressWarnings("unchecked")
	void extractsVerifiedInteractionAndNormalizesEnglishPartnerName() {
		String quote = "암로디핀 10 mg과 심바스타틴 80 mg의 다회용량 병용투여는 심바스타틴 노출을 증가시켰다.";
		DrugMaster drug = drugMaster("AMLO001", "amlodipine", "<DOC>" + quote + "</DOC>");
		String llmJson = """
				{
				  "drug": "암로디핀",
				  "interactions": [
				    {
				      "partner_drug_ko": "심바스타틴",
				      "partner_drug_en": "simvastatin",
				      "risk_level": "주의",
				      "reason_summary": "심바스타틴 노출 증가",
				      "source_quote": "%s"
				    }
				  ]
				}
				""".formatted(quote);
		when(drugMasterRepository.findById("AMLO001")).thenReturn(Optional.of(drug));
		when(nbExtractionRepository.findByItemSeqAndDrugChangeDateAndLlmModelAndPromptVersion(
				drug.getItemSeq(),
				drug.getSourceChangeDate(),
				"gemini-2.5-flash-lite",
				NbExtractionPrompt.PROMPT_VERSION
		)).thenReturn(Optional.empty());
		when(geminiClient.callJsonExtraction(any(), any()))
				.thenReturn(new LLMResponse(llmJson, 1200, 300, 0.001, "gemini-2.5-flash-lite", "v1"));
		when(nbExtractionRepository.saveAndFlush(any(NbExtraction.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
		when(nbInteractionRepository.saveAll(any()))
				.thenAnswer(invocation -> StreamSupport.stream(((Iterable<NbInteraction>) invocation.getArgument(0)).spliterator(), false).toList());

		NbExtractionResult result = nbExtractionService.extract("AMLO001");

		assertThat(result.fromCache()).isFalse();
		assertThat(result.extraction().isVerified()).isTrue();
		assertThat(result.interactions()).singleElement()
				.satisfies(interaction -> {
					assertThat(interaction.getPartnerDrugKo()).isEqualTo("심바스타틴");
					assertThat(interaction.getPartnerDrugNorm()).isEqualTo("simvastatin");
					assertThat(interaction.getRiskLevel()).isEqualTo("주의");
				});
	}

	private static DrugMaster drugMaster(String itemSeq, String ingredientNorm, String nbDocData) {
		DrugMaster drug = DrugMaster.create(itemSeq);
		drug.refresh(new DrugMaster.DrugMasterRefreshValues(
				itemSeq + " name",
				null,
				"test",
				null,
				null,
				"전문의약품",
				null,
				"C08CA01",
				ingredientNorm,
				ingredientNorm,
				null,
				nbDocData,
				null,
				null,
				LocalDate.of(2026, 5, 23)
		), FIXED_CLOCK);
		return drug;
	}
}
