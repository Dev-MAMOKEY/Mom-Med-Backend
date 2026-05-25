package mamokey.mom_med.backend.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Mom-Med API")
                        .description("부모 복약 안전 관리 서비스 API (Slice 01~08)")
                        .version("v1"))
                .tags(List.of(
                        new Tag().name("Parent").description("부모 프로파일 관리 (Slice 01)"),
                        new Tag().name("Drug").description("약 검색 · 식별 (Slice 01)"),
                        new Tag().name("Medication").description("부모 약장 관리 · 안전검사 (Slice 02~04)"),
                        new Tag().name("Safety").description("약물 안전성 단독 검사 (Slice 02)"),
                        new Tag().name("NB").description("NB 문서 추출 · 조회 (Slice 03)"),
                        new Tag().name("Allergy").description("부모 알레르기 관리 (Slice 05)"),
                        new Tag().name("Condition").description("부모 기저질환 관리 (Slice 05)"),
                        new Tag().name("DeviceToken").description("자녀 디바이스 토큰 관리 (Slice 07)"),
                        new Tag().name("Hospital").description("부모 단골 병원 관리 (Slice 08)"),
                        new Tag().name("Pharmacy").description("부모 단골 약국 관리 (Slice 08)"),
                        new Tag().name("EmergencyCard").description("응급카드 QR 관리 (Slice 08)"),
                        new Tag().name("Hospital").description("HIRA 병원·약국 검색 (Slice 08)")
                ));
    }
}
