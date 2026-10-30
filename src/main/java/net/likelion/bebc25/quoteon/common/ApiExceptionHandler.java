package net.likelion.bebc25.quoteon.common;

import net.likelion.bebc25.quoteon.admin.InvalidMemberQueryException;
import net.likelion.bebc25.quoteon.auth.InvalidCredentialsException;
import net.likelion.bebc25.quoteon.auth.MemberAccessDeniedException;
import net.likelion.bebc25.quoteon.customer.CustomerAccessDeniedException;
import net.likelion.bebc25.quoteon.customer.CustomerAlreadyInactiveException;
import net.likelion.bebc25.quoteon.customer.CustomerNotActiveException;
import net.likelion.bebc25.quoteon.customer.CustomerNotFoundException;
import net.likelion.bebc25.quoteon.customer.InvalidCustomerQueryException;
import net.likelion.bebc25.quoteon.member.EmailAlreadyExistsException;
import net.likelion.bebc25.quoteon.member.MemberNotFoundException;
import net.likelion.bebc25.quoteon.member.MemberNotPendingException;
import net.likelion.bebc25.quoteon.product.ProductNotActiveException;
import net.likelion.bebc25.quoteon.product.ProductNotFoundException;
import net.likelion.bebc25.quoteon.quote.InvalidQuoteQueryException;
import net.likelion.bebc25.quoteon.quote.QuoteAccessDeniedException;
import net.likelion.bebc25.quoteon.quote.QuoteNotFoundException;
import net.likelion.bebc25.quoteon.quote.DuplicateProductException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("요청 값을 확인해 주세요.");
        return ResponseEntity.badRequest().body(ApiErrorResponse.of(400, "VALIDATION_ERROR", message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadable(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.of(400, "VALIDATION_ERROR", "요청 본문을 확인해 주세요."));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidCredentials(InvalidCredentialsException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiErrorResponse.of(
                        HttpStatus.UNAUTHORIZED.value(),
                        "INVALID_CREDENTIALS",
                        "이메일 또는 비밀번호가 올바르지 않습니다."
                ));
    }

    @ExceptionHandler(MemberAccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleMemberAccess(MemberAccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(exception.response());
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateEmail(EmailAlreadyExistsException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of(409, "EMAIL_ALREADY_EXISTS", "이미 가입된 이메일입니다."));
    }

    @ExceptionHandler(MemberNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleMemberNotFound(MemberNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(
                        HttpStatus.NOT_FOUND.value(),
                        "MEMBER_NOT_FOUND",
                        "회원을 찾을 수 없습니다."
                ));
    }

    @ExceptionHandler(MemberNotPendingException.class)
    public ResponseEntity<ApiErrorResponse> handleMemberNotPending(MemberNotPendingException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of(
                        HttpStatus.CONFLICT.value(),
                        "MEMBER_NOT_PENDING",
                        "승인 대기 상태의 회원만 처리할 수 있습니다."
                ));
    }

    @ExceptionHandler(InvalidMemberQueryException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidMemberQuery(InvalidMemberQueryException exception) {
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.of(400, "VALIDATION_ERROR", exception.getMessage()));
    }

    @ExceptionHandler(DuplicateProductException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateProduct(DuplicateProductException exception) {
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.of(400, "DUPLICATE_PRODUCT", "같은 제품을 항목에 중복해서 넣을 수 없습니다."));
    }

    @ExceptionHandler(InvalidCustomerQueryException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidCustomerQuery(InvalidCustomerQueryException exception) {
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.of(400, "VALIDATION_ERROR", exception.getMessage()));
    }

    @ExceptionHandler(CustomerNotActiveException.class)
    public ResponseEntity<ApiErrorResponse> handleCustomerNotActive(CustomerNotActiveException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of(
                        HttpStatus.CONFLICT.value(),
                        "CUSTOMER_NOT_ACTIVE",
                        "비활성 고객은 신규 견적에 선택할 수 없습니다."
                ));
    }

    @ExceptionHandler(CustomerAlreadyInactiveException.class)
    public ResponseEntity<ApiErrorResponse> handleCustomerAlreadyInactive(CustomerAlreadyInactiveException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of(
                        HttpStatus.CONFLICT.value(),
                        "CUSTOMER_ALREADY_INACTIVE",
                        "활성 고객만 비활성화할 수 있습니다."
                ));
    }

    @ExceptionHandler(CustomerNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleCustomerNotFound(CustomerNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(
                        HttpStatus.NOT_FOUND.value(),
                        "CUSTOMER_NOT_FOUND",
                        "고객을 찾을 수 없습니다."
                ));
    }

    @ExceptionHandler(CustomerAccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleCustomerAccessDenied(CustomerAccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiErrorResponse.of(
                        HttpStatus.FORBIDDEN.value(),
                        "CUSTOMER_ACCESS_DENIED",
                        "본인의 고객만 견적에 사용할 수 있습니다."
                ));
    }

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleProductNotFound(ProductNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(
                        HttpStatus.NOT_FOUND.value(),
                        "PRODUCT_NOT_FOUND",
                        "제품을 찾을 수 없습니다."
                ));
    }

    @ExceptionHandler(QuoteNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleQuoteNotFound(QuoteNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(
                        HttpStatus.NOT_FOUND.value(),
                        "QUOTE_NOT_FOUND",
                        "견적서를 찾을 수 없습니다."
                ));
    }

    @ExceptionHandler(QuoteAccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleQuoteAccessDenied(QuoteAccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiErrorResponse.of(
                        HttpStatus.FORBIDDEN.value(),
                        "QUOTE_ACCESS_DENIED",
                        "본인의 견적서만 조회하거나 수정할 수 있습니다."
                ));
    }

    @ExceptionHandler(InvalidQuoteQueryException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidQuoteQuery(InvalidQuoteQueryException exception) {
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.of(400, "VALIDATION_ERROR", exception.getMessage()));
    }

    @ExceptionHandler(ProductNotActiveException.class)
    public ResponseEntity<ApiErrorResponse> handleProductNotActive(ProductNotActiveException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of(
                        HttpStatus.CONFLICT.value(),
                        "PRODUCT_NOT_ACTIVE",
                        "판매 중인 제품만 견적에 포함할 수 있습니다."
                ));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiErrorResponse> handleDataAccess(DataAccessException exception) {
        log.error("database query failed", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorResponse.of(500, "DATA_ACCESS_ERROR", "데이터를 조회하지 못했습니다."));
    }
}
