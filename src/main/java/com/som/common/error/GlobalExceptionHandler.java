package com.som.common.error;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.som.common.web.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 모든 예외를 공통 에러 형식으로 바꾼다.
 * 거부된 입력값·예외 메시지 원문·스택은 응답에 넣지 않는다. 검증 오류 메시지는 "필드: 이유"만 담는다.
 * 요청 바디·거부값은 로그에도 남기지 않는다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String INVALID_FORMAT_REASON = "형식이 올바르지 않습니다";
    private static final String MISSING_REASON = "값이 필요합니다";

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorResponse> handleApi(ApiException ex, HttpServletRequest request) {
        return respond(ex.code(), ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleBodyValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Stream<String> messages = ex.getBindingResult().getAllErrors().stream().map(this::describe);
        return validation(messages, request);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ErrorResponse> handleMethodValidation(HandlerMethodValidationException ex, HttpServletRequest request) {
        Stream<String> messages = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> error instanceof FieldError fieldError
                                ? describe(fieldError)
                                : join(result.getMethodParameter().getParameterName(), error)));
        return validation(messages, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        Stream<String> messages = ex.getConstraintViolations().stream()
                .map(violation -> lastNodeName(violation) + ": " + violation.getMessage());
        return validation(messages, request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return validation(Stream.of(ex.getName() + ": " + INVALID_FORMAT_REASON), request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex,
                                                         HttpServletRequest request) {
        return validation(Stream.of(ex.getParameterName() + ": " + MISSING_REASON), request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        if (ex.getCause() instanceof InvalidFormatException invalid) {
            return validation(Stream.of(fieldPath(invalid) + ": " + INVALID_FORMAT_REASON), request);
        }
        return respond(ErrorCode.MALFORMED_REQUEST, ErrorCode.MALFORMED_REQUEST.defaultMessage(), request);
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    ResponseEntity<ErrorResponse> handleNotFound(Exception ex, HttpServletRequest request) {
        return respond(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.defaultMessage(), request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex,
                                                         HttpServletRequest request) {
        return respond(ErrorCode.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED.defaultMessage(), request);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException ex, HttpServletRequest request) {
        return validation(Stream.of(ex.getHeaderName() + ": " + MISSING_REASON), request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        // 위에서 따로 다루지 않은 Spring MVC 표준 예외(415 등)는 원래 상태 코드를 따른다.
        // 클라이언트 잘못(4xx)이 500 INTERNAL_ERROR로 바뀌지 않게 한다. 메시지는 고정 문구만 쓴다.
        ErrorCode standard = ex instanceof org.springframework.web.ErrorResponse spring
                ? fromSpringStatus(spring.getStatusCode().value())
                : null;
        if (standard != null) {
            return respond(standard, standard.defaultMessage(), request);
        }
        // 스택은 서버 로그에만 남긴다(요청 ID는 MDC로 함께 기록). 요청 바디는 남기지 않는다.
        log.error("Unhandled exception: {} {}", request.getMethod(), request.getRequestURI(), ex);
        return respond(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage(), request);
    }

    private ErrorCode fromSpringStatus(int status) {
        return switch (status) {
            case 400 -> ErrorCode.MALFORMED_REQUEST;
            case 404 -> ErrorCode.NOT_FOUND;
            case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
            case 415 -> ErrorCode.UNSUPPORTED_MEDIA_TYPE;
            default -> null; // 그 밖의 상태(5xx 등)는 서버 오류로 처리한다
        };
    }

    private ResponseEntity<ErrorResponse> validation(Stream<String> messages, HttpServletRequest request) {
        String message = messages.sorted().distinct().collect(Collectors.joining(", "));
        return respond(ErrorCode.VALIDATION_ERROR,
                message.isEmpty() ? ErrorCode.VALIDATION_ERROR.defaultMessage() : message, request);
    }

    private ResponseEntity<ErrorResponse> respond(ErrorCode code, String message, HttpServletRequest request) {
        return ResponseEntity.status(code.status())
                .body(ErrorResponse.of(code, message, RequestIds.current(request)));
    }

    /** 바인딩 실패(타입 변환)의 기본 메시지에는 거부값이 들어가므로 고정 문구로 바꾼다. */
    private String describe(ObjectError error) {
        if (error instanceof FieldError fieldError) {
            String reason = fieldError.isBindingFailure() ? INVALID_FORMAT_REASON : fieldError.getDefaultMessage();
            return fieldError.getField() + ": " + reason;
        }
        return join(error.getObjectName(), error);
    }

    private String join(String name, MessageSourceResolvable error) {
        return name + ": " + error.getDefaultMessage();
    }

    private String lastNodeName(ConstraintViolation<?> violation) {
        String name = null;
        for (Path.Node node : violation.getPropertyPath()) {
            name = node.getName();
        }
        return name;
    }

    private String fieldPath(JsonMappingException ex) {
        List<String> names = ex.getPath().stream()
                .map(JsonMappingException.Reference::getFieldName)
                .filter(Objects::nonNull)
                .toList();
        return names.isEmpty() ? "body" : String.join(".", names);
    }
}
