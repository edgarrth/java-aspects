package pe.axiz.payment.infrastructure.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.annotation.Order;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.axiz.payment.infrastructure.aop.annotation.IdempotentOperation;
import pe.axiz.payment.infrastructure.persistence.idempotency.IdempotencyEntity;
import pe.axiz.payment.infrastructure.persistence.idempotency.IdempotencyRepository;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;

@Aspect
@Component
@Order(0)
public class IdempotencyAspect {

    private final IdempotencyRepository repository;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final JsonMapper mapper;
    private final SpelExpressionParser parser = new SpelExpressionParser();
    private final DefaultParameterNameDiscoverer names = new DefaultParameterNameDiscoverer();

    public IdempotencyAspect(IdempotencyRepository repository, JdbcTemplate jdbc,
                             PlatformTransactionManager transactionManager, JsonMapper mapper) {
        this.repository = repository;
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
        this.mapper = mapper;
    }

    @Around("@annotation(annotation)")
    public Object around(ProceedingJoinPoint pjp, IdempotentOperation annotation) throws Throwable {
        String key = evaluateKey(pjp, annotation.key());
        String requestHash = hashRequest(pjp);
        try {
            return transactions.execute(status -> {
                try {
                    return process(pjp, key, requestHash);
                } catch (RuntimeException | Error error) {
                    throw error;
                } catch (Throwable error) {
                    throw new CheckedOperationException(error);
                }
            });
        } catch (CheckedOperationException error) {
            throw error.getCause();
        }
    }

    private Object process(ProceedingJoinPoint pjp, String key, String requestHash) throws Throwable {
        // La PRIMARY KEY serializa solicitudes concurrentes con la misma clave.
        int claimed = jdbc.update("""
                INSERT INTO idempotency_records (idempotency_key, status, request_hash, updated_at)
                VALUES (?, 'STARTED', ?, ?)
                ON CONFLICT (idempotency_key) DO NOTHING
                """, key, requestHash, OffsetDateTime.now(ZoneOffset.UTC));

        if (claimed == 0) {
            var existing = repository.findById(key).orElseThrow();
            if (!requestHash.equals(existing.requestHash())) {
                throw new IdempotencyConflictException("La llave de idempotencia ya se usó con otra solicitud");
            }
            if (!"COMPLETED".equals(existing.status())) {
                throw new IdempotencyConflictException("La solicitud con esta llave sigue en proceso");
            }
            Class<?> returnType = ((MethodSignature) pjp.getSignature()).getReturnType();
            return mapper.readValue(existing.responseJson(), returnType);
        }

        Object result = pjp.proceed();
        repository.save(IdempotencyEntity.completed(
                key, mapper.writeValueAsString(result), requestHash, Instant.now()));
        return result;
    }

    private String hashRequest(ProceedingJoinPoint pjp) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(((MethodSignature) pjp.getSignature()).getMethod().toGenericString()
                    .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest(mapper.writeValueAsBytes(pjp.getArgs())));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 no disponible", error);
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

    private static final class CheckedOperationException extends RuntimeException {
        private CheckedOperationException(Throwable cause) {
            super(cause);
        }
    }
}
