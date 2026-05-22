package com.example.control2_backend.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // Manejo de validaciones fallidas (ej: @NotBlank, @NotNull)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(
            MethodArgumentNotValidException ex, WebRequest request) {

        Map<String, String> errores = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String nombreCampo = ((FieldError) error).getField();
            String mensaje = error.getDefaultMessage();
            errores.put(nombreCampo, mensaje);
        });

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            Map.of(
                "timestamp", LocalDateTime.now(),
                "status", HttpStatus.BAD_REQUEST.value(),
                "error", "Validación fallida",
                "detalles", errores
            )
        );
    }

    // Manejo de errores de argumento (ej: usuario ya existe)
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(
            IllegalArgumentException ex, WebRequest request) {

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            Map.of(
                "timestamp", LocalDateTime.now(),
                "status", HttpStatus.BAD_REQUEST.value(),
                "error", "Argumento inválido",
                "mensaje", ex.getMessage()
            )
        );
    }

    // Manejo de acceso denegado
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDeniedException(
            AccessDeniedException ex, WebRequest request) {

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
            Map.of(
                "timestamp", LocalDateTime.now(),
                "status", HttpStatus.FORBIDDEN.value(),
                "error", "Acceso denegado",
                "mensaje", "No tienes permisos para acceder a este recurso"
            )
        );
    }

    // Manejo genérico de RuntimeException (ej: usuario no encontrado)
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(
            RuntimeException ex, WebRequest request) {

        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        String mensaje = ex.getMessage();

        // Detectar si es una excepción conocida
        if (mensaje != null && mensaje.contains("no encontrado")) {
            status = HttpStatus.NOT_FOUND;
        } else if (mensaje != null && mensaje.contains("No autorizado")) {
            status = HttpStatus.FORBIDDEN;
        }

        return ResponseEntity.status(status).body(
            Map.of(
                "timestamp", LocalDateTime.now(),
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "mensaje", mensaje
            )
        );
    }

    // Manejo genérico para cualquier otra excepción
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(
            Exception ex, WebRequest request) {

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            Map.of(
                "timestamp", LocalDateTime.now(),
                "status", HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "error", "Error interno del servidor",
                "mensaje", "Ocurrió un error inesperado. Por favor intenta más tarde."
            )
        );
    }
}
