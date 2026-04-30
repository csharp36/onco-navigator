package com.onconavigator.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Centralized exception handler for all REST controllers.
 *
 * <p>HIPAA requirement (T-03-01): API error responses must NEVER leak PHI. Spring's default
 * error handling exposes {@code exception.getMessage()} which can contain entity field values,
 * validation constraint details, or repository error messages — all of which may contain PHI.
 *
 * <p>This handler intercepts all exceptions and returns generic, non-PHI error messages.
 * The generic handler logs only the exception class name — never the message or stack trace
 * that could contain PHI from entity field validation or repository errors.
 *
 * <p>Specific handlers:
 * <ul>
 *   <li>{@link ResponseStatusException} — returns the caller's explicit reason string (safe — set by controllers, not from entities)</li>
 *   <li>{@link MethodArgumentNotValidException} — returns field-level validation errors (safe — messages come from {@code @NotBlank} annotations, not entity data)</li>
 *   <li>{@link Exception} — catches all other exceptions, logs class name only, returns generic "internal error" message</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handle explicitly thrown {@link ResponseStatusException} from controllers.
     *
     * <p>Controllers set the reason string — these are safe, non-PHI messages like
     * "Patient not found" or "Alert already resolved".
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of("error", ex.getReason() != null ? ex.getReason() : "Request failed"));
    }

    /**
     * Handle {@link MethodArgumentNotValidException} from {@code @Valid} annotated parameters.
     *
     * <p>Returns a map of field names to their validation messages. Messages come from
     * the {@code message} attribute of bean validation annotations (e.g., {@code @NotBlank}),
     * not from entity field values — safe to return to the client.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Invalid value",
                        (a, b) -> a));
        return ResponseEntity.badRequest().body(Map.of("errors", fieldErrors));
    }

    /**
     * Handle malformed request bodies (JSON parse errors, type mismatches).
     *
     * <p>Safe to log: Jackson deserialization errors reference field names and type expectations,
     * not PHI values. Returns a 400 with the parse error detail so the client can fix the request.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleMessageNotReadable(HttpMessageNotReadableException ex) {
        String detail = ex.getMostSpecificCause().getMessage();
        log.warn("Request body not readable: {}", detail);
        return ResponseEntity.badRequest()
                .body(Map.of("error", "Invalid request body: " + detail));
    }

    /**
     * Catch-all handler for unexpected exceptions.
     *
     * <p>Logs only the exception class name — NOT {@code ex.getMessage()} which could
     * contain PHI from entity field validation, Hibernate constraint messages, or
     * repository error details. Returns a generic error message to the client.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneric(Exception ex) {
        log.error("Unhandled exception: {}", ex.getClass().getSimpleName());
        return ResponseEntity.internalServerError()
                .body(Map.of("error", "An internal error occurred"));
    }
}
