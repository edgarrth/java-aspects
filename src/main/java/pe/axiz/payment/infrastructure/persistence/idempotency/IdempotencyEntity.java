package pe.axiz.payment.infrastructure.persistence.idempotency;
import jakarta.persistence.*; import java.time.Instant;
@Entity @Table(name="idempotency_records") public class IdempotencyEntity {
 @Id @Column(name="idempotency_key",length=120) private String key; @Column(nullable=false) private String status; @Column(columnDefinition="text") private String responseJson; @Column(nullable=false) private Instant updatedAt;
 protected IdempotencyEntity(){} private IdempotencyEntity(String key,String status,String responseJson,Instant updatedAt){this.key=key;this.status=status;this.responseJson=responseJson;this.updatedAt=updatedAt;}
 public static IdempotencyEntity started(String key,Instant at){return new IdempotencyEntity(key,"STARTED",null,at);} public static IdempotencyEntity completed(String key,String json,Instant at){return new IdempotencyEntity(key,"COMPLETED",json,at);} public String status(){return status;} public String responseJson(){return responseJson;}
}