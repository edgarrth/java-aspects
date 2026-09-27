package pe.axiz.payment.application.view;
import pe.axiz.payment.domain.model.Payment;
import java.math.BigDecimal; import java.time.Instant; import java.util.UUID;
public record PaymentView(UUID id, String merchantReference, BigDecimal amount, String currency, String status, String gatewayReference, Instant createdAt, Instant updatedAt) {
    public static PaymentView from(Payment p){ return new PaymentView(p.id(), p.merchantReference(), p.money().amount(), p.money().currency().getCurrencyCode(), p.status().name(), p.gatewayReference(), p.createdAt(), p.updatedAt()); }
}