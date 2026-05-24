package mamokey.mom_med.backend.external.hira;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import mamokey.mom_med.backend.global.exception.CustomException;
import mamokey.mom_med.backend.global.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * 건강보험심사평가원(HIRA) 질병정보서비스 API 클라이언트입니다 (Slice 05).
 *
 * <p>sno 12904 — getDissNameCodeList1 엔드포인트 호출.
 * 응답은 JSON 미지원 XML 전용이므로, 표준 Java DOM 파서로 직접 파싱합니다.</p>
 *
 * <ul>
 *   <li>KCD 코드로 공식 질병명 조회: {@link #searchByCode(String)}</li>
 *   <li>질병명으로 KCD 코드 검색: {@link #searchByName(String)}</li>
 * </ul>
 */
@Component
public class HiraClient {

    private static final Logger log = LoggerFactory.getLogger(HiraClient.class);

    private static final String API_BASE_URL = "http://apis.data.go.kr";
    private static final String DISEASE_SEARCH_PATH =
            "/B551182/diseaseInfoService1/getDissNameCodeList1";

    private final RestClient restClient;
    private final String apiKey;

    public HiraClient(
            RestClient.Builder restClientBuilder,
            @Value("${external.hira.api-key:}") String apiKey
    ) {
        this.restClient = restClientBuilder.baseUrl(API_BASE_URL).build();
        this.apiKey = apiKey;
    }

    /**
     * KCD 코드로 질병 정보를 조회합니다.
     *
     * <p>예) "I10" → [HiraDiseaseItem(sickCd="I10", sickNm="본태성[원발성] 고혈압", ...)]
     * 코드가 없거나 API 키가 없으면 빈 리스트를 반환합니다.</p>
     *
     * @param kcdCode KCD 코드 (예: "I10", "E11")
     * @return 매칭된 질병 item 목록 (보통 1건)
     */
    public List<HiraDiseaseItem> searchByCode(String kcdCode) {
        return search(kcdCode, "SICK_CD");
    }

    /**
     * 질병명으로 KCD 코드를 검색합니다.
     *
     * <p>예) "고혈압" → [HiraDiseaseItem(sickCd="I10", sickNm="본태성[원발성] 고혈압", ...)]</p>
     *
     * @param diseaseName 질병명 키워드
     * @return 매칭된 질병 item 목록 (최대 10건)
     */
    public List<HiraDiseaseItem> searchByName(String diseaseName) {
        return search(diseaseName, "SICK_NM");
    }

    // ── 내부 구현 ─────────────────────────────────────────────────────────────

    private List<HiraDiseaseItem> search(String searchText, String diseaseType) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("HIRA_API_KEY가 설정되어 있지 않아 질병 검색을 건너뜁니다.");
            return List.of();
        }
        if (searchText == null || searchText.isBlank()) {
            return List.of();
        }

        try {
            String xml = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(DISEASE_SEARCH_PATH)
                            .queryParam("serviceKey", apiKey)
                            .queryParam("numOfRows", 10)
                            .queryParam("pageNo", 1)
                            .queryParam("sickType", "1")   // 3단 분류
                            .queryParam("medTp", "1")      // 양방
                            .queryParam("diseaseType", diseaseType)
                            .queryParam("searchText", searchText)
                            .build())
                    .retrieve()
                    .body(String.class);

            return parseXml(xml);
        }
        catch (RestClientException exception) {
            log.error("HIRA 질병 API 호출 실패. searchText={}, diseaseType={}", searchText, diseaseType, exception);
            throw new CustomException(ErrorCode.EXTERNAL_API_ERROR,
                    "HIRA 질병 API 호출에 실패했습니다.");
        }
    }

    /**
     * HIRA XML 응답을 파싱해 {@link HiraDiseaseItem} 목록으로 변환합니다.
     *
     * <pre>{@code
     * <response>
     *   <body>
     *     <items>
     *       <item>
     *         <sickCd>I10</sickCd>
     *         <sickNm>본태성[원발성] 고혈압</sickNm>
     *         <sickEngNm>Essential (primary) hypertension</sickEngNm>
     *       </item>
     *     </items>
     *   </body>
     * </response>
     * }</pre>
     */
    private static List<HiraDiseaseItem> parseXml(String xml) {
        if (xml == null || xml.isBlank()) {
            return List.of();
        }
        try {
            Document doc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(new InputSource(new StringReader(xml)));

            NodeList items = doc.getElementsByTagName("item");
            List<HiraDiseaseItem> result = new ArrayList<>();
            for (int i = 0; i < items.getLength(); i++) {
                Element item = (Element) items.item(i);
                result.add(new HiraDiseaseItem(
                        text(item, "sickCd"),
                        text(item, "sickNm"),
                        text(item, "sickEngNm")
                ));
            }
            return result;
        }
        catch (Exception exception) {
            log.error("HIRA XML 파싱 실패. xml 앞 200자: {}", xml.substring(0, Math.min(xml.length(), 200)), exception);
            return List.of();
        }
    }

    private static String text(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) {
            return null;
        }
        String value = nodes.item(0).getTextContent();
        return value == null || value.isBlank() ? null : value.strip();
    }
}
