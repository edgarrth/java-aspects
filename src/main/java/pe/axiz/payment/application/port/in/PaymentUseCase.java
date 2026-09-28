package pe.axiz.payment.application.port.in;

import pe.axiz.payment.application.command.CreatePaymentCommand;
import pe.axiz.payment.application.view.PaymentView;

import java.util.UUID;

public interface PaymentUseCase {
    PaymentView create(CreatePaymentCommand command);

    PaymentView capture(UUID id);

    PaymentView refund(UUID id);

    PaymentView find(UUID id);
}