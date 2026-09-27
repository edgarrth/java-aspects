package pe.axiz.payment.infrastructure.persistence.audit;
import jakarta.persistence.*; import java.time.Instant; import java.util.UUID;
@Entity @Table(name="audit_events") public class AuditEventEntity {
 @Id private UUID id; @Column(nullable=false) private String action; @Column(nullable=false) private String method; @Column(nullable=false) private String outcome; private String errorType; @Column(nullable=false) private Instant occurredAt;
 protected AuditEventEntity(){} private AuditEventEntity(UUID id,String action,String method,String outcome,String errorType,Instant occurredAt){this.id=id;this.action=action;this.method=method;this.outcome=outcome;this.errorType=errorType;this.occurredAt=occurredAt;}
 public static AuditEventEntity success(String action,String method,Instant at){return new AuditEventEntity(UUID.randomUUID(),action,method,"SUCCESS",null,at);} public static AuditEventEntity failure(String action,String method,String error,Instant at){return new AuditEventEntity(UUID.randomUUID(),action,method,"FAILURE",error,at);}
}