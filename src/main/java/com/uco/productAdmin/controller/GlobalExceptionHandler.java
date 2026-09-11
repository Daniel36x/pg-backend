package com.uco.productAdmin.controller; // O el paquete donde decidas ponerlo

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException ex) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("timestamp", LocalDateTime.now());
        // Forzamos a que sea un error 404 (Not Found) o 400 (Bad Request) en vez de 500
        errorResponse.put("status", HttpStatus.NOT_FOUND.value());
        errorResponse.put("error", "Error en la operación");
        errorResponse.put("message", ex.getMessage()); // Aquí veremos TU mensaje real

        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }
}