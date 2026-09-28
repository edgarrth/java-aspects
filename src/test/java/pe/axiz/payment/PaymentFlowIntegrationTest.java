package pe.axiz.payment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import pe.axiz.payment.application.view.PaymentView;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class PaymentFlowIntegrationTest {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Value("${local.server.port}")
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JsonMapper mapper;

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @Test
    void fullFlowValidatesFlywayAspectsAndHttpErrors() throws Exception {
        assertThat(jdbc.queryForObject("select max(version) from flyway_schema_history", String.class))
                .isEqualTo("3");

        String merchant = "integration-" + UUID.randomUUID();
        String key = "integration-" + UUID.randomUUID();
        String body = body(merchant, "13.37");

        var createdResponse = send("POST", "/api/v1/payments", key, body);
        assertThat(createdResponse.statusCode()).isEqualTo(201);
        PaymentView created = payment(createdResponse);
        assertThat(created.status()).isEqualTo("AUTHORIZED");

        var repeated = send("POST", "/api/v1/payments", key, body);
        assertThat(repeated.statusCode()).isEqualTo(201);
        assertThat(payment(repeated).id()).isEqualTo(created.id());

        var differentRequest = send("POST", "/api/v1/payments", key, body(merchant, "14.00"));
        assertThat(differentRequest.statusCode()).isEqualTo(409);
        assertThat(jdbc.queryForObject("select count(*) from payments where merchant_reference = ?",
                Integer.class, merchant)).isEqualTo(1);

        assertThat(payment(send("GET", "/api/v1/payments/" + created.id(), null, null)).status())
                .isEqualTo("AUTHORIZED");
        assertThat(payment(send("POST", "/api/v1/payments/" + created.id() + "/capture", null, null)).status())
                .isEqualTo("CAPTURED");
        assertThat(send("POST", "/api/v1/payments/" + created.id() + "/capture", null, null).statusCode())
                .isEqualTo(400);
        assertThat(payment(send("POST", "/api/v1/payments/" + created.id() + "/refund", null, null)).status())
                .isEqualTo("REFUNDED");
        assertThat(send("GET", "/api/v1/payments/" + UUID.randomUUID(), null, null).statusCode())
                .isEqualTo(404);

        assertThat(jdbc.queryForObject("select count(*) from audit_events where action = 'CREATE_PAYMENT' and outcome = 'SUCCESS'",
                Integer.class)).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from audit_events where action = 'CAPTURE_PAYMENT' and outcome = 'FAILURE'",
                Integer.class)).isGreaterThanOrEqualTo(1);
        assertThat(send("GET", "/actuator/metrics/payment.aop.operation", null, null).statusCode())
                .isEqualTo(200);
        Map<?, ?> diagnostics = mapper.readValue(
                send("GET", "/api/v1/aop/diagnostics", null, null).body(), Map.class);
        assertThat(((Number) diagnostics.get("domainConstructorsWoven")).longValue()).isGreaterThan(0);
        assertThat(((Number) diagnostics.get("privateDomainMethodsWoven")).longValue()).isGreaterThan(0);
    }

    @Test
    void concurrentRequestsWithTheSameKeyCreateOnePayment() throws Exception {
        String merchant = "concurrent-" + UUID.randomUUID();
        String key = "concurrent-" + UUID.randomUUID();
        String body = body(merchant, "13.37");
        var start = new CountDownLatch(1);

        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> {
                start.await();
                return send("POST", "/api/v1/payments", key, body);
            });
            var second = workers.submit(() -> {
                start.await();
                return send("POST", "/api/v1/payments", key, body);
            });
            start.countDown();

            var firstResponse = first.get(30, TimeUnit.SECONDS);
            var secondResponse = second.get(30, TimeUnit.SECONDS);
            assertThat(firstResponse.statusCode()).isEqualTo(201);
            assertThat(secondResponse.statusCode()).isEqualTo(201);
            assertThat(payment(firstResponse).id()).isEqualTo(payment(secondResponse).id());
        }

        assertThat(jdbc.queryForObject("select count(*) from payments where merchant_reference = ?",
                Integer.class, merchant)).isEqualTo(1);
    }

    private HttpResponse<String> send(String method, String path, String key, String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(20));
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        if (body != null) {
            request.header("Content-Type", "application/json");
        }
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private PaymentView payment(HttpResponse<String> response) {
        return mapper.readValue(response.body(), PaymentView.class);
    }

    private static String body(String merchant, String amount) {
        return "{\"merchantReference\":\"" + merchant + "\",\"amount\":" + amount + ",\"currency\":\"PEN\"}";
    }
}
