package pe.axiz.payment.domain.model;

import pe.axiz.payment.domain.exception.InvalidPaymentTransitionException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class Payment {
    private final UUID id;
    private final String merchantReference;
    private final Money money;
    private PaymentStatus status;
    private String gatewayReference;
    private final Instant createdAt;
    private Instant updatedAt;

    private Payment(UUID id, String merchantReference, Money money, PaymentStatus status, String gatewayReference, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id);
        this.merchantReference = Objects.requireNonNull(merchantReference);
        this.money = Objects.requireNonNull(money);
        this.status = Objects.requireNonNull(status);
        this.gatewayReference = gatewayReference;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    public static Payment create(String merchantReference, Money money) {
        var now = Instant.now();
        return new Payment(UUID.randomUUID(), merchantReference, money, PaymentStatus.CREATED, null, now, now);
    }

    public static Payment rehydrate(UUID id, String merchantReference, Money money, PaymentStatus status, String gatewayReference, Instant createdAt, Instant updatedAt) {
        return new Payment(id, merchantReference, money, status, gatewayReference, createdAt, updatedAt);
    }

    public void authorize(String reference) {
        requireStatus(PaymentStatus.CREATED);
        gatewayReference = Objects.requireNonNull(reference);
        moveTo(PaymentStatus.AUTHORIZED);
    }

    public void capture() {
        requireStatus(PaymentStatus.AUTHORIZED);
        moveTo(PaymentStatus.CAPTURED);
    }

    public void refund() {
        requireStatus(PaymentStatus.CAPTURED);
        moveTo(PaymentStatus.REFUNDED);
    }

    public void fail() {
        if (status == PaymentStatus.CAPTURED || status == PaymentStatus.REFUNDED)
            throw new InvalidPaymentTransitionException(status, PaymentStatus.FAILED);
        moveTo(PaymentStatus.FAILED);
    }

    private void requireStatus(PaymentStatus expected) {
        if (status != expected) throw new InvalidPaymentTransitionException(status, expected);
    }

    private void moveTo(PaymentStatus target) {
        status = target;
        updatedAt = Instant.now();
    }

    public UUID id() {
        return id;
    }

    public String merchantReference() {
        return merchantReference;
    }

    public Money money() {
        return money;
    }

    public PaymentStatus status() {
        return status;
    }

    public String gatewayReference() {
        return gatewayReference;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}