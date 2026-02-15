package com.gosafe.config;

import com.gosafe.controller.AuthController.UnauthorizedException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Bean validation errors (@Valid) → 422 with first error message */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(e -> e.getDefaultMessage())
            .orElse("Validation failed.");
        return ResponseEntity.status(422).body(Map.of("error", msg));
    }

    /** Missing / invalid JWT → 401 */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<?> handleUnauth(UnauthorizedException ex) {
        return ResponseEntity.status(401).body(Map.of("error", ex.getMessage()));
    }

    /** Catch-all → 500 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGeneral(Exception ex) {
        return ResponseEntity.status(500).body(Map.of("error",
            ex.getMessage() != null ? ex.getMessage() : "Internal server error"));
    }
}
