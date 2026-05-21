package mamokey.mom_med.backend.global.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DrugNameNormalizerTest {

	@Test
	void removesBesylateSalt() {
		assertThat(DrugNameNormalizer.normalize("Amlodipine Besylate")).isEqualTo("amlodipine");
	}

	@Test
	void removesParentheticalAsDescriptionBeforeSalt() {
		assertThat(DrugNameNormalizer.normalize("amlodipine besylate (as amlodipine)"))
				.isEqualTo("amlodipine");
	}

	@Test
	void keepsPlainEnglishBaseName() {
		assertThat(DrugNameNormalizer.normalize("Acetaminophen")).isEqualTo("acetaminophen");
	}

	@Test
	void removesSodiumSalt() {
		assertThat(DrugNameNormalizer.normalize("Warfarin Sodium")).isEqualTo("warfarin");
	}

	@Test
	void removesCamsylateSaltWithParentheticalDescription() {
		assertThat(DrugNameNormalizer.normalize("amlodipine camsylate (as amlodipine)"))
				.isEqualTo("amlodipine");
	}

	@Test
	void passesKoreanNameThrough() {
		assertThat(DrugNameNormalizer.normalize("암로디핀베실산염")).isEqualTo("암로디핀베실산염");
	}
}
