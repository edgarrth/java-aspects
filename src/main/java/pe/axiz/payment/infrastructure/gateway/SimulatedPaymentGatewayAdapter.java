package pe.axiz.payment.infrastructure.gateway;

import org.springframework.stereotype.Component; import pe.axiz.payment.application.port.out.PaymentGatewayPort; import pe.axiz.payment.domain.model.Payment; import pe.axiz.payment.infrastructure.aop.annotation.RetryTransient;
import java.math.BigDecimal; import java.util.UUID; import java.util.concurrent.ConcurrentHashMap; import java.util.concurrent.atomic.AtomicInteger;

@Component
public class SimulatedPaymentGatewayAdapter implements PaymentGatewayPort {
    private final ConcurrentHashMap<UUID, AtomicInteger> attempts=new ConcurrentHashMap<>();
    @Override @RetryTransient(maxAttempts=3, backoffMs=75)
    public String authorize(Payment payment){ int attempt=attempts.computeIfAbsent(payment.id(), ignored->new AtomicInteger()).incrementAndGet(); if(payment.money().amount().compareTo(new BigDecimal("13.37"))==0 && attempt<3) throw new TransientGatewayException("Falla transitoria simulada en intento "+attempt); return "gw_"+UUID.randomUUID(); }
    @Override @RetryTransient public void capture(Payment payment) { }
    @Override @RetryTransient public void refund(Payment payment) { }
}