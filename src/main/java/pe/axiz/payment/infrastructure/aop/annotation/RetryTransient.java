package pe.axiz.payment.infrastructure.aop.annotation;
import java.lang.annotation.*;
@Target(ElementType.METHOD) @Retention(RetentionPolicy.RUNTIME) public @interface RetryTransient { int maxAttempts() default 3; long backoffMs() default 50; }