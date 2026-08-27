package com.ticketnest.common;

import com.ticketnest.common.dto.ErrorResponse;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global exception handler for REST API.
 * Returns consistent ErrorResponse shape for all error types.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    /** Handles @Valid validation failures on @RequestBody DTOs. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, jakarta.servlet.http.HttpServletRequest request) {
        var bindingFailure = ex.getBindingResult().getFieldErrors()
                .stream()
                .filter(fe -> fe.isBindingFailure())
                .findFirst();
        if (bindingFailure.isPresent()) {
            ErrorResponse body = ErrorResponse.of(
                    HttpStatus.BAD_REQUEST.value(),
                    "Invalid Parameter",
                    "Parameter '" + bindingFailure.get().getField() + "' has an invalid value",
                    request.getRequestURI()
            );
            return ResponseEntity.badRequest().body(body);
        }

        Map<String, List<String>> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.groupingBy(
                        fe -> fe.getField(),
                        LinkedHashMap::new,
                        Collectors.mapping(fe -> fe.getDefaultMessage(), Collectors.toList())
                ));
        ErrorResponse body = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                "Validation Failed",
                "Invalid request payload",
                request.getRequestURI(),
                errors
        );
        return ResponseEntity.badRequest().body(body);
    }

    /** Handles @Valid on path variables / request params. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex, jakarta.servlet.http.HttpServletRequest request) {
        Map<String, List<String>> errors = ex.getConstraintViolations()
                .stream()
                .collect(Collectors.groupingBy(
                        cv -> cv.getPropertyPath().toString(),
                        LinkedHashMap::new,
                        Collectors.mapping(cv -> cv.getMessage(), Collectors.toList())
                ));
        ErrorResponse body = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                "Validation Failed",
                "Invalid request parameters",
                request.getRequestURI(),
                errors
        );
        return ResponseEntity.badRequest().body(body);
    }

    /** Handles malformed JSON in request body. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedJson(HttpMessageNotReadableException ex, jakarta.servlet.http.HttpServletRequest request) {
        ErrorResponse body = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                "Malformed JSON",
                "Request body could not be parsed",
                request.getRequestURI()
        );
        return ResponseEntity.badRequest().body(body);
    }

    /** Handles malformed path variables and request parameters. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, jakarta.servlet.http.HttpServletRequest request) {
        String param = ex.getName();
        ErrorResponse body = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                "Invalid Parameter",
                "Parameter '" + param + "' has an invalid value",
                request.getRequestURI()
        );
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex, jakarta.servlet.http.HttpServletRequest request) {
        ErrorResponse body = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                "Invalid Parameter",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.badRequest().body(body);
    }

    /** Handles entity not found (our custom throw in services). */
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(EntityNotFoundException ex, jakarta.servlet.http.HttpServletRequest request) {
        ErrorResponse body = ErrorResponse.of(
                HttpStatus.NOT_FOUND.value(),
                "Not Found",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    /** Handles missing static resources and disabled documentation endpoints. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex, jakarta.servlet.http.HttpServletRequest request) {
        ErrorResponse body = ErrorResponse.of(
                HttpStatus.NOT_FOUND.value(),
                "Not Found",
                "Resource not found",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    /** Handles RuntimeException thrown for "not found" in services. */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntime(RuntimeException ex, jakarta.servlet.http.HttpServletRequest request) {
        String msg = ex.getMessage();
        boolean isNotFound = msg != null && msg.toLowerCase().contains("not found");
        HttpStatus status = isNotFound ? HttpStatus.NOT_FOUND : HttpStatus.INTERNAL_SERVER_ERROR;
        String error = isNotFound ? "Not Found" : "Internal Server Error";

        ErrorResponse body = ErrorResponse.of(
                status.value(),
                error,
                msg != null ? msg : "Unexpected error",
                request.getRequestURI()
        );
        return ResponseEntity.status(status).body(body);
    }

    /** Catch-all for any unhandled exception. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleAll(Exception ex, jakarta.servlet.http.HttpServletRequest request) {
        ErrorResponse body = ErrorResponse.of(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal Server Error",
                "An unexpected error occurred",
                request.getRequestURI()
        );
        return ResponseEntity.internalServerError().body(body);
    }
}
