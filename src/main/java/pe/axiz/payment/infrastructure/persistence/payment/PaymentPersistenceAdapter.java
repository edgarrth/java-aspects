package pe.axiz.payment.infrastructure.persistence.payment;
import org.springframework.stereotype.Component; import pe.axiz.payment.application.port.out.PaymentRepositoryPort; import pe.axiz.payment.domain.model.*; import java.util.*;
@Component public class PaymentPersistenceAdapter implements PaymentRepositoryPort {
 private final SpringDataPaymentRepository repo; public PaymentPersistenceAdapter(SpringDataPaymentRepository repo){this.repo=repo;}
 public Payment save(Payment p){ var e=repo.save(new PaymentEntity(p.id(),p.merchantReference(),p.money().amount(),p.money().currency().getCurrencyCode(),p.status().name(),p.gatewayReference(),p.createdAt(),p.updatedAt())); return toDomain(e); }
 public Optional<Payment> findById(UUID id){return repo.findById(id).map(this::toDomain);} private Payment toDomain(PaymentEntity e){return Payment.rehydrate(e.id(),e.merchantReference(),new Money(e.amount(),Currency.getInstance(e.currency())),PaymentStatus.valueOf(e.status()),e.gatewayReference(),e.createdAt(),e.updatedAt());}
}