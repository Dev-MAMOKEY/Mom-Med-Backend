package mamokey.mom_med.backend.external.kma;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 기상청 단기예보 API(getVilageFcst) 응답 item 한 건입니다.
 *
 * <p>category 주요 코드:
 * TMP=1시간기온, TMN=일최저기온, TMX=일최고기온,
 * SKY=하늘상태, PTY=강수형태, POP=강수확률, WSD=풍속</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KmaFcstItem(
        String baseDate,
        String baseTime,
        String category,
        String fcstDate,
        String fcstTime,
        String fcstValue,
        int nx,
        int ny
) {}
