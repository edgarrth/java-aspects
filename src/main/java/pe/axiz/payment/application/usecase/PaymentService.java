package pe.axiz.payment.application.usecase;

import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
import pe.axiz.payment.application.command.CreatePaymentCommand; import pe.axiz.payment.application.port.in.PaymentUseCase; import pe.axiz.payment.application.port.out.*; import pe.axiz.payment.application.view.PaymentView; import pe.axiz.payment.domain.model.*; import pe.axiz.payment.infrastructure.aop.annotation.*;
import java.util.Currency; import java.util.UUID;

@Service
public class PaymentService implements PaymentUseCase {
    private final PaymentRepositoryPort payments; private final PaymentGatewayPort gateway;
    public PaymentService(PaymentRepositoryPort payments, PaymentGatewayPort gateway){this.payments=payments;this.gateway=gateway;}
    @Override @Transactional @IdempotentOperation(key="#command.idempotencyKey()") @Auditable(action="CREATE_PAYMENT") @MeasuredOperation("payment.create")
    public PaymentView create(CreatePaymentCommand command){ var payment=Payment.create(command.merchantReference(), new Money(command.amount(), Currency.getInstance(command.currency()))); try { var reference=gateway.authorize(payment); payment.authorize(reference); return PaymentView.from(payments.save(payment)); } catch(RuntimeException ex){ payment.fail(); payments.save(payment); throw ex; } }
    @Override @Transactional @Auditable(action="CAPTURE_PAYMENT") @MeasuredOperation("payment.capture")
    public PaymentView capture(UUID id){var p=load(id); gateway.capture(p); p.capture(); return PaymentView.from(payments.save(p));}
    @Override @Transactional @Auditable(action="REFUND_PAYMENT") @MeasuredOperation("payment.refund")
    public PaymentView refund(UUID id){var p=load(id); gateway.refund(p); p.refund(); return PaymentView.from(payments.save(p));}
    @Override @Transactional(readOnly=true) public PaymentView find(UUID id){return PaymentView.from(load(id));}
    private Payment load(UUID id){return payments.findById(id).orElseThrow(() -> new IllegalArgumentException("Pago no encontrado: "+id));}
}