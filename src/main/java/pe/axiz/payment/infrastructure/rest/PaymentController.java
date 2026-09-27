package pe.axiz.payment.infrastructure.rest;
import jakarta.validation.Valid; import jakarta.validation.constraints.*; import org.springframework.http.*; import org.springframework.web.bind.annotation.*; import pe.axiz.payment.application.command.CreatePaymentCommand; import pe.axiz.payment.application.port.in.PaymentUseCase; import pe.axiz.payment.application.view.PaymentView;
import java.math.BigDecimal; import java.net.URI; import java.util.UUID;
@RestController @RequestMapping("/api/v1/payments") public class PaymentController {
 private final PaymentUseCase useCase; public PaymentController(PaymentUseCase useCase){this.useCase=useCase;}
 @PostMapping public ResponseEntity<PaymentView> create(@RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey,@Valid @RequestBody CreatePaymentRequest request){var result=useCase.create(new CreatePaymentCommand(idempotencyKey,request.merchantReference(),request.amount(),request.currency())); return ResponseEntity.created(URI.create("/api/v1/payments/"+result.id())).body(result);}
 @GetMapping("/{id}") public PaymentView find(@PathVariable UUID id){return useCase.find(id);} @PostMapping("/{id}/capture") public PaymentView capture(@PathVariable UUID id){return useCase.capture(id);} @PostMapping("/{id}/refund") public PaymentView refund(@PathVariable UUID id){return useCase.refund(id);}
 public record CreatePaymentRequest(@NotBlank String merchantReference,@NotNull @DecimalMin("0.01") BigDecimal amount,@NotBlank @Pattern(regexp="[A-Z]{3}") String currency){}
}