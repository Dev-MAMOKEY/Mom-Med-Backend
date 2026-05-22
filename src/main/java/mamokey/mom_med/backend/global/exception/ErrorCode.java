package mamokey.mom_med.backend.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 공통
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "잘못된 입력입니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "리소스를 찾을 수 없습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),

    // 약 (Slice 01)
    DRUG_NOT_FOUND(HttpStatus.NOT_FOUND, "약을 찾을 수 없습니다."),
    DRUG_AMBIGUOUS(HttpStatus.MULTIPLE_CHOICES, "동명이품이 존재합니다. 품목기준코드로 다시 요청해주세요."),
    DUPLICATE_MEDICATION(HttpStatus.CONFLICT, "이미 복용 중인 약입니다."),

    // 부모 프로필 (Slice 04)
    PARENT_NOT_FOUND(HttpStatus.NOT_FOUND, "부모 프로필을 찾을 수 없습니다."),
    CONSENT_REQUIRED(HttpStatus.FORBIDDEN, "데이터 공유 동의가 필요합니다."),

    // 알레르기 (Slice 04)
    ALLERGY_NOT_FOUND(HttpStatus.NOT_FOUND, "알레르기 정보를 찾을 수 없습니다."),

    // 기저질환 (Slice 05)
    DISEASE_NOT_FOUND(HttpStatus.NOT_FOUND, "질병 코드를 찾을 수 없습니다."),
    CONDITION_NOT_FOUND(HttpStatus.NOT_FOUND, "기저질환 정보를 찾을 수 없습니다."),

    // 응급카드 (Slice 08)
    EMERGENCY_CARD_NOT_FOUND(HttpStatus.NOT_FOUND, "응급카드를 찾을 수 없습니다."),
    EMERGENCY_CARD_EXPIRED(HttpStatus.GONE, "만료된 응급카드입니다."),
    EMERGENCY_CARD_REVOKED(HttpStatus.GONE, "취소된 응급카드입니다."),

    // 외부 API
    EXTERNAL_API_ERROR(HttpStatus.BAD_GATEWAY, "외부 API 호출에 실패했습니다.");

    private final HttpStatus status;
    private final String message;
}
