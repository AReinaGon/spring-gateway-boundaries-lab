# Gateway with request mappers

A self-contained Spring Cloud Gateway Server WebFlux application. It owns its production code, rules, tests, fixture server, Maven configuration and Docker configuration. It does not read source files or configuration from its sibling or the repository root.

This example extends the architecture boundary to metadata-transformation processors. The filters translate current request metadata to immutable snapshots, run processors independent of Spring and apply header updates to the current exchange. A second host can reuse the processors but still owns patch application. The contract is independent of Spring types and still expresses HTTP concepts; reactive body handling and filter execution remain tied to the integration.

The root laboratory measures the selected representation's runtime costs and demonstrates its additional reuse property. Whether those properties justify the contract depends on your system. This is an experiment about pragmatism in choosing boundaries, not a claim that adding request DTOs creates a complete hexagonal architecture.

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

The gateway listens on localhost:18082; override `GATEWAY_PORT` if needed. Try GET `/inspect/catalog` with `X-Workspace: alpha` and `Authorization: Bearer demo-alpha-reader`. The echo destination returns what it receives. Stop this project's services with `docker compose down`.

The four filters limit the body to 1,024 bytes, normalize a single workspace identifier, recognize fixture tokens and authorize the operation using preceding transformations. Tokens and permissions are fictional. Both projects retain the same acceptance scenarios, duplicated locally for independent execution.
