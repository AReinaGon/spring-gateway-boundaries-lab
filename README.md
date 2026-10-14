# Pragmatism in architecture: choosing coupling deliberately

Which dependency are we willing to accept, what behavior needs protection, and does a wider boundary justify its cost? This lab investigates a pragmatic architecture decision relevant to hexagonal and Clean principles: keep policies independent while using framework types inside an integration, or additionally isolate transformation processors behind owned metadata contracts. A specialized HTTP component is the experimental subject. Neither option represents an entire hexagonal system, and this is not a performance contest between architecture styles.

Two independently runnable applications implement the same fictional four-filter chain. One uses native Spring request types inside the gateway integration; the other reads immutable request-metadata snapshots and applies explicit header updates. Both isolate primitive rules. Runtime costs, reuse in a second host, and a controlled policy change provide evidence for a contextual decision. The chain and credentials are fictional.

Both applications use **Spring Cloud Gateway Server WebFlux**, through `spring-cloud-starter-gateway-server-webflux`, on Spring Boot 3.5.16 and the Spring Cloud 2025.0.3 BOM. They run on Reactor Netty and use `GatewayFilter`, `ServerWebExchange` and reactive `Mono` continuations. This lab does not implement the Server Web MVC variant. Its behavioral and performance observations apply to these WebFlux implementations and the documented workloads.

## Chain and contract

1. **Size:** read at most 1,024 body bytes, reject excess with 413, then replay the original bytes. The body is bounded and buffered once; the limit also applies to chunked input without Content-Length.
2. **Normalize:** require exactly one `X-Workspace`, strip surrounding whitespace and lowercase its ASCII identifier. Reject invalid or duplicate identifiers with 400. Remove caller-supplied `X-Lab-*` headers. This is identifier validation, not general HTML/XSS sanitization.
3. **Token:** recognize a fixture token and write the subject, role and token workspace into internal request headers. Missing, unknown or duplicate tokens produce 401.
4. **Authorize:** consume the normalized workspace and identity from preceding filters. Readers and writers may GET; only writers may POST; the token workspace must match. Reject other operations/scopes with 403. Write the action and remove the token and internal role/scope before forwarding.

The terminal HTTP destination echoes what it actually receives. A valid request retains its method, URI/query, unrelated repeated headers and exact binary body. Rejected requests never reach that destination. Trace and mapper-invocation response headers expose this lab's execution, not production telemetry.

Fixture credentials: `Bearer demo-alpha-reader`, `Bearer demo-alpha-writer`, `Bearer demo-beta-reader`. These are public, hardcoded opaque fixtures. They provide no JWT verification, issuer validation, token expiry, revocation, external authentication or production credential management.

## Structure

- [`without-mappers/`](without-mappers/README.md): a self-contained application using the current Spring request in its filters, with its own primitive rules and infrastructure.
- [`with-mappers/`](with-mappers/README.md): a self-contained application using immutable request-metadata snapshots, independent transformation processors and header updates, with its own rules and infrastructure.
- `benchmark/`: an external comparison harness that compiles production source from both projects. Neither application depends on it.
- `tools/`: external comparison and evidence scripts. Neither application needs them to build, test or run.

Each application owns its POM (direct Spring Boot parent), production source, test source, test fixture, Dockerfile and Compose configuration. You can copy either project directory elsewhere and build it alone. The root POM only offers the convenience of running both builds; it is not their parent. There is no shared source directory or shared build-time application module.

Infrastructure, rules and acceptance contracts are intentionally duplicated to make each example readable independently. `tools/check_equivalence.py` audits their equivalence without linking the builds. This is an explanatory choice for a laboratory, not a prescription for maintaining duplicated production code.

The mapped version applies patches to the current exchange, retaining context, unrelated data and the reactive body. The native version retains primitive rules without Spring. Both use the same bounded-body algorithm in their own local copy.

## YAML composition and the boundary

Both applications declare the same route and ordered filter names in their own `src/main/resources/application.yml`:

```yaml
spring:
  cloud:
    gateway:
      server:
        webflux:
          routes:
            - id: fictional-chain
              uri: ${lab.upstream-url:http://localhost:18080}
              predicates:
                - Path=/inspect/**
              filters:
                - BodyLimit
                - NormalizeWorkspace
                - TokenIdentity
                - AuthorizeOperation
```

Each filter has its own `GatewayFilterFactory` component under the application's `filters` package. Spring composes those route filters in the listed order. There is no grouped `NativeFilters`/`MappedFilters` class and no Java `RouteLocatorBuilder` configuration.

The complete `ServerWebExchange` stays inside Spring adapters in both designs. The direct factories extract values, call `Rules`, and transform the current request using Spring APIs. The mapped factories call `RequestMapper.read`, run `MappedRules` with a `RequestSnapshot`, and apply the resulting `HeaderUpdate` to the current exchange. Primitive policies and mapped processors do not receive a Spring exchange. This comparison adds an independent transformation contract; it does not compare pure business rules with business rules that accept the whole Spring runtime context.

## Verification

GitHub Actions runs the functional suites and checks duplicated contracts and type independence with Java 21 and Docker. Long-running performance experiments are explicit local commands, not CI capacity checks.

Requirements: JDK 21+, Maven and Docker with Linux containers.

```shell
mvn -B clean verify
python tools/check_equivalence.py
python tools/collect_metrics.py
python tools/compile_cores.py
python tools/probe_boundaries.py --docker-java21
```

For a standalone build, run `mvn -B clean verify` from either project directory. Each project locates the echo fixture on its own test classpath. Missing Docker fails the integration suite. To explicitly run only tests without Docker:

```shell
mvn -B -DskipITs=true clean verify
```

The acceptance scenarios cover normalization feeding authorization, two workspaces, UTF-8/binary body preservation, exact/oversized body limits with and without Content-Length, ambiguous headers, forged internal identity/role, invalid tokens, forbidden writes/scopes and unsupported methods. Additional mapping tests verify immutable snapshots, case-insensitive lookups and applying a patch without losing URI, unrelated headers or exchange attributes.

`compile_cores.py` uses an explicit empty external classpath: it compiles each project's own rules and separately the mapped snapshots/processors. This proves type independence for those classes; it does not prove independence from HTTP semantics or the gateway runtime.

`probe_boundaries.py` additionally executes six identical metadata cases in a synchronous Java host without Spring. The native host reuses rules and owns its header orchestration. The mapped host reuses rules and processors, and owns patch application. It repeats the cases with one authorization-policy change in generated source copies, permitting DELETE for writers. Both variants contain this change in Rules, with unchanged adapters and host code. All 24 executions are compared. This demonstrates specific reuse and change containment, not a replacement HTTP runtime, full framework migration or an isolation score. Production source is never modified by the probe.

## Docker Compose

```shell
docker compose up --build --detach
python tools/compose_smoke.py
docker compose down
```

The root Compose is a convenience for comparison. Each project also has its own independent Compose (see its README). Root Compose publishes the native gateway on localhost:18081 and the mapped gateway on localhost:18082. Override `NATIVE_PORT` and `MAPPED_PORT` if occupied. The echo server is internal. Each gateway image is built and tested on Java 21. Full Testcontainers integrations run from host Maven, not inside nested Docker.

The smoke command compares a real transformed POST, URI, repeated headers and body, plus 401/403/413 rejection examples for both deployed images. It writes its evidence to ignored `results/generated/compose-smoke.json`.

## YAML/factory measurements — 2026-10-14

Current source: YAML route composition and one GatewayFilterFactory per filter. The boundary remains unchanged: values into Rules in both variants; RequestSnapshot and HeaderUpdate additionally isolate transformation processors in the mapped variant. No business policy accepts ServerWebExchange.

All 88 functional tests passed, including 36 real HTTP/Testcontainers executions. The 24 Java 21 reuse/policy-change probes passed without Spring. Both independently built Docker images passed the deployed transformation and rejection smoke. JMH constructs the filters through their factories during setup, outside the timed operation; HTTP starts from the actual YAML routes. Full protocol and limits: [benchmark/README.md](benchmark/README.md).

| Body B | Extra headers | Variant | Mean µs ± JMH margin | KiB/op |
|---|---|---|---|---|
| 64 | 8 | mapped | 28.13 ± 1.50 | 32.75 |
| 64 | 8 | native | 15.11 ± 0.52 | 25.74 |
| 64 | 32 | mapped | 65.61 ± 1.44 | 70.86 |
| 64 | 32 | native | 28.30 ± 1.49 | 53.18 |
| 64 | 96 | mapped | 202.27 ± 4.74 | 177.35 |
| 64 | 96 | native | 66.46 ± 1.38 | 131.53 |
| 1024 | 8 | mapped | 27.81 ± 0.81 | 38.67 |
| 1024 | 8 | native | 15.47 ± 0.28 | 31.58 |
| 1024 | 32 | mapped | 69.23 ± 4.29 | 76.81 |
| 1024 | 32 | native | 29.28 ± 0.77 | 59.68 |
| 1024 | 96 | mapped | 200.63 ± 4.20 | 183.14 |
| 1024 | 96 | native | 72.91 ± 4.35 | 138.77 |

JMH reports complete simulated operations including request construction, four filters, validation and body consumption, with three JVM forks and the same factorial inputs. Allocation is bytes per operation, not retained RAM. The score margin is JMH's reported 99.9% mean interval; it is not a measure of an entire architecture style.

| Offered/s | Variant | p95 median (range), ms | Valid ≤20 ms, median (range) | CPU ms/attempt | Errors |
|---|---|---|---|---|---|
| 100 | native | 4.84 (4.81–4.98) | 100.00% (100.00–100.00) | 1.34 | 0 |
| 100 | mapped | 4.90 (4.70–5.03) | 100.00% (100.00–100.00) | 1.39 | 0 |
| 100 | upstream | 3.71 (3.70–3.76) | 100.00% (100.00–100.00) | 0.89 | 0 |
| 300 | native | 4.30 (4.29–4.43) | 100.00% (100.00–100.00) | 1.03 | 0 |
| 300 | mapped | 4.26 (4.23–4.42) | 100.00% (100.00–100.00) | 1.03 | 0 |
| 300 | upstream | 3.03 (3.01–3.03) | 100.00% (100.00–100.00) | 0.74 | 0 |
| 600 | native | 4.42 (4.41–4.58) | 99.97% (99.95–100.00) | 1.01 | 0 |
| 600 | mapped | 4.44 (4.43–4.54) | 99.88% (99.77–99.92) | 1.02 | 0 |
| 600 | upstream | 3.03 (2.99–3.22) | 99.97% (99.92–99.98) | 0.66 | 0 |
| 900 | native | 39.46 (28.97–89.26) | 86.76% (75.01–92.83) | 1.01 | 0 |
| 900 | mapped | 37.86 (33.43–46.05) | 91.09% (90.25–92.00) | 1.02 | 0 |
| 900 | upstream | 4.75 (4.39–4.83) | 98.73% (98.50–98.83) | 0.71 | 0 |

Medians and full ranges of three runs per target/load. The illustrative objective is at least 99% valid responses within 20 ms from scheduled arrival. Evaluate every run, the direct control and client-dispatch delay before attributing a bottleneck. A valid response can still miss its deadline; an isolated median cannot establish maximum capacity.

| Offered/s | Variant | CPU ms/attempt | GC pauses/run | GC pause ms/run | RSS after MiB |
|---|---|---|---|---|---|
| 100 | native | 1.34 | 5 | 7.43 | 248.4 |
| 100 | mapped | 1.39 | 6 | 8.37 | 257.7 |
| 300 | native | 1.03 | 16 | 20.81 | 248.4 |
| 300 | mapped | 1.03 | 19 | 24.05 | 257.7 |
| 600 | native | 1.01 | 32 | 41.77 | 248.6 |
| 600 | mapped | 1.02 | 37 | 45.73 | 258.0 |
| 900 | native | 1.01 | 48 | 81.32 | 248.8 |
| 900 | mapped | 1.02 | 56 | 85.30 | 258.1 |

CPU is the complete container's measured usage delta per offered request. GC counts and pause times describe the Java process over each run; RSS is observed at run endpoints. Neither metric isolates a mapper's cost.

| Variant | Container final MiB | Lifetime peak MiB |
|---|---|---|
| native | 267.7 | 272.5 |
| mapped | 256.7 | 259.3 |
| upstream | 23.1 | 29.4 |

The final cgroup reading and lifetime peak are separate from RSS, heap and allocation. Lifetime peak includes startup, warmup and diagnostic commands, not a particular load tier. Three repetitions on one host are observations, not proof of a universal CPU or RAM winner.

Evidence: [complete summary](results/performance-yaml-factories-2026-10-14.json), [JMH](results/jmh-yaml-factories-2026-10-14.json), [all HTTP runs](results/http-pressure-yaml-factories-2026-10-14.json), [342,000 raw samples](results/http-pressure-samples-yaml-factories-2026-10-14.jsonl.gz), [memory](results/container-resources-yaml-factories-2026-10-14.json), [functional counts](results/observed-yaml-factories-2026-10-14.json), [reuse probes](results/boundary-probes-yaml-factories-2026-10-14.json), [hash manifest](results/evidence-manifest-yaml-factories-2026-10-14.json).

This repository publishes the current YAML/factory source and matching results. Earlier Java-DSL sources and measurements are retained separately in the local editorial archive and are excluded from this public snapshot. The runs do not establish that YAML itself changes per-request performance.

## Interpreting the comparison

Both implementations separate responsibilities. The native design keeps primitive rules independent of Spring while request transformations use framework APIs. The mapped design additionally keeps transformation processors independent of Spring through its own metadata and update contracts. The adapters and reactive execution still depend on the gateway in both.

Use the observations to assess your circumstances:

- Which behavior needs to be developed, tested or run without Spring?
- Does another consumer actually need the same transformation contract?
- Which request transformations must the contract support, and how often might it change?
- Are the measured costs relevant to the workload, resources and service targets of your system?

A smaller translation surface, an independent processor and a lower measured cost are different properties. Their value depends on the required boundary. Reuse of metadata rules/processors and containment of one policy change are now executed in a second Java host. This does not demonstrate complete framework migration, maximum gateway capacity or long-term maintenance; the lab does not assign an overall architecture score.

## Observations and limits

Current YAML/factory verification: 88 passing executions, with 24 in-process + 18 HTTP cases without mappers and 28 + 18 with mappers. All passed without failures, errors or skips. [Counts and source metrics](results/observed-yaml-factories-2026-10-14.json). Previous snapshots are preserved in the local editorial archive.

For a request accepted through all four filters, the mapped design invokes four snapshot reads and four patch applications; the native design invokes neither lab mapper. Empty patches do not rebuild the exchange. This is an invocation count, not an allocation, CPU or latency measurement. Spring itself still creates/decorates objects in both variants.

Own types allow the mapped processors to be tested with snapshots and compiled without Spring. Small primitive rules in the native variant are independently testable too. The mapper contracts still express HTTP headers, a method and URI: they do not make the pipeline technology-neutral by themselves.

Current production Java counts: without mappers, 8 files/235 nonblank lines; with mappers, 12 files/297 nonblank lines. Each owns 3 equivalent infrastructure files/135 lines. YAML, tests and documentation are excluded. These counts describe this source version and its translation surface; they do not estimate maintenance hours or predict defect rates. No framework migration, maximum-throughput result, body transformation, cancellation stress test, persistence transaction or production security claim is made. Both designs intentionally buffer a small bounded body; large-body streaming would require a different contract.

Pinned for reproduction: Java target 21, Spring Boot 3.5.16, Spring Cloud BOM 2025.0.3, Testcontainers 2.0.5, Python echo image 3.12 Alpine. These are reproducibility choices, not a production-version recommendation.

Sources: [Spring Cloud Gateway developer guide](https://docs.spring.io/spring-cloud-gateway/reference/4.3/spring-cloud-gateway-server-webflux/developer-guide.html), [filter ordering](https://docs.spring.io/spring-cloud-gateway/reference/4.3/spring-cloud-gateway-server-webflux/global-filters.html), [bounded DataBuffer join](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/io/buffer/DataBufferUtils.html), [Spring Cloud compatibility](https://spring.io/projects/spring-cloud/), [Testcontainers generic containers](https://java.testcontainers.org/features/creating_container/).
