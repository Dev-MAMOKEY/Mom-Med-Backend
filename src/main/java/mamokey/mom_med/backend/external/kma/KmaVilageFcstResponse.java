package mamokey.mom_med.backend.external.kma;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 기상청 단기예보 API(getVilageFcst) 전체 응답 래퍼입니다.
 *
 * <p>JSON 구조: response → body → items → item[]</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KmaVilageFcstResponse(Response response) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(Header header, Body body) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Header(String resultCode, String resultMsg) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body(Items items) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Items(List<KmaFcstItem> item) {}

    public String resultCode() {
        return response != null && response.header() != null ? response.header().resultCode() : null;
    }

    public List<KmaFcstItem> items() {
        try {
            List<KmaFcstItem> list = response.body().items().item();
            return list != null ? list : List.of();
        } catch (NullPointerException e) {
            return List.of();
        }
    }
}
