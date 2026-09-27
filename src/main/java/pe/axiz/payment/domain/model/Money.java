package pe.axiz.payment.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

public record Money(BigDecimal amount, Currency currency) {
    public Money {
        Objects.requireNonNull(amount, "El monto es obligatorio");
        Objects.requireNonNull(currency, "La moneda es obligatoria");
        amount = amount.setScale(2, RoundingMode.HALF_EVEN);
        if (amount.signum() <= 0) throw new IllegalArgumentException("El monto debe ser mayor a cero");
    }
}