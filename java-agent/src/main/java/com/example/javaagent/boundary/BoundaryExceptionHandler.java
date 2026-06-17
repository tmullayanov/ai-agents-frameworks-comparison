package com.example.javaagent.boundary;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.UUID;

@RestControllerAdvice
public class BoundaryExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<AgentResponse> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        String message = exception.getBindingResult().getAllErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage() == null ? "Invalid agent request." : error.getDefaultMessage())
                .orElse("Invalid agent request.");
        AgentResponse response = new AgentResponse(
                message,
                ResponseStatus.ERROR,
                null,
                AgentStructuredOutput.empty(),
                new ExecutionTrace(
                        "run-java-spring-ai-" + UUID.randomUUID(),
                        null,
                        null,
                        List.of(),
                        false,
                        null,
                        ResponseStatus.ERROR
                )
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<AgentResponse> handleUnreadableJson(HttpMessageNotReadableException exception) {
        AgentResponse response = new AgentResponse(
                "Invalid agent request JSON.",
                ResponseStatus.ERROR,
                null,
                AgentStructuredOutput.empty(),
                new ExecutionTrace(
                        "run-java-spring-ai-" + UUID.randomUUID(),
                        null,
                        null,
                        List.of(),
                        false,
                        null,
                        ResponseStatus.ERROR
                )
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
}
