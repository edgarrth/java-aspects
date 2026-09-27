package pe.axiz.payment.infrastructure.aop.annotation;
import java.lang.annotation.*;
@Target(ElementType.METHOD) @Retention(RetentionPolicy.RUNTIME) public @interface MeasuredOperation { String value(); long warnAfterMs() default 250; }