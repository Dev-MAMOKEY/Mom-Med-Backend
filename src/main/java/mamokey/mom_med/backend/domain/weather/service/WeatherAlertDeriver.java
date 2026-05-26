package mamokey.mom_med.backend.domain.weather.service;

import mamokey.mom_med.backend.domain.weather.model.WeatherObservation;
import mamokey.mom_med.backend.external.kma.KmaFcstItem;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 기상청 단기예보 데이터에서 폭염·한파 특보를 도출합니다 (Slice 07 v2).
 *
 * <ul>
 *   <li>폭염: TMX(오늘 일 최고기온) ≥ 35 → 폭염경보, ≥ 33 → 폭염주의보</li>
 *   <li>한파: TMN(내일 아침 최저기온) ≤ -15 → 한파경보, ≤ -12 → 한파주의보</li>
 * </ul>
 */
@Component
public class WeatherAlertDeriver {

    /**
     * @param items        KMA API 응답 아이템 전체 (날짜 필터 없이)
     * @param todayStr     오늘 날짜 문자열 (yyyyMMdd) — TMX 판정 기준
     * @param tomorrowStr  내일 날짜 문자열 (yyyyMMdd) — TMN 판정 기준
     */
    public WeatherObservation derive(List<KmaFcstItem> items, String todayStr, String tomorrowStr) {
        if (items == null || items.isEmpty()) return WeatherObservation.empty();

        Double tmx = items.stream()
                .filter(i -> "TMX".equals(i.category()) && todayStr.equals(i.fcstDate()))
                .mapToDouble(i -> parseDouble(i.fcstValue()))
                .boxed()
                .findFirst()
                .orElse(null);

        Double tmn = items.stream()
                .filter(i -> "TMN".equals(i.category()) && tomorrowStr.equals(i.fcstDate()))
                .mapToDouble(i -> parseDouble(i.fcstValue()))
                .boxed()
                .findFirst()
                .orElse(null);

        List<String> alerts = new ArrayList<>();
        if (tmx != null) {
            if (tmx >= 35.0) alerts.add("폭염경보");
            else if (tmx >= 33.0) alerts.add("폭염주의보");
        }
        if (tmn != null) {
            if (tmn <= -15.0) alerts.add("한파경보");
            else if (tmn <= -12.0) alerts.add("한파주의보");
        }

        return new WeatherObservation(tmx, tmn, alerts);
    }

    private double parseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
