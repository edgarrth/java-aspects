package pe.axiz.payment.infrastructure.aop;

import org.aspectj.lang.ProceedingJoinPoint; import org.aspectj.lang.annotation.*; import org.springframework.core.annotation.Order; import org.springframework.stereotype.Component;
import pe.axiz.payment.infrastructure.aop.annotation.RetryTransient; import pe.axiz.payment.infrastructure.gateway.TransientGatewayException;

@Aspect @Component @Order(10)
public class RetryAspect {
    @Around("@annotation(retry)")
    public Object retry(ProceedingJoinPoint pjp, RetryTransient retry) throws Throwable {
        int attempt=0;
        while(true){ try { attempt++; return pjp.proceed(); } catch(TransientGatewayException ex){ if(attempt>=retry.maxAttempts()) throw ex; try { Thread.sleep(retry.backoffMs()*attempt); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw interrupted; } } }
    }
}