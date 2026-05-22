package mamokey.mom_med.backend.external.mfds;

import java.util.List;
import java.util.function.Supplier;

import mamokey.mom_med.backend.global.exception.CustomException;
import mamokey.mom_med.backend.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * 식약처(data.go.kr) 의약품 API 호출을 담당하는 클라이언트입니다.
 *
 * <p>Slice 01 서비스는 식약처 URL, query parameter, 응답 envelope 구조를 직접 알 필요 없이
 * 이 클라이언트의 세 가지 메서드만 사용합니다.</p>
 */
@Component
public class MfdsClient {

	private static final String API_BASE_URL = "https://apis.data.go.kr";
	private static final String DRUG_LIST_PATH = "/1471000/DrugPrdtPrmsnInfoService07/getDrugPrdtPrmsnInq07";
	private static final String DRUG_DETAIL_PATH = "/1471000/DrugPrdtPrmsnInfoService07/getDrugPrdtPrmsnDtlInq06";
	private static final String PILL_VISUAL_PATH = "/1471000/MdcinGrnIdntfcInfoService03/getMdcinGrnIdntfcInfoList03";

	private final RestClient restClient;
	private final String apiKey;

	public MfdsClient(
			RestClient.Builder restClientBuilder,
			@Value("${external.mfds.api-key:}") String apiKey
	) {
		this.restClient = restClientBuilder.baseUrl(API_BASE_URL).build();
		this.apiKey = apiKey;
	}

	/**
	 * 사용자 입력 약 이름으로 식약처 제품 허가정보 목록을 조회합니다.
	 */
	public MfdsDrugListResponse searchDrugProducts(String itemName) {
		validateApiKey();
		MfdsDrugListApiResponse response = executeWithSingleRetry(() -> restClient.get()
				.uri(uriBuilder -> uriBuilder.path(DRUG_LIST_PATH)
						.queryParam("serviceKey", apiKey)
						.queryParam("pageNo", 1)
						.queryParam("numOfRows", 10)
						.queryParam("type", "json")
						.queryParam("item_name", itemName)
						.build())
				.retrieve()
				.body(MfdsDrugListApiResponse.class));

		return new MfdsDrugListResponse(totalCount(response), listItems(response));
	}

	/**
	 * 목록 API에서 선택한 ITEM_SEQ로 상세 허가정보를 조회합니다.
	 */
	public MfdsDrugDetailItem getDrugDetail(String itemSeq) {
		validateApiKey();
		MfdsDrugDetailApiResponse response = executeWithSingleRetry(() -> restClient.get()
				.uri(uriBuilder -> uriBuilder.path(DRUG_DETAIL_PATH)
						.queryParam("serviceKey", apiKey)
						.queryParam("pageNo", 1)
						.queryParam("numOfRows", 1)
						.queryParam("type", "json")
						.queryParam("item_seq", itemSeq)
						.build())
				.retrieve()
				.body(MfdsDrugDetailApiResponse.class));

		return detailItems(response).stream()
				.findFirst()
				.orElseThrow(() -> new CustomException(ErrorCode.DRUG_NOT_FOUND));
	}

	/**
	 * 낱알식별 API는 item_seq 직접 검색이 느릴 수 있어 item_name으로 조회한 뒤 서비스에서 ITEM_SEQ로 필터링합니다.
	 */
	public List<MfdsPillVisualItem> searchPillVisuals(String itemName) {
		validateApiKey();
		MfdsPillVisualApiResponse response = executeWithSingleRetry(() -> restClient.get()
				.uri(uriBuilder -> uriBuilder.path(PILL_VISUAL_PATH)
						.queryParam("serviceKey", apiKey)
						.queryParam("pageNo", 1)
						.queryParam("numOfRows", 100)
						.queryParam("type", "json")
						.queryParam("item_name", itemName)
						.build())
				.retrieve()
				.body(MfdsPillVisualApiResponse.class));

		return pillItems(response);
	}

	private void validateApiKey() {
		if (apiKey == null || apiKey.isBlank()) {
			throw new CustomException(ErrorCode.EXTERNAL_API_ERROR, "MFDS_API_KEY가 설정되어 있지 않습니다.");
		}
	}

	private <T> T executeWithSingleRetry(Supplier<T> request) {
		for (int attempt = 0; attempt < 2; attempt++) {
			try {
				return request.get();
			}
			catch (RestClientResponseException exception) {
				if (exception.getStatusCode().is5xxServerError() && attempt == 0) {
					continue;
				}
				throw new CustomException(ErrorCode.EXTERNAL_API_ERROR,
						"식약처 API 호출에 실패했습니다: " + exception.getStatusCode());
			}
			catch (RestClientException exception) {
				throw new CustomException(ErrorCode.EXTERNAL_API_ERROR, "식약처 API 호출에 실패했습니다.");
			}
		}

		throw new CustomException(ErrorCode.EXTERNAL_API_ERROR, "식약처 API 호출 재시도에 실패했습니다.");
	}

	private static int totalCount(MfdsDrugListApiResponse response) {
		if (response == null || response.body() == null || response.body().totalCount() == null) {
			return 0;
		}
		return response.body().totalCount();
	}

	private static List<MfdsDrugListItem> listItems(MfdsDrugListApiResponse response) {
		if (response == null || response.body() == null || response.body().items() == null) {
			return List.of();
		}
		return response.body().items();
	}

	private static List<MfdsDrugDetailItem> detailItems(MfdsDrugDetailApiResponse response) {
		if (response == null || response.body() == null || response.body().items() == null) {
			return List.of();
		}
		return response.body().items();
	}

	private static List<MfdsPillVisualItem> pillItems(MfdsPillVisualApiResponse response) {
		if (response == null || response.body() == null || response.body().items() == null) {
			return List.of();
		}
		return response.body().items();
	}
}
