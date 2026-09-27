package pe.axiz.payment.application.port.out;
import pe.axiz.payment.domain.model.Payment; import java.util.Optional; import java.util.UUID;
public interface PaymentRepositoryPort { Payment save(Payment payment); Optional<Payment> findById(UUID id); }