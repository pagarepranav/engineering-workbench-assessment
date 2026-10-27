package dev.pranav.workbench.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class Errors {
    private static final Logger log = LoggerFactory.getLogger(Errors.class);
    @ExceptionHandler(ApiException.class) ResponseEntity<?> expected(ApiException e) {
        return ResponseEntity.status(e.status()).body(Map.of("error", e.getMessage()));
    }
    @ExceptionHandler({HttpMessageNotReadableException.class, IllegalArgumentException.class})
    ResponseEntity<?> invalid(Exception e) { return ResponseEntity.badRequest().body(Map.of("error", "Invalid request body or value")); }
    @ExceptionHandler(DataAccessException.class) ResponseEntity<?> database(DataAccessException e) {
        String id = UUID.randomUUID().toString(); log.error("Database error incident={}", id, e);
        return ResponseEntity.status(503).body(Map.of("error", "Storage unavailable", "incident", id));
    }
    @ExceptionHandler(Exception.class) ResponseEntity<?> unexpected(Exception e) {
        String id = UUID.randomUUID().toString(); log.error("Request failed incident={}", id, e);
        return ResponseEntity.internalServerError().body(Map.of("error", "Unexpected server error", "incident", id));
    }
}
