package pe.axiz.payment.infrastructure.persistence.payment;

import jakarta.persistence.*; import java.math.BigDecimal; import java.time.Instant; import java.util.UUID;
@Entity @Table(name="payments")
public class PaymentEntity {
 @Id private UUID id; @Column(nullable=false) private String merchantReference; @Column(nullable=false,precision=19,scale=2) private BigDecimal amount; @Column(nullable=false,length=3) private String currency; @Column(nullable=false) private String status; private String gatewayReference; @Column(nullable=false) private Instant createdAt; @Column(nullable=false) private Instant updatedAt;
 protected PaymentEntity(){} public PaymentEntity(UUID id,String merchantReference,BigDecimal amount,String currency,String status,String gatewayReference,Instant createdAt,Instant updatedAt){this.id=id;this.merchantReference=merchantReference;this.amount=amount;this.currency=currency;this.status=status;this.gatewayReference=gatewayReference;this.createdAt=createdAt;this.updatedAt=updatedAt;}
 public UUID id(){return id;} public String merchantReference(){return merchantReference;} public BigDecimal amount(){return amount;} public String currency(){return currency;} public String status(){return status;} public String gatewayReference(){return gatewayReference;} public Instant createdAt(){return createdAt;} public Instant updatedAt(){return updatedAt;}
}