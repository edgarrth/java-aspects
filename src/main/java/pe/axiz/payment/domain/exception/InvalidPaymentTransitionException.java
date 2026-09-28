package pe.axiz.payment.domain.exception;

import pe.axiz.payment.domain.model.PaymentStatus;

public class InvalidPaymentTransitionException extends RuntimeException {
    public InvalidPaymentTransitionException(PaymentStatus current, PaymentStatus expected) {
        super("Transición inválida. Estado actual=" + current + ", estado requerido=" + expected);
    }
}