package mamokey.mom_med.backend.global.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * DrugNameNormalizer의 회귀 테스트입니다.
 *
 * <p>이 테스트들은 Slice 01/02가 약물명 매칭을 시작하기 전에 정규화 규칙이 흔들리지 않게 막아줍니다.
 * SALT_SUFFIXES를 바꿀 때는 실제 매칭 결과가 어떻게 달라지는지 이 테스트부터 확인해야 합니다.</p>
 */
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
		// 한글 단독 표기는 영문 정규화 대상이 아니므로 원문을 그대로 보존합니다.
		assertThat(DrugNameNormalizer.normalize("암로디핀베실산염")).isEqualTo("암로디핀베실산염");
	}
}
