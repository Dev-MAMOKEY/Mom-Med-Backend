package mamokey.mom_med.backend.domain.nb.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import mamokey.mom_med.backend.domain.drug.entity.DrugMaster;
import mamokey.mom_med.backend.domain.drug.repository.DrugMasterRepository;
import mamokey.mom_med.backend.domain.nb.entity.NbExtraction;
import mamokey.mom_med.backend.domain.nb.entity.NbInteraction;
import mamokey.mom_med.backend.domain.nb.prompt.NbExtractionPrompt;
import mamokey.mom_med.backend.domain.nb.repository.NbExtractionRepository;
import mamokey.mom_med.backend.domain.nb.repository.NbInteractionRepository;
import mamokey.mom_med.backend.domain.nb.util.DrugGroupDictionary;
import mamokey.mom_med.backend.domain.nb.util.NbDocDataCleaner;
import mamokey.mom_med.backend.global.exception.CustomException;
import mamokey.mom_med.backend.global.exception.ErrorCode;
import mamokey.mom_med.backend.global.util.DrugNameNormalizer;
import mamokey.mom_med.backend.global.util.HallucinationVerifier;
import mamokey.mom_med.backend.infra.llm.GeminiClient;
import mamokey.mom_med.backend.infra.llm.LLMResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * NB_DOC_DATA를 LLM으로 구조화 추출하고 검증된 결과를 저장하는 서비스입니다.
 *
 * <p>캐시 키는 item_seq + drug_change_date + llm_model + prompt_version입니다.
 * 캐시가 verified=true면 Gemini를 다시 호출하지 않고 DB interaction을 반환합니다.
 * 캐시가 없으면 prompt 생성, Gemini JSON mode 호출, source_quote 환각 검증, 1회 재시도, upsert 저장을 수행합니다.</p>
 */
@Service
@Transactional
public class NbExtractionService {

	private static final String DEFAULT_MODEL = "gemini-2.5-flash-lite";

	private final DrugMasterRepository drugMasterRepository;
	private final NbExtractionRepository nbExtractionRepository;
	private final NbInteractionRepository nbInteractionRepository;
	private final GeminiClient geminiClient;
	private final ObjectMapper objectMapper;

	public NbExtractionService(
			DrugMasterRepository drugMasterRepository,
			NbExtractionRepository nbExtractionRepository,
			NbInteractionRepository nbInteractionRepository,
			GeminiClient geminiClient,
			ObjectMapper objectMapper
	) {
		this.drugMasterRepository = drugMasterRepository;
		this.nbExtractionRepository = nbExtractionRepository;
		this.nbInteractionRepository = nbInteractionRepository;
		this.geminiClient = geminiClient;
		this.objectMapper = objectMapper;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public NbExtractionResult extract(String itemSeq) {
		DrugMaster drug = findDrugOrThrow(itemSeq);
		NbExtraction cached = nbExtractionRepository
				.findByItemSeqAndDrugChangeDateAndLlmModelAndPromptVersion(
						drug.getItemSeq(),
						drug.getSourceChangeDate(),
						DEFAULT_MODEL,
						NbExtractionPrompt.PROMPT_VERSION
				)
				.orElse(null);

		if (cached != null && cached.isVerified()) {
			return new NbExtractionResult(
					cached,
					nbInteractionRepository.findByExtractionIdOrderByIdAsc(cached.getId()),
					true
			);
		}

		String cleanedNbDocData = NbDocDataCleaner.clean(drug.getNbDocData());
		if (cleanedNbDocData.isBlank()) {
			throw new CustomException(ErrorCode.INVALID_INPUT, "NB_DOC_DATA가 비어 있어 추출할 수 없습니다: " + itemSeq);
		}

		return extractWithRetry(drug, cleanedNbDocData, cached, false);
	}

	@Transactional(readOnly = true)
	public NbExtractionResult findVerified(String itemSeq) {
		NbExtraction extraction = nbExtractionRepository.findFirstByItemSeqAndVerifiedTrueOrderByIdDesc(itemSeq)
				.orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "검증된 NB 추출 결과가 없습니다: " + itemSeq));
		return new NbExtractionResult(
				extraction,
				nbInteractionRepository.findByExtractionIdOrderByIdAsc(extraction.getId()),
				true
		);
	}

	/**
	 * SafetyJudge에서 "이미 검증된 NB 캐시만 있으면 사용"해야 할 때 사용하는 조회 메서드입니다.
	 *
	 * <p>{@link #findVerified(String)}처럼 예외를 던지면 상위 트랜잭션이 rollback-only로 표시될 수 있습니다.
	 * safety/check에서는 NB 캐시가 없다는 사실이 오류가 아니라 "NB evidence 없음"에 가까우므로 Optional로 반환합니다.</p>
	 */
	@Transactional(readOnly = true)
	public Optional<NbExtractionResult> findVerifiedOptional(String itemSeq) {
		return nbExtractionRepository.findFirstByItemSeqAndVerifiedTrueOrderByIdDesc(itemSeq)
				.map(extraction -> new NbExtractionResult(
						extraction,
						nbInteractionRepository.findByExtractionIdOrderByIdAsc(extraction.getId()),
						true
				));
	}

	private NbExtractionResult extractWithRetry(
			DrugMaster drug,
			String cleanedNbDocData,
			NbExtraction cached,
			boolean retried
	) {
		String prompt = NbExtractionPrompt.build(drug, cleanedNbDocData);
		LLMResponse llmResponse = geminiClient.callJsonExtraction(prompt, NbExtractionPrompt.PROMPT_VERSION);
		Map<String, Object> extractionJson = parseJson(llmResponse.text());

		HallucinationVerifier.VerifyResult verifyResult = HallucinationVerifier.verify(
				toVerificationMap(drug.getItemSeq(), extractionJson),
				Map.of(drug.getItemSeq(), cleanedNbDocData)
		);

		if (verifyResult.hallucinated() > 0 && !retried) {
			return extractWithRetry(drug, cleanedNbDocData, cached, true);
		}

		boolean verified = verifyResult.hallucinated() == 0;
		NbExtraction extraction = cached == null
				? NbExtraction.create(drug.getItemSeq(), drug.getSourceChangeDate(), llmResponse.model(), llmResponse.promptVersion())
				: cached;
		extraction.refresh(
				extractionJson,
				verified,
				verificationSummary(verifyResult),
				llmResponse.inputTokens(),
				llmResponse.outputTokens(),
				BigDecimal.valueOf(llmResponse.costUsd())
		);

		NbExtraction saved = nbExtractionRepository.saveAndFlush(extraction);
		nbInteractionRepository.deleteByExtractionId(saved.getId());

		List<NbInteraction> interactions = verified
				? nbInteractionRepository.saveAll(toInteractions(saved, extractionJson))
				: List.of();
		return new NbExtractionResult(saved, interactions, false);
	}

	private DrugMaster findDrugOrThrow(String itemSeq) {
		return drugMasterRepository.findById(itemSeq)
				.orElseThrow(() -> new CustomException(ErrorCode.DRUG_NOT_FOUND, "약 마스터를 찾을 수 없습니다: " + itemSeq));
	}

	private Map<String, Object> parseJson(String text) {
		try {
			return objectMapper.readValue(text, new TypeReference<>() {
			});
		}
		catch (Exception exception) {
			throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "Gemini NB 추출 JSON 파싱에 실패했습니다.");
		}
	}

	@SuppressWarnings("unchecked")
	private List<NbInteraction> toInteractions(NbExtraction extraction, Map<String, Object> extractionJson) {
		String drugName = stringValue(extractionJson.get("drug"));
		Object rawInteractions = extractionJson.get("interactions");
		if (!(rawInteractions instanceof List<?> rows)) {
			return List.of();
		}

		List<NbInteraction> interactions = new ArrayList<>();
		for (Object row : rows) {
			if (!(row instanceof Map<?, ?> map)) {
				continue;
			}
			String entryType = stringValue(map.get("entry_type"));
			String riskLevel = stringValue(map.get("risk_level"));
			String reasonSummary = stringValue(map.get("reason_summary"));
			String sourceQuote = stringValue(map.get("source_quote"));

			// patient_class entry (Slice 05 확장)
			if (NbInteraction.ENTRY_TYPE_PATIENT_CLASS.equals(entryType)) {
				String patientClassText = stringValue(map.get("patient_class_text"));
				String patientClassKcd = stringValue(map.get("patient_class_kcd"));
				if (patientClassText != null && riskLevel != null) {
					interactions.add(NbInteraction.patientClass(
							extraction.getId(),
							extraction.getItemSeq(),
							drugName == null ? extraction.getItemSeq() : drugName,
							patientClassText,
							patientClassKcd,
							riskLevel,
							reasonSummary,
							sourceQuote
					));
				}
				continue;
			}

			// drug_drug entry (기본값: entry_type 없거나 "drug_drug")
			String partnerKo = stringValue(map.get("partner_drug_ko"));
			String partnerEn = stringValue(map.get("partner_drug_en"));
			if (partnerKo == null || riskLevel == null) {
				continue;
			}

			interactions.add(NbInteraction.drugDrug(
					extraction.getId(),
					extraction.getItemSeq(),
					drugName == null ? extraction.getItemSeq() : drugName,
					partnerKo,
					normalizedPartner(partnerKo, partnerEn),
					partnerEn,
					DrugGroupDictionary.isDrugGroupName(partnerKo),
					riskLevel,
					reasonSummary,
					sourceQuote
			));
		}
		return interactions;
	}

	private Map<String, Object> toVerificationMap(String itemSeq, Map<String, Object> extractionJson) {
		Map<String, Object> verificationRoot = new LinkedHashMap<>();
		Object rawInteractions = extractionJson.get("interactions");
		if (!(rawInteractions instanceof List<?> rows)) {
			verificationRoot.put("interactions", List.of());
			return verificationRoot;
		}

		List<Map<String, Object>> wrapped = new ArrayList<>();
		for (Object row : rows) {
			if (!(row instanceof Map<?, ?> map)) {
				continue;
			}
			Map<String, Object> citation = new LinkedHashMap<>();
			citation.put("file", itemSeq);
			citation.put("quote", stringValue(map.get("source_quote")));
			wrapped.add(Map.of("source_citation", citation));
		}
		verificationRoot.put("interactions", wrapped);
		return verificationRoot;
	}

	private Map<String, Object> verificationSummary(HallucinationVerifier.VerifyResult verifyResult) {
		return Map.of(
				"total", verifyResult.total(),
				"exact", verifyResult.exact(),
				"normalized", verifyResult.normalized(),
				"fuzzy", verifyResult.fuzzy(),
				"hallucinated", verifyResult.hallucinated()
		);
	}

	private static String stringValue(Object value) {
		if (value == null) {
			return null;
		}
		String text = value.toString();
		return text.isBlank() ? null : text;
	}

	private static String normalizedPartner(String partnerKo, String partnerEn) {
		if (partnerEn != null && !partnerEn.isBlank()) {
			return DrugNameNormalizer.normalize(partnerEn);
		}
		return DrugNameNormalizer.normalize(partnerKo);
	}
}
