package mamokey.mom_med.backend.external.hira;

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

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * HIRA 약국정보서비스(sno 12100) 클라이언트 (Slice 08).
 *
 * <p>endpoint: /B551182/pharmacyInfoService/getParmacyBasisList
 * 약국명 또는 지역코드로 검색합니다.</p>
 */
@Component
public class HiraPharmacyClient {

    private static final Logger log = LoggerFactory.getLogger(HiraPharmacyClient.class);

    private static final String API_BASE_URL = "http://apis.data.go.kr";
    private static final String PATH = "/B551182/pharmacyInfoService/getParmacyBasisList";

    private final RestClient restClient;
    private final String apiKey;

    public HiraPharmacyClient(
            RestClient.Builder restClientBuilder,
            @Value("${external.hira.api-key:}") String apiKey
    ) {
        this.restClient = restClientBuilder.baseUrl(API_BASE_URL).build();
        this.apiKey = apiKey;
    }

    /**
     * 약국명으로 검색합니다.
     *
     * @param yadmNm  약국명 키워드
     * @param maxRows 최대 결과 수
     */
    public List<HiraPharmacyItem> searchByName(String yadmNm, int maxRows) {
        if (apiKey == null || apiKey.isBlank() || yadmNm == null || yadmNm.isBlank()) {
            return List.of();
        }
        try {
            byte[] bytes = restClient.get()
                    .uri(b -> b.path(PATH)
                            .queryParam("serviceKey", apiKey)
                            .queryParam("pageNo", 1)
                            .queryParam("numOfRows", maxRows)
                            .queryParam("yadmNm", yadmNm)
                            .build())
                    .retrieve()
                    .body(byte[].class);
            return parseXml(bytes != null ? new String(bytes, StandardCharsets.UTF_8) : null);
        } catch (RestClientException e) {
            log.error("HIRA 약국 검색 API 호출 실패. yadmNm={}", yadmNm, e);
            throw new CustomException(ErrorCode.EXTERNAL_API_ERROR, "HIRA 약국 검색 API 호출에 실패했습니다.");
        }
    }

    // ── 내부 XML 파싱 ──────────────────────────────────────────────────────

    private static List<HiraPharmacyItem> parseXml(String xml) {
        if (xml == null || xml.isBlank()) return List.of();
        try {
            Document doc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(new InputSource(new StringReader(xml)));

            NodeList items = doc.getElementsByTagName("item");
            List<HiraPharmacyItem> result = new ArrayList<>();
            for (int i = 0; i < items.getLength(); i++) {
                Element item = (Element) items.item(i);
                result.add(new HiraPharmacyItem(
                        text(item, "ykiho"),
                        text(item, "yadmNm"),
                        text(item, "addr"),
                        text(item, "telno"),
                        decimal(item, "XPos"),
                        decimal(item, "YPos")
                ));
            }
            return result;
        } catch (Exception e) {
            log.error("HIRA 약국 XML 파싱 실패. xml 앞 200자: {}",
                    xml.substring(0, Math.min(xml.length(), 200)), e);
            return List.of();
        }
    }

    private static String text(Element parent, String tag) {
        NodeList nodes = parent.getElementsByTagName(tag);
        if (nodes.getLength() == 0) return null;
        String v = nodes.item(0).getTextContent();
        return (v == null || v.isBlank()) ? null : v.strip();
    }

    private static BigDecimal decimal(Element parent, String tag) {
        String v = text(parent, tag);
        if (v == null) return null;
        try { return new BigDecimal(v); } catch (NumberFormatException e) { return null; }
    }
}
