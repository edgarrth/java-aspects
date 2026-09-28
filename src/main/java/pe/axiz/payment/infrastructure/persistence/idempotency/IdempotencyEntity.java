package pe.axiz.payment.infrastructure.persistence.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "idempotency_records")
public class IdempotencyEntity {
    @Id
    @Column(name = "idempotency_key", length = 120)
    private String key;

    @Column(nullable = false)
    private String status;

    @Column(columnDefinition = "text")
    private String responseJson;

    @Column(name = "request_hash", length = 64)
    private String requestHash;

    @Column(nullable = false)
    private Instant updatedAt;

    protected IdempotencyEntity() {
    }

    private IdempotencyEntity(String key, String status, String responseJson, String requestHash, Instant updatedAt) {
        this.key = key;
        this.status = status;
        this.responseJson = responseJson;
        this.requestHash = requestHash;
        this.updatedAt = updatedAt;
    }

    public static IdempotencyEntity completed(String key, String json, String requestHash, Instant at) {
        return new IdempotencyEntity(key, "COMPLETED", json, requestHash, at);
    }

    public String status() {
        return status;
    }

    public String responseJson() {
        return responseJson;
    }

    public String requestHash() {
        return requestHash;
    }
}
