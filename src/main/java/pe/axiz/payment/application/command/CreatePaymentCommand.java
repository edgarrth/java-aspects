package pe.axiz.payment.application.command;
import java.math.BigDecimal;
public record CreatePaymentCommand(String idempotencyKey, String merchantReference, BigDecimal amount, String currency) {}