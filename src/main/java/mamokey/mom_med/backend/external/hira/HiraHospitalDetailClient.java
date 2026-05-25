package mamokey.mom_med.backend.external.hira;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * HIRA 의료기관별상세정보서비스(sno 12101) 클라이언트 (Slice 08).
 *
 * <p>endpoint: /B551182/MadmDtlInfoService2.7/getDtlInfo2.7
 * ykiho를 입력으로 받아 응급실 전화번호·운영시간 상세 정보를 반환합니다.</p>
 */
@Component
public class HiraHospitalDetailClient {

    private static final Logger log = LoggerFactory.getLogger(HiraHospitalDetailClient.class);

    private static final String API_BASE_URL = "http://apis.data.go.kr";
    private static final String PATH = "/B551182/MadmDtlInfoService2.7/getDtlInfo2.7";

    // 요일 진료시간 태그 목록
    private static final String[] DAY_PREFIXES =
            {"trmtMon", "trmtTue", "trmtWed", "trmtThu", "trmtFri", "trmtSat"};
    private static final String[] DAY_KEYS =
            {"mon", "tue", "wed", "thu", "fri", "sat"};

    private final RestClient restClient;
    private final String apiKey;
    private final ObjectMapper objectMapper;

    public HiraHospitalDetailClient(
            RestClient.Builder restClientBuilder,
            @Value("${external.hira.api-key:}") String apiKey,
            ObjectMapper objectMapper
    ) {
        this.restClient = restClientBuilder.baseUrl(API_BASE_URL).build();
        this.apiKey = apiKey;
        this.objectMapper = objectMapper;
    }

    /**
     * ykiho로 응급실 상세 정보를 조회합니다.
     *
     * @param ykiho 요양기관기호
     * @return 상세 정보 (없으면 empty)
     */
    public Optional<HiraHospitalDetailItem> fetchDetail(String ykiho) {
        if (apiKey == null || apiKey.isBlank() || ykiho == null || ykiho.isBlank()) {
            return Optional.empty();
        }
        try {
            byte[] bytes = restClient.get()
                    .uri(b -> b.path(PATH)
                            .queryParam("serviceKey", apiKey)
                            .queryParam("ykiho", ykiho)
                            .build())
                    .retrieve()
                    .body(byte[].class);
            return parseXml(bytes != null ? new String(bytes, StandardCharsets.UTF_8) : null);
        } catch (RestClientException e) {
            log.error("HIRA 병원 상세 API 호출 실패. ykiho={}", ykiho, e);
            throw new CustomException(ErrorCode.EXTERNAL_API_ERROR, "HIRA 병원 상세 API 호출에 실패했습니다.");
        }
    }

    // ── 내부 XML 파싱 ──────────────────────────────────────────────────────

    private Optional<HiraHospitalDetailItem> parseXml(String xml) {
        if (xml == null || xml.isBlank()) return Optional.empty();
        try {
            Document doc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(new InputSource(new StringReader(xml)));

            NodeList items = doc.getElementsByTagName("item");
            if (items.getLength() == 0) return Optional.empty();

            Element item = (Element) items.item(0);

            // 요일별 진료시간 JSON 구성
            Map<String, Object> weeklyHours = new LinkedHashMap<>();
            for (int i = 0; i < DAY_PREFIXES.length; i++) {
                String start = text(item, DAY_PREFIXES[i] + "Start");
                String end   = text(item, DAY_PREFIXES[i] + "End");
                if (start != null || end != null) {
                    weeklyHours.put(DAY_KEYS[i], Map.of(
                            "start", start != null ? start : "",
                            "end",   end   != null ? end   : ""
                    ));
                }
            }
            String weeklyHoursJson = weeklyHours.isEmpty()
                    ? null
                    : objectMapper.writeValueAsString(weeklyHours);

            return Optional.of(new HiraHospitalDetailItem(
                    text(item, "emyNgtYn"),
                    text(item, "emyNgtTelNo1"),
                    text(item, "emyNgtTelNo2"),
                    text(item, "emyDayYn"),
                    text(item, "emyDayTelNo1"),
                    text(item, "emyDayTelNo2"),
                    weeklyHoursJson
            ));
        } catch (Exception e) {
            log.error("HIRA 병원 상세 XML 파싱 실패. xml 앞 200자: {}",
                    xml.substring(0, Math.min(xml.length(), 200)), e);
            return Optional.empty();
        }
    }

    private static String text(Element parent, String tag) {
        NodeList nodes = parent.getElementsByTagName(tag);
        if (nodes.getLength() == 0) return null;
        String v = nodes.item(0).getTextContent();
        return (v == null || v.isBlank()) ? null : v.strip();
    }
}
