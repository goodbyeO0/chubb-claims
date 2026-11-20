package com.chubb.claims.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import jakarta.persistence.OptimisticLockException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
class ApiExceptions {
    @ExceptionHandler(ApiException.class) ResponseEntity<ApiError> business(ApiException ex) {
        return ResponseEntity.status(ex.status).body(ApiError.of(ex.code));
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiError> invalid(Exception ex) { return ResponseEntity.badRequest().body(ApiError.of("INVALID_INPUT")); }
    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, OptimisticLockException.class})
    ResponseEntity<ApiError> conflict(Exception ex) { return ResponseEntity.status(409).body(ApiError.of("VERSION_CONFLICT")); }
    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> missing(Exception ex) { return ResponseEntity.status(404).body(ApiError.of("NOT_FOUND")); }
}
