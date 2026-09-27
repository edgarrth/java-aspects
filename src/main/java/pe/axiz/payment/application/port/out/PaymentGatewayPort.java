package pe.axiz.payment.application.port.out;
import pe.axiz.payment.domain.model.Payment;
public interface PaymentGatewayPort { String authorize(Payment payment); void capture(Payment payment); void refund(Payment payment); }