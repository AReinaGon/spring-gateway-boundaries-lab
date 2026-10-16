# Spring Gateway Boundaries Lab

A small experimental lab comparing where to place an architecture boundary. Two independent Spring Cloud Gateway Server WebFlux applications implement the same fictional filter chain: limit body size, normalize workspace metadata, identify a user and authorize the request.

Both keep primitive policies independent from Spring. The difference is how they transform request metadata:

- [without-mappers](without-mappers/README.md): transforms Spring requests directly.
- [with-mappers](with-mappers/README.md): maps metadata to owned snapshots and applies explicit header updates.

Each project has its own source, tests and Docker configuration. The whole `ServerWebExchange` stays in Spring adapters in both. An optional [JMH benchmark](benchmark/README.md) compares their processing paths.

This is a simple laboratory for exploring tradeoffs, not a production template or a complete hexagonal architecture. Neither implementation is intended as a universal winner.

## Try it

Requirements: JDK 21+, Maven and Docker with Linux containers.

Run the tests, including HTTP integration tests with Testcontainers and isolation checks:

```shell
mvn -B clean verify
```

Start both applications:

```shell
docker compose up --build --detach
```

They listen on ports **18081** (without mappers) and **18082** (with mappers). Try either port:

```shell
curl -H "X-Workspace: alpha" -H "Authorization: Bearer demo-alpha-reader" http://localhost:18081/inspect/catalog
```

The tokens are fictional test credentials. See each project's README for filter behavior and standalone commands.

```shell
docker compose down
```

## Article

[Hexagonal y Clean: ¿qué ganamos con otra capa?](https://sobreviviendoaprod.substack.com/p/hexagonal-y-clean-que-ganamos-con)

The current Java echo fixture supports functional tests; the article's HTTP measurements used the earlier fixture and have not been rerun with this one.

[MIT license](LICENSE).
