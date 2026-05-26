package mamokey.mom_med.backend.domain.weather.model;

import java.util.List;

/**
 * 격자(nx, ny)의 기상 관측 결과 및 도출된 특보 목록.
 *
 * <p>tmx는 오늘 일 최고기온(폭염 판정), tmn은 내일 아침 최저기온(한파 판정).</p>
 */
public record WeatherObservation(Double tmx, Double tmn, List<String> derivedAlerts) {

    public boolean hasAlerts() {
        return derivedAlerts != null && !derivedAlerts.isEmpty();
    }

    public static WeatherObservation empty() {
        return new WeatherObservation(null, null, List.of());
    }
}
