# Payment Processing con Spring AOP + AspectJ

PoC de AOP de en un microservicio. El caso funcional es un flujo simple de payment processing: crear/autorizar 
un pago, consultarlo, capturarlo y devolverlo. Sobre ese flujo se implemento concerns transversales con Spring 
AOP y, donde los proxies de Spring ya no alcanzan, uso AspectJ Load-Time Weaving.

La idea principal es separar dos niveles:

- **Spring AOP** para interceptar beans administrados por Spring: idempotencia, auditoría, medición y retry.
- **AspectJ weaving** para interceptar join points del dominio que no dependen de proxies: construcción de `Payment` y ejecución de métodos privados.

Con esto se puede comparar en el mismo proyecto qué resuelve un proxy y qué resuelve realmente AspectJ.

## Stack usado

| Tecnología | Versión / criterio | Uso |
|---|---|---|
| Java | 25 | Runtime y compilación |
| Spring Boot | 4.1.1 | Base del microservicio |
| Spring Framework | 7.x administrado por Boot | Web, AOP, transacciones |
| AspectJ Weaver | 1.9.25.1 | Load-Time Weaving |
| AspectJ Maven Plugin | 1.14.1 | Compila únicamente el aspecto LTW con `ajc` |
| Maven | 3.6.3+ | Build |
| PostgreSQL | 18.6 | Persistencia de pagos, auditoría e idempotencia |
| Flyway | Versión administrada por Boot | Creación y evolución de esquema |

## Caso de uso funcional

El servicio simula un procesador de pagos con este flujo:

1. Se crea un pago con una llave de idempotencia.
2. El servicio llama a un gateway de pagos simulado para autorizarlo.
3. Si el gateway responde bien, el pago queda `AUTHORIZED`.
4. El pago puede pasar a `CAPTURED`.
5. Luego puede pasar a `REFUNDED`.
6. Si se intenta una transición inválida, el dominio la rechaza.

El monto `13.37` tiene un comportamiento especial solo para la PoC: el gateway falla transitoriamente en los dos primeros intentos y funciona en el tercero. Eso permite comprobar el aspecto de retry sin depender de un proveedor externo.

## Caso de uso técnico: AOP y AspectJ

Esta es la parte principal del proyecto.

### 1. Idempotencia declarativa

`PaymentService.create(...)` usa:

```java
@IdempotentOperation(key = "#command.idempotencyKey()")
```

`IdempotencyAspect` hace lo siguiente:

- usa SpEL para obtener dinámicamente la llave declarada en la anotación;
- reserva la llave en `idempotency_records` dentro de la misma transacción que crea el pago;
- compara la huella SHA-256 de la solicitud antes de devolver una respuesta anterior;
- si dos solicitudes iguales llegan a la vez, la segunda espera el resultado de la primera y recibe el mismo pago;
- devuelve HTTP 409 si se reutiliza la llave con otro contenido o con un registro previo todavía en proceso;
- si ocurre un error, la transacción revierte el pago y la reserva para permitir un nuevo intento.

Los registros creados antes de V3 no tienen huella de solicitud. Su llave devuelve 409; se debe usar una llave nueva.

Esto evita meter lógica de idempotencia dentro del caso de uso de pagos.

### 2. Retry para fallas transitorias

El adapter del gateway usa:

```java
@RetryTransient(maxAttempts = 3, backoffMs = 75)
```

`RetryAspect` aplica retry solo sobre `TransientGatewayException`. No reintenta cualquier excepción porque eso 
sería peligroso en pagos.

El backoff aumenta por intento y el número máximo de intentos queda declarado en la anotación.

### 3. Auditoría de resultado

Los métodos que cambian el estado del pago usan `@Auditable`.

`AuditAspect` implementa dos tipos de advice:

- `@AfterReturning` para registrar operaciones exitosas;
- `@AfterThrowing` para registrar operaciones fallidas.

Los eventos se guardan en `audit_events` y no están mezclados con la lógica de negocio.

### 4. Métricas y detección de operaciones lentas

`@MeasuredOperation` permite nombrar cada operación y definir un umbral.

`PerformanceAspect`:

- mide el tiempo con `System.nanoTime()`;
- registra un `Timer` de Micrometer;
- publica la métrica `payment.aop.operation`;
- genera warning solo cuando la operación supera el umbral configurado.

Las métricas quedan disponibles mediante Actuator.

### 5. Orden explícito de aspectos

Los aspectos tienen orden definido con `@Order`:

| Orden | Aspecto | Objetivo |
|---:|---|---|
| 0 | `IdempotencyAspect` | Evitar que una ejecución duplicada llegue al caso de uso |
| 10 | `RetryAspect` | Reintentar llamadas transitorias del gateway |
| 20 | `PerformanceAspect` | Medir la operación completa |
| 30 | `AuditAspect` | Registrar el resultado final |

Esto evita depender del orden accidental en que Spring encuentre los aspectos.

### 6. AspectJ Load-Time Weaving sobre el dominio

`DomainWeavingAspect` no es un bean de Spring. Se declara en:

```text
src/main/resources/META-INF/aop.xml
```

El weaver está restringido a:

```text
pe.axiz.payment.domain..*
```

El aspecto se compila previamente con `ajc`, pero las clases del dominio no. De esa forma los métodos 
sintéticos `aspectOf()` y `hasAspect()` existen en `DomainWeavingAspect`, mientras que `Payment` sigue siendo 
armado por LTW cuando arranca la JVM con `aspectjweaver`.

El aspecto captura dos join points que muestran la diferencia con Spring AOP:

```java
initialization(pe.axiz.payment.domain.model.Payment.new(..))
execution(private * pe.axiz.payment.domain.model.Payment.*(..))
```

Con eso se puede observar:

- construcción de objetos de dominio que no son beans de Spring;
- ejecución de métodos privados del agregado;
- llamadas internas que no pasan por un proxy de Spring.

El endpoint `/api/v1/aop/diagnostics` expone contadores de estos join points para poder verificar el weaving desde fuera.

## Arquitectura

```mermaid
flowchart LR
    Client[Cliente REST] --> Controller[PaymentController]
    Controller --> InPort[PaymentUseCase]
    InPort --> Service[PaymentService]

    subgraph Spring_AOP[Spring AOP]
      Idem[IdempotencyAspect]
      Metrics[PerformanceAspect]
      Audit[AuditAspect]
      Retry[RetryAspect]
    end

    Idem -. intercepta .-> Service
    Metrics -. intercepta .-> Service
    Audit -. intercepta .-> Service

    Service --> Domain[Payment Aggregate]
    Service --> RepoPort[PaymentRepositoryPort]
    Service --> GatewayPort[PaymentGatewayPort]

    Retry -. intercepta .-> Gateway[SimulatedPaymentGatewayAdapter]
    GatewayPort --> Gateway
    RepoPort --> JpaAdapter[PaymentPersistenceAdapter]
    JpaAdapter --> PostgreSQL[(PostgreSQL 18.6)]

    subgraph AspectJ_LTW[AspectJ Load-Time Weaving]
      DomainAspect[DomainWeavingAspect]
    end

    DomainAspect -. constructor + private methods .-> Domain
```

## DDD + hexagonal

La organización evita que el dominio dependa de Spring, JPA o HTTP.

```text
payment-aspectj-poc/
├── datasets/
│   └── load-demo.sh
├── infraestructura/
│   ├── docker-compose.yml
│   ├── requests.http
│   └── responses/          respuestas de referencia
├── scripts/
│   └── run-with-aspectj.sh
├── src/
│   ├── main/
│   │   ├── java/pe/axiz/payment/
│   │   │   ├── application/       casos de uso, comandos y puertos
│   │   │   ├── domain/            agregado Payment, Money y reglas
│   │   │   └── infrastructure/    REST, AOP, JPA y gateway
│   │   └── resources/
│   │       ├── db/migration/      Flyway
│   │       └── META-INF/aop.xml   configuración del weaver
│   └── test/                      pruebas unitarias y de weaving
├── pom.xml
└── README.md
```

## Código principal

### `Payment`

Es el agregado. Mantiene el estado y controla sus transiciones:

```text
CREATED -> AUTHORIZED -> CAPTURED -> REFUNDED
```

La lógica de transición está en el dominio, no en el controller ni en JPA.

### `PaymentService`

Implementa el puerto de entrada `PaymentUseCase`. Orquesta dominio, persistencia y gateway. Las preocupaciones transversales se agregan mediante anotaciones y aspectos.

### `PaymentPersistenceAdapter`

Implementa `PaymentRepositoryPort`. Convierte entre el agregado y `PaymentEntity` para que JPA no contamine el dominio.

### `SimulatedPaymentGatewayAdapter`

Implementa `PaymentGatewayPort`. Sirve para probar el flujo sin integrar un PSP real. El monto `13.37` provoca dos fallas transitorias antes de responder correctamente.

### `DomainWeavingAspect`

Es el aspecto específico de AspectJ. Existe para demostrar join points que Spring AOP por proxy no puede cubrir.

## Endpoints en orden de ejecución

| Orden | Método | Endpoint | Función | Qué demuestra técnicamente |
|---:|---|---|---|---|
| 1 | POST | `/api/v1/payments` | Crea y autoriza un pago | idempotencia, auditoría, métricas, retry, weaving del dominio |
| 2 | GET | `/api/v1/payments/{id}` | Consulta el pago | puerto de entrada + persistencia hexagonal |
| 3 | POST | `/api/v1/payments/{id}/capture` | Captura un pago autorizado | auditoría, métricas y reglas de transición |
| 4 | POST | `/api/v1/payments/{id}/refund` | Devuelve un pago capturado | auditoría, métricas y reglas de transición |
| 5 | GET | `/api/v1/aop/diagnostics` | Muestra contadores del weaving | prueba que AspectJ interceptó constructor y métodos privados |
| 6 | GET | `/actuator/metrics/payment.aop.operation` | Consulta la métrica AOP | instrumentación generada por `PerformanceAspect` |

## Levantar infraestructura

La única infraestructura externa es PostgreSQL.

Desde la raíz:

```bash
cd infraestructura
docker compose up
```

Para apagarlo:

```bash
docker compose down
```

Para borrar también el volumen y empezar desde cero:

```bash
docker compose down -v
```

## Base de datos y Flyway

No hay scripts SQL duplicados dentro de `infraestructura`.

El esquema se administra únicamente con Flyway:

```text
src/main/resources/db/migration/V1__create_payment_tables.sql
src/main/resources/db/migration/V2__change_payment_currency_to_varchar.sql
src/main/resources/db/migration/V3__add_idempotency_request_hash.sql
```

Flyway crea:

- `payments`;
- `audit_events`;
- `idempotency_records`.
Hibernate está configurado con `ddl-auto: validate`, así que valida el modelo pero no crea ni modifica tablas por detrás.

## Compilar y ejecutar

### Requisitos

```text
JDK 25
Maven 3.6.3 o superior
Docker + Docker Compose
```


### 1. Compilar y ejecutar pruebas

Desde la raíz:

```bash
mvn clean verify
```

Durante `compile`, `maven-compiler-plugin` compila la aplicación con Java 25 y `aspectj-maven-plugin` recompila solamente `DomainWeavingAspect` con `ajc`. Esto evita hacer compile-time weaving del dominio y genera la infraestructura AspectJ que necesita LTW.

Las pruebas de integración usan Testcontainers con PostgreSQL 18.6 y necesitan Docker activo. Cubren migraciones, API, idempotencia concurrente, auditoría, métricas y weaving. El plugin de AspectJ recompila el aspecto en cada build para preservar `aspectOf()` también en compilaciones incrementales.

El `maven-surefire-plugin` arranca las pruebas con:

```text
-javaagent:aspectjweaver-1.9.25.1.jar
```

Por eso `DomainWeavingAspectTest` no solo prueba el dominio: también comprueba que el constructor y los métodos privados fueron tejidos por AspectJ.

Si quiero validar específicamente que el aspecto fue compilado por `ajc`, puedo ejecutar:

```bash
javap -p target/classes/pe/axiz/payment/infrastructure/aop/DomainWeavingAspect.class | grep -E "aspectOf|hasAspect"
```

Deben aparecer ambos métodos.

### 2. Levantar PostgreSQL

En otra terminal:

```bash
cd infraestructura
docker compose up
```

### 3. Ejecutar el servicio con AspectJ LTW

Desde la raíz:

```bash
./scripts/run-with-aspectj.sh
```

El script recompila el JAR con Maven, aplica las migraciones pendientes de Flyway al arrancar y ejecuta la aplicación con `-javaagent`.

El agente es necesario para los join points de AspectJ. Si se ejecuta sin él, Spring AOP seguirá funcionando, pero el contador de weaving del dominio no se incrementará.

Al arrancar correctamente también deberían aparecer logs de Flyway aplicando `V1__create_payment_tables.sql` antes de que Hibernate valide el esquema.

## Precarga de datos

La carpeta `datasets` no toca directamente la base de datos. La carga entra por la API para recorrer exactamente los mismos aspectos y reglas que una llamada real.

Con el servicio arriba:

```bash
./datasets/load-demo.sh
```

El script crea:

- un pago normal por `49.90 PEN`;
- un pago por `13.37 PEN` para ejercitar el retry AOP.

También se puede cambiar la URL:

```bash
BASE_URL=http://localhost:8080 ./datasets/load-demo.sh
```

## Pruebas por curl

### Test 1. Crear y autorizar un pago

```bash
curl -i -X POST http://localhost:8080/api/v1/payments \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: demo-001' \
  -d '{"merchantReference":"order-1001","amount":150.50,"currency":"PEN"}'
```

Qué debería pasar:

- se ejecuta `IdempotencyAspect`;
- se crea un `Payment`;
- AspectJ intercepta la construcción del agregado;
- el gateway autoriza el pago;
- se ejecutan métodos privados del agregado y AspectJ los cuenta;
- se persiste el pago como `AUTHORIZED`;
- `AuditAspect` registra el resultado;
- `PerformanceAspect` registra el tiempo.

Respuesta esperada:

```json
{
  "id": "<uuid>",
  "merchantReference": "order-1001",
  "amount": 150.50,
  "currency": "PEN",
  "status": "AUTHORIZED",
  "gatewayReference": "gw_<uuid>",
  "createdAt": "<timestamp>",
  "updatedAt": "<timestamp>"
}
```

Guarda el `id` para las siguientes pruebas.

### Test 2. Repetir la misma solicitud

Ejecutar exactamente el mismo curl anterior usando nuevamente:

```text
Idempotency-Key: demo-001
```

Qué se prueba:

- el caso de uso no debe volver a procesar el pago;
- `IdempotencyAspect` devuelve la respuesta persistida;
- no se genera un segundo pago.

Si se repite la llave con un monto u otro dato diferente, la API responde `409 Conflict`.

### Test 3. Forzar retry del gateway

```bash
curl -i -X POST http://localhost:8080/api/v1/payments \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: retry-001' \
  -d '{"merchantReference":"order-retry","amount":13.37,"currency":"PEN"}'
```

El adapter falla en los intentos 1 y 2 y responde en el 3. La llamada final debe terminar en `AUTHORIZED` sin que 
el caso de uso conozca la lógica de retry.

### Test 4. Consultar el pago

```bash
curl -s http://localhost:8080/api/v1/payments/<PAYMENT_ID>
```

Sirve para validar la lectura desde el adapter JPA a través del puerto hexagonal.

### Test 5. Capturar el pago

```bash
curl -i -X POST http://localhost:8080/api/v1/payments/<PAYMENT_ID>/capture
```

El estado debe cambiar de `AUTHORIZED` a `CAPTURED`.

### Test 6. Repetir capture para provocar una transición inválida

```bash
curl -i -X POST http://localhost:8080/api/v1/payments/<PAYMENT_ID>/capture
```

El dominio debe rechazar la transición y responder `400`. `AuditAspect` debe registrar la operación como fallida.

### Test 7. Refund

```bash
curl -i -X POST http://localhost:8080/api/v1/payments/<PAYMENT_ID>/refund
```

El pago debe quedar `REFUNDED`.

### Test 8. Comprobar AspectJ weaving

```bash
curl -s http://localhost:8080/api/v1/aop/diagnostics
```

Ejemplo:

```json
{
  "domainConstructorsWoven": 4,
  "privateDomainMethodsWoven": 7
}
```

Los números dependen de cuántas operaciones se hayan ejecutado, pero ambos deberían ser mayores a cero.

### Test 9. Consultar métricas del aspecto

```bash
curl -s http://localhost:8080/actuator/metrics/payment.aop.operation
```

Ahí se puede ver cuántas operaciones fueron medidas y su tiempo acumulado.

## Archivo de requests para el IDE

También dejé:

```text
infraestructura/requests.http
```
