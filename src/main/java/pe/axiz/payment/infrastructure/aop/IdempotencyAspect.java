package pe.axiz.payment.infrastructure.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.annotation.Order;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pe.axiz.payment.infrastructure.aop.annotation.IdempotentOperation;
import pe.axiz.payment.infrastructure.persistence.idempotency.IdempotencyEntity;
import pe.axiz.payment.infrastructure.persistence.idempotency.IdempotencyRepository;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

@Aspect
@Component
@Order(0)
public class IdempotencyAspect {

    private final IdempotencyRepository repository;
    private final JsonMapper mapper;
    private final SpelExpressionParser parser = new SpelExpressionParser();
    private final DefaultParameterNameDiscoverer names = new DefaultParameterNameDiscoverer();

    public IdempotencyAspect(IdempotencyRepository repository, JsonMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Around("@annotation(annotation)")
    @Transactional
    public Object around(ProceedingJoinPoint pjp, IdempotentOperation annotation) throws Throwable {
        String key = evaluateKey(pjp, annotation.key());
        var existing = repository.findById(key);

        if (existing.isPresent() && "COMPLETED".equals(existing.get().status())) {
            Class<?> returnType = ((MethodSignature) pjp.getSignature()).getReturnType();
            return mapper.readValue(existing.get().responseJson(), returnType);
        }

        repository.saveAndFlush(IdempotencyEntity.started(key, Instant.now()));

        try {
            Object result = pjp.proceed();
            repository.save(
                    IdempotencyEntity.completed(
                            key,
                            mapper.writeValueAsString(result),
                            Instant.now()
                    )
            );
            return result;
        } catch (Throwable error) {
            repository.deleteById(key);
            throw error;
        }
    }

    private String evaluateKey(ProceedingJoinPoint pjp, String expression) {
        var signature = (MethodSignature) pjp.getSignature();
        var context = new StandardEvaluationContext();
        var parameterNames = names.getParameterNames(signature.getMethod());

        if (parameterNames == null) {
            throw new IllegalStateException("No se pudieron descubrir los parámetros");
        }

        Object[] args = pjp.getArgs();
        for (int i = 0; i < parameterNames.length; i++) {
            context.setVariable(parameterNames[i], args[i]);
        }

        String key = parser.parseExpression(expression).getValue(context, String.class);
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("La llave de idempotencia es obligatoria");
        }
        return key;
    }
}
