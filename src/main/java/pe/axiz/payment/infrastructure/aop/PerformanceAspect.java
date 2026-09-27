package pe.axiz.payment.infrastructure.aop;

import io.micrometer.core.instrument.MeterRegistry; import org.aspectj.lang.ProceedingJoinPoint; import org.aspectj.lang.annotation.*; import org.slf4j.Logger; import org.slf4j.LoggerFactory; import org.springframework.core.annotation.Order; import org.springframework.stereotype.Component;
import pe.axiz.payment.infrastructure.aop.annotation.MeasuredOperation; import java.util.concurrent.TimeUnit;

@Aspect @Component @Order(20)
public class PerformanceAspect {
    private static final Logger log=LoggerFactory.getLogger(PerformanceAspect.class); private final MeterRegistry registry;
    public PerformanceAspect(MeterRegistry registry){this.registry=registry;}
    @Around("@annotation(measured)")
    public Object measure(ProceedingJoinPoint pjp, MeasuredOperation measured) throws Throwable {
        long start=System.nanoTime();
        try { return pjp.proceed(); }
        finally { long elapsed=System.nanoTime()-start; registry.timer("payment.aop.operation", "operation", measured.value()).record(elapsed, TimeUnit.NANOSECONDS); long ms=TimeUnit.NANOSECONDS.toMillis(elapsed); if(ms>=measured.warnAfterMs()) log.warn("Operación lenta operation={} elapsedMs={}", measured.value(), ms); }
    }
}