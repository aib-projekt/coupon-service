package pl.aibprojekt.couponservice.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import pl.aibprojekt.couponservice.api.dto.ErrorResponse;
import pl.aibprojekt.couponservice.domain.CouponErrorCode;
import pl.aibprojekt.couponservice.domain.CouponException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(CouponException.class)
    public ResponseEntity<ErrorResponse> handleCouponException(CouponException ex) {
        // GEO_UNAVAILABLE is a dependency failure; 4xx codes are expected business outcomes
        if (ex.getErrorCode().getHttpStatus().is5xxServerError()) {
            log.error("Coupon service error: {} - {}", ex.getErrorCode(), ex.getMessage());
        } else {
            log.warn("Coupon business error: {} - {}", ex.getErrorCode(), ex.getMessage());
        }
        log.debug("Full stacktrace:", ex);
        return ResponseEntity
            .status(ex.getErrorCode().getHttpStatus())
            .body(new ErrorResponse(ex.getErrorCode().name(), ex.getMessage()));
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(WebExchangeBindException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .findFirst()
            .orElse("Validation failed");
        log.error("Validation error: {}", message);
        return ResponseEntity
            .status(CouponErrorCode.INVALID_REQUEST.getHttpStatus())
            .body(new ErrorResponse(CouponErrorCode.INVALID_REQUEST.name(), message));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation");
        log.debug("Full stacktrace:", ex);
        String message = ex.getMessage();
        if (message != null && message.contains("uq_coupon_usages_per_user")) {
            return ResponseEntity
                .status(CouponErrorCode.ALREADY_USED.getHttpStatus())
                .body(new ErrorResponse(CouponErrorCode.ALREADY_USED.name(),
                    "This coupon has already been used by this user."));
        }
        return ResponseEntity
            .status(CouponErrorCode.COUPON_ALREADY_EXISTS.getHttpStatus())
            .body(new ErrorResponse(CouponErrorCode.COUPON_ALREADY_EXISTS.name(),
                "A coupon with this code already exists."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage());
        log.debug("Full stacktrace:", ex);
        return ResponseEntity
            .internalServerError()
            .body(new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred."));
    }
}
