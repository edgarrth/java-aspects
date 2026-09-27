package pe.axiz.payment.infrastructure.aop;

import org.junit.jupiter.api.Test;
import pe.axiz.payment.domain.model.Money;
import pe.axiz.payment.domain.model.Payment;

import java.math.BigDecimal;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;

class DomainWeavingAspectTest {
    @Test
    void shouldWeaveConstructorAndPrivateDomainMethods() {
        long constructorsBefore = DomainWeavingAspect.constructorJoinPoints();
        long privateBefore = DomainWeavingAspect.privateMethodJoinPoints();
        var payment = Payment.create("weaving-test", new Money(new BigDecimal("20.00"), Currency.getInstance("PEN")));
        payment.authorize("gw-test");
        assertThat(DomainWeavingAspect.constructorJoinPoints()).isGreaterThan(constructorsBefore);
        assertThat(DomainWeavingAspect.privateMethodJoinPoints()).isGreaterThan(privateBefore);
    }
}
