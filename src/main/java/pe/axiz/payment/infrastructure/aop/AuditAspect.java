package pe.axiz.payment.infrastructure.aop;

import org.aspectj.lang.JoinPoint; import org.aspectj.lang.annotation.*; import org.springframework.core.annotation.Order; import org.springframework.stereotype.Component;
import pe.axiz.payment.infrastructure.aop.annotation.Auditable; import pe.axiz.payment.infrastructure.persistence.audit.AuditEventEntity; import pe.axiz.payment.infrastructure.persistence.audit.AuditEventRepository;
import java.time.Instant;

@Aspect @Component @Order(30)
public class AuditAspect {
    private final AuditEventRepository repository;
    public AuditAspect(AuditEventRepository repository){this.repository=repository;}
    @AfterReturning(pointcut="@annotation(auditable)", returning="result")
    public void success(JoinPoint jp, Auditable auditable, Object result){ repository.save(AuditEventEntity.success(auditable.action(), jp.getSignature().toShortString(), Instant.now())); }
    @AfterThrowing(pointcut="@annotation(auditable)", throwing="error")
    public void failure(JoinPoint jp, Auditable auditable, Throwable error){ repository.save(AuditEventEntity.failure(auditable.action(), jp.getSignature().toShortString(), error.getClass().getSimpleName(), Instant.now())); }
}