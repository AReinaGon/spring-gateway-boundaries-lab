# Gateway without request mappers

A self-contained Spring Cloud Gateway Server WebFlux application. It owns its production code, rules, tests, fixture server, Maven configuration and Docker configuration. It does not read source files or configuration from its sibling or the repository root.

This example places a small architecture boundary around primitive policies. The filters transform the current Spring request directly; the policies compile and execute without Spring. A second host can reuse those policies but must provide its own header orchestration. It is a deliberate separation choice, not an absence of architecture. Its value depends on which behavior your system needs to isolate.

The root laboratory compares this choice with an application-owned metadata contract, including factorial allocation/time measurements, HTTP pressure, and executable policy-reuse probes. The experiment concerns pragmatism in choosing boundaries; it does not claim this entire application implements a full hexagonal architecture.

## YAML route and filter factories

`src/main/resources/application.yml` declares `/inspect/**`, the destination `${lab.upstream-url:http://localhost:18080}` and the ordered filters `BodyLimit`, `NormalizeWorkspace`, `TokenIdentity` and `AuthorizeOperation`. Each name resolves to its own Spring `GatewayFilterFactory` bean in the `filters` package. The application class only starts Spring Boot; there is no Java route builder or static grouped filter class.

The Spring adapter owns the exchange and reactive continuation. Primitive policies receive values, not `ServerWebExchange`. In the mapped application, transformation processors additionally receive an immutable snapshot and return header changes; applying those changes still belongs to the adapter. YAML composes the filters, while Java implements their behavior.

## Verify

Requirements: JDK 21+, Maven and Docker with Linux containers. Run from this directory, or copy this directory elsewhere first:

```shell
mvn -B clean verify
```

Testcontainers starts the echo destination using this project's test resource. A missing Docker runtime fails integration tests. To explicitly run only in-process tests, use `mvn -B -DskipITs=true clean verify`.

## Run independently

```shell
docker compose up --build --detach
```

The gateway listens on localhost:18081; override `GATEWAY_PORT` if needed. Try GET `/inspect/catalog` with `X-Workspace: alpha` and `Authorization: Bearer demo-alpha-reader`. The echo destination returns what it receives. Stop this project's services with `docker compose down`.

The four filters limit the body to 1,024 bytes, normalize a single workspace identifier, recognize fixture tokens and authorize the operation using preceding transformations. Tokens and permissions are fictional. Both projects retain the same acceptance scenarios, duplicated locally for independent execution.
