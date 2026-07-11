package com.aiform.id995a.controller;

import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(IOException.class)
  public ResponseEntity<String> handleIOException(IOException exception) {
    return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
        .contentType(MediaType.TEXT_PLAIN)
        .body(exception.getMessage());
  }

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<String> handleResponseStatusException(ResponseStatusException exception) {
    // Return status errors directly so expired in-memory job ids cannot stall in error dispatch.
    String message = exception.getReason();
    if (message == null || message.isBlank()) {
      message = exception.getStatusCode().toString();
    }
    return ResponseEntity.status(exception.getStatusCode())
        .contentType(MediaType.TEXT_PLAIN)
        .body(message);
  }
}
