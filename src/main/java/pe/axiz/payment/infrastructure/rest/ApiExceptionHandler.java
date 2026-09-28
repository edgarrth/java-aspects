package pe.axiz.payment.infrastructure.rest;
import org.springframework.http.*; import org.springframework.web.bind.MethodArgumentNotValidException; import org.springframework.web.bind.annotation.*; import pe.axiz.payment.application.exception.PaymentNotFoundException; import pe.axiz.payment.domain.exception.InvalidPaymentTransitionException; import pe.axiz.payment.infrastructure.aop.IdempotencyConflictException; import pe.axiz.payment.infrastructure.gateway.TransientGatewayException; import java.time.Instant; import java.util.Map;
@RestControllerAdvice public class ApiExceptionHandler {
 @ExceptionHandler({IllegalArgumentException.class,InvalidPaymentTransitionException.class}) ResponseEntity<?> badRequest(RuntimeException ex){return response(HttpStatus.BAD_REQUEST,ex);}
 @ExceptionHandler(PaymentNotFoundException.class) ResponseEntity<?> notFound(PaymentNotFoundException ex){return response(HttpStatus.NOT_FOUND,ex);}
 @ExceptionHandler(IdempotencyConflictException.class) ResponseEntity<?> conflict(IdempotencyConflictException ex){return response(HttpStatus.CONFLICT,ex);}
 @ExceptionHandler(TransientGatewayException.class) ResponseEntity<?> gateway(TransientGatewayException ex){return response(HttpStatus.SERVICE_UNAVAILABLE,ex);}
 @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<?> validation(MethodArgumentNotValidException ex){return ResponseEntity.badRequest().body(Map.of("timestamp",Instant.now(),"status",400,"error","VALIDATION_ERROR","message","Request inválido"));}
 private ResponseEntity<?> response(HttpStatus status,RuntimeException ex){return ResponseEntity.status(status).body(Map.of("timestamp",Instant.now(),"status",status.value(),"error",status.getReasonPhrase(),"message",ex.getMessage()));}
}
