package mamokey.mom_med.backend.infra.etl.weather;

import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 로컬/운영에서 명시적으로 켰을 때만 Slice 06 seed를 자동 적재하는 runner입니다.
 *
 * <p>기본값은 false입니다. 서버를 켤 때마다 대용량 xlsx를 읽지 않도록 하고,
 * 필요할 때만 WEATHER_RULES_LOAD_ENABLED 또는 REGION_GRID_LOAD_ENABLED를 true로 둡니다.</p>
 */
@Component
public class WeatherSeedLoadRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(WeatherSeedLoadRunner.class);

	private final WeatherRuleLoader weatherRuleLoader;
	private final RegionGridLoader regionGridLoader;
	private final boolean weatherRulesEnabled;
	private final String weatherRulesPath;
	private final String weatherRulesVersion;
	private final boolean regionGridEnabled;
	private final String regionGridPath;

	public WeatherSeedLoadRunner(
			WeatherRuleLoader weatherRuleLoader,
			RegionGridLoader regionGridLoader,
			@Value("${app.etl.weather.rules.enabled:false}") boolean weatherRulesEnabled,
			@Value("${app.etl.weather.rules.path:seed/weather_rules_v0.2.json}") String weatherRulesPath,
			@Value("${app.etl.weather.rules.version:v0.2}") String weatherRulesVersion,
			@Value("${app.etl.weather.region-grid.enabled:false}") boolean regionGridEnabled,
			@Value("${app.etl.weather.region-grid.path:}") String regionGridPath
	) {
		this.weatherRuleLoader = weatherRuleLoader;
		this.regionGridLoader = regionGridLoader;
		this.weatherRulesEnabled = weatherRulesEnabled;
		this.weatherRulesPath = weatherRulesPath;
		this.weatherRulesVersion = weatherRulesVersion;
		this.regionGridEnabled = regionGridEnabled;
		this.regionGridPath = regionGridPath;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (weatherRulesEnabled) {
			WeatherRuleLoadResult result = weatherRuleLoader.load(Path.of(weatherRulesPath), weatherRulesVersion);
			log.info("weather_rules seed loaded: inserted={}, updated={}, total={}",
					result.inserted(), result.updated(), result.total());
		}
		if (regionGridEnabled) {
			RegionGridLoadResult result = regionGridLoader.load(Path.of(regionGridPath));
			log.info("region_grid xlsx loaded: inserted={}, updated={}, skipped={}, total={}",
					result.inserted(), result.updated(), result.skipped(), result.total());
		}
	}
}
