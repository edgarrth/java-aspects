package pe.axiz.payment.domain.model;
import org.junit.jupiter.api.Test; import java.math.BigDecimal; import java.util.Currency; import static org.assertj.core.api.Assertions.*;
class PaymentTest {
 @Test void shouldFollowPaymentLifecycle(){var p=Payment.create("order-1",new Money(new BigDecimal("100.00"),Currency.getInstance("PEN"))); p.authorize("gw-1"); p.capture(); p.refund(); assertThat(p.status()).isEqualTo(PaymentStatus.REFUNDED);}
 @Test void shouldRejectCaptureBeforeAuthorization(){var p=Payment.create("order-1",new Money(new BigDecimal("100.00"),Currency.getInstance("PEN"))); assertThatThrownBy(p::capture).isInstanceOf(RuntimeException.class);}
}