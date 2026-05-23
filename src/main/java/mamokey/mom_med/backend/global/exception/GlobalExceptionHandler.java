package mamokey.mom_med.backend.global.exception;

import mamokey.mom_med.backend.global.rsdata.BlockErrorResponse;
import mamokey.mom_med.backend.global.rsdata.RsData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<RsData<Void>> handleCustomException(CustomException e) {
        ErrorCode errorCode = e.getErrorCode();
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(RsData.fail(errorCode.getStatus().value(), e.getMessage()));
    }

    // 약물 안전 검사 BLOCK — 프론트 합의 구조 { error: "block", verdict: {...} }
    @ExceptionHandler(SafetyBlockException.class)
    public ResponseEntity<BlockErrorResponse> handleSafetyBlock(SafetyBlockException e) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(BlockErrorResponse.of(e.getVerdict()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<RsData<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("입력값이 올바르지 않습니다.");
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(RsData.fail(400, message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<RsData<Void>> handleException(Exception e) {
        log.error("Unhandled application exception", e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(RsData.fail(500, "서버 내부 오류가 발생했습니다."));
    }
}
