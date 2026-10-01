package br.com.controle.gastos.api;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiErrorHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiErrorHandler.class);

    public record ErrorBody(String message, Map<String, String> fields) {}

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorBody> handleApi(ApiException error) {
        return ResponseEntity.status(error.status()).body(new ErrorBody(error.getMessage(), Map.of()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorBody> handleValidation(MethodArgumentNotValidException error) {
        Map<String, String> fields = new LinkedHashMap<>();
        error.getBindingResult().getFieldErrors().forEach(field -> fields.putIfAbsent(field.getField(), field.getDefaultMessage()));
        return ResponseEntity.badRequest().body(new ErrorBody("Confira os campos informados.", fields));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            ConstraintViolationException.class, HandlerMethodValidationException.class})
    ResponseEntity<ErrorBody> handleMalformed(Exception error) {
        return ResponseEntity.badRequest().body(new ErrorBody("Dados inválidos. Confira datas, valores e parâmetros.", Map.of()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ErrorBody> handleConflict(DataIntegrityViolationException error) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorBody("O registro já existe ou está sendo usado. Atualize a página e tente novamente.", Map.of()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorBody> handleUnexpected(Exception error) {
        if (error instanceof ErrorResponse response) {
            String message = switch (response.getStatusCode().value()) {
                case 404 -> "Recurso não encontrado.";
                case 405 -> "Método HTTP não permitido para este recurso.";
                case 415 -> "Envie os dados no formato JSON (application/json).";
                default -> "Solicitação inválida. Confira os dados enviados.";
            };
            return ResponseEntity.status(response.getStatusCode()).body(new ErrorBody(message, Map.of()));
        }
        log.error("Falha ao processar a solicitação", error);
        return ResponseEntity.internalServerError().body(new ErrorBody("Não foi possível concluir a operação. Tente novamente.", Map.of()));
    }
}
