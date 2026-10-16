# Spring Gateway Boundaries Lab

Two independent Spring Cloud Gateway Server WebFlux applications implement the same fictional filter chain with different boundary placements. Both keep primitive policies independent from Spring. One transforms Spring requests directly; the other also isolates transformation processors behind immutable metadata and explicit header updates.

The examples support a contextual architecture decision: which behavior needs independence, and what does an additional contract enable? Neither project represents an entire hexagonal architecture or establishes a universal winner.

## Projects

- [without-mappers](without-mappers/README.md): direct Spring request transformations around independent primitive rules.
- [with-mappers](with-mappers/README.md): owned request snapshots and header-update processors around its own copy of the rules.
- [benchmark](benchmark/README.md): an optional Java JMH harness using production source from both projects.

Each application owns its POM, source, tests, Java echo fixture, Dockerfile and Compose configuration. Either directory can be copied and built independently. The root POM only aggregates the builds; the applications have no shared parent or source module.

## Filter chain

Each project's application.yml declares the route and these ordered filters, with one GatewayFilterFactory per filter:

1. **BodyLimit:** buffer at most 1,024 bytes, reject oversized bodies with 413 and replay the original bytes.
2. **NormalizeWorkspace:** validate one workspace identifier, normalize it and remove caller-supplied internal headers.
3. **TokenIdentity:** recognize fixture tokens and populate identity metadata; reject missing, duplicate or invalid tokens with 401.
4. **AuthorizeOperation:** check the normalized workspace, role and method; reject forbidden requests with 403 and remove sensitive/internal headers before forwarding.

Valid requests preserve method, URI/query, unrelated repeated headers and binary body. Rejected requests never reach the echo destination.

The whole ServerWebExchange stays in Spring adapters in both projects. Primitive policies receive values. In the mapped project, RequestMapper reads a RequestSnapshot, MappedRules returns a HeaderUpdate, and the adapter applies it to the current exchange. The extra boundary isolates transformations as well as policies; it still describes HTTP metadata.

Fixture tokens are Bearer demo-alpha-reader, Bearer demo-alpha-writer and Bearer demo-beta-reader. They are fictional opaque strings, without JWT verification or production credential management.

## Verify

Requirements: JDK 21+, Maven and Docker with Linux containers.

```shell
mvn -B clean verify
```

The suite covers in-process composition, real HTTP requests through Testcontainers, request preservation, rejected requests and mapping contracts. The echo destination is built from each project's own Java test resource. Missing Docker fails integration tests.

BoundaryIsolationTest compiles the production rules and, where applicable, metadata processors with an empty external classpath. A second JVM executes six contract cases, then repeats them with a generated policy change allowing DELETE for writers. Only a temporary source copy changes; the adapters and checked-in source stay unchanged. This checks specific reuse and change containment without Spring, not complete framework migration.

Run the same command from either application directory to verify it independently. To explicitly skip HTTP integrations:

```shell
mvn -B -DskipITs=true clean verify
```

GitHub Actions runs the functional and isolation suites on Java 21 with Docker and builds the optional benchmark.

## Run

```shell
docker compose up --build --detach
```

The direct project listens on localhost:18081 and the mapped project on localhost:18082. Override NATIVE_PORT and MAPPED_PORT if needed. The Java echo server is internal.

```shell
curl -H "X-Workspace: alpha" -H "Authorization: Bearer demo-alpha-reader" http://localhost:18081/inspect/catalog
curl -H "X-Workspace: alpha" -H "Authorization: Bearer demo-alpha-reader" http://localhost:18082/inspect/catalog
```

Stop these services without affecting other Compose projects:

```shell
docker compose down
```

Both application directories also provide their own independent Compose configuration.

## Scope

This repository contains application, test and benchmark source, build/runtime configuration and usage documentation. Measurement outputs, analysis scripts and editorial evidence stay outside the public source tree.

Both examples intentionally buffer a small body. They do not demonstrate large-body streaming, external authentication, persistence transactions or a complete replacement of the gateway runtime. The synchronous isolation host does not execute reactive body handling.

The [article](https://sobreviviendoaprod.substack.com/p/hexagonal-y-clean-que-ganamos-con) discusses measurements from its recorded experimental setup. The Java echo fixture introduced here replaces that setup's earlier destination for functional verification; it is not a rerun of the published HTTP measurements.

## License

[MIT](LICENSE).
