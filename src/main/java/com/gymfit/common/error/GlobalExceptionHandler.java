package com.gymfit.common.error;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(
            ApiException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity
                .status(exception.getStatus())
                .body(new ApiError(
                        Instant.now(),
                        exception.getStatus().value(),
                        exception.getCode(),
                        exception.getMessage(),
                        request.getRequestURI(),
                        Map.of()
                ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        Map<String, String> errors =
                new LinkedHashMap<>();

        exception.getBindingResult()
                .getFieldErrors()
                .forEach(error ->
                        errors.put(
                                error.getField(),
                                error.getDefaultMessage()
                        )
                );

        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ApiError(
                        Instant.now(),
                        HttpStatus.UNPROCESSABLE_ENTITY.value(),
                        "validation_error",
                        "Dữ liệu không hợp lệ",
                        request.getRequestURI(),
                        errors
                ));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> handleBadCredentials(
            BadCredentialsException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(new ApiError(
                        Instant.now(),
                        HttpStatus.UNAUTHORIZED.value(),
                        "invalid_credentials",
                        "Email hoặc mật khẩu không đúng",
                        request.getRequestURI(),
                        Map.of()
                ));
    }

    @ExceptionHandler(LockedException.class)
    public ResponseEntity<ApiError> handleLocked(
            LockedException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(new ApiError(
                        Instant.now(),
                        HttpStatus.FORBIDDEN.value(),
                        "account_locked",
                        "Tài khoản đã bị khóa",
                        request.getRequestURI(),
                        Map.of()
                ));
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ApiError> handleDisabled(
            DisabledException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(new ApiError(
                        Instant.now(),
                        HttpStatus.FORBIDDEN.value(),
                        "account_disabled",
                        "Tài khoản đã bị vô hiệu hóa",
                        request.getRequestURI(),
                        Map.of()
                ));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(
            AccessDeniedException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(new ApiError(
                        Instant.now(),
                        HttpStatus.FORBIDDEN.value(),
                        "access_denied",
                        "Bạn không có quyền thực hiện chức năng này",
                        request.getRequestURI(),
                        Map.of()
                ));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(
            DataIntegrityViolationException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ApiError(
                        Instant.now(),
                        HttpStatus.CONFLICT.value(),
                        "data_conflict",
                        "Dữ liệu bị xung đột",
                        request.getRequestURI(),
                        Map.of()
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnknown(
            Exception exception,
            HttpServletRequest request
    ) {
        exception.printStackTrace();

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError(
                        Instant.now(),
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "internal_error",
                        "Có lỗi xảy ra trên hệ thống",
                        request.getRequestURI(),
                        Map.of()
                ));
    }
}