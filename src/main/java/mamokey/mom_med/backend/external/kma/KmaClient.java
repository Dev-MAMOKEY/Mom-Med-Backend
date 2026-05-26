package mamokey.mom_med.backend.external.kma;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * 기상청 단기예보조회서비스(getVilageFcst) HTTP 클라이언트 (Slice 07).
 *
 * <p>API 키가 비어 있거나 호출에 실패하면 빈 리스트를 반환해 앱 기동에 영향을 주지 않습니다.
 * ETL 스케줄러는 base_date/base_time을 명시적으로 전달하므로 날짜 필터 없이 원본 아이템을 반환합니다.</p>
 */
@Component
public class KmaClient {

    private static final Logger log = LoggerFactory.getLogger(KmaClient.class);
    private static final String BASE_URL = "https://apis.data.go.kr";
    private static final String PATH = "/1360000/VilageFcstInfoService_2.0/getVilageFcst";

    private final RestClient restClient;
    private final String apiKey;

    public KmaClient(
            RestClient.Builder restClientBuilder,
            @Value("${external.kma.api-key:}") String apiKey
    ) {
        this.restClient = restClientBuilder.baseUrl(BASE_URL).build();
        this.apiKey = apiKey;
    }

    /**
     * 지정된 base_date/base_time으로 예보를 조회합니다.
     * 날짜 필터 없이 원본 아이템을 전부 반환하므로 호출자가 TMX/TMN 날짜를 선택합니다.
     *
     * @param nx       기상청 격자 X
     * @param ny       기상청 격자 Y
     * @param baseDate 발표일자 (yyyyMMdd)
     * @param baseTime 발표시각 (HHmm, 예: "0500")
     * @return 예보 아이템 전체 (실패 시 빈 리스트)
     */
    public List<KmaFcstItem> getRawForecast(short nx, short ny, String baseDate, String baseTime) {
        if (apiKey == null || apiKey.isBlank()) {
            return List.of();
        }
        try {
            KmaVilageFcstResponse response = restClient.get()
                    .uri(b -> b.path(PATH)
                            .queryParam("serviceKey", apiKey)
                            .queryParam("numOfRows", 900)
                            .queryParam("pageNo", 1)
                            .queryParam("dataType", "JSON")
                            .queryParam("base_date", baseDate)
                            .queryParam("base_time", baseTime)
                            .queryParam("nx", nx)
                            .queryParam("ny", ny)
                            .build())
                    .retrieve()
                    .body(KmaVilageFcstResponse.class);

            if (response == null || !"00".equals(response.resultCode())) {
                log.warn("KMA API 비정상 응답. nx={}, ny={}, resultCode={}",
                        nx, ny, response != null ? response.resultCode() : "null");
                return List.of();
            }
            return response.items();
        } catch (Exception e) {
            log.warn("KMA API 호출 실패. nx={}, ny={}: {}", nx, ny, e.getMessage());
            return List.of();
        }
    }
}
