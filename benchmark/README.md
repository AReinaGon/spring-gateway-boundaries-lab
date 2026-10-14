# Architecture-boundary experiments

The question is pragmatic: which behavior needs independence from a framework, what does that independence enable, and what runtime cost does the selected representation incur? A four-filter gateway is the experimental subject. Both applications already isolate primitive policies; the additional abstraction isolates metadata-transformation processors too. This is not a comparison of a complete hexagonal system with a system that has no separation.

## Factorial JMH

```shell
mvn -B -f benchmark/pom.xml -DskipTests clean package
python tools/run_jmh.py
```

The harness compiles exact production source from both self-contained applications. Neither application depends on it. Body sizes (64 and 1,024 bytes) and extra header counts (8, 32 and 96) are crossed independently: six fixtures for each variant, twelve measurements total. The setup constructs each real GatewayFilter through its own GatewayFilterFactory, outside the measured operation. Each operation builds a simulated request, executes all four filters, validates outgoing metadata and consumes the exact body. YAML loading, factory construction and application startup are not timed by JMH; the HTTP phase uses the actual YAML configuration. No network, routing handler or Netty server is measured.

The runner uses Java 21 in a bounded Docker container, one CPU quota, 768 MiB limit, one benchmark thread, three JVM forks, ten two-second warmup iterations and five two-second measurement iterations. Forks use fixed 256 MiB heaps and one active processor. Lab tracing/counters are disabled equally. GC profiling records allocation bytes per complete operation, not resident memory or the exclusive cost of a mapper. Reported score margins are JMH's 99.9% mean intervals.

Outputs: ignored results/generated/jmh-factorial/jmh.json, jmh.log and jmh-command.json. Smoke mode measures only one fixture briefly and validates conformance; it never backs numerical conclusions. Inspect raw measured iterations and forks for instability.

## HTTP pressure and shared-limit control

Run this phase after JMH, without overlapping the loads.

```shell
docker compose -f compose.yaml -f compose.perf.yaml up --build --detach
python tools/compose_smoke.py --performance
python tools/http_pressure.py
python tools/container_resources.py
python tools/summarize_deep.py --implementation yaml-factories
docker compose -f compose.yaml -f compose.perf.yaml down
```

Both gateway images have one CPU quota, 512 MiB memory limit, maximum 256 MiB heap, two active processors, disabled instrumentation and GC pause logging. The Python echo destination has four CPU of quota and 256 MiB. Its sockets use TCP_NODELAY for gateway and direct-control traffic equally. Periodic echo healthchecks are disabled during measurement; the smoke/readiness probe verifies availability before warmup. The override exposes the control only on localhost:18080. Override UPSTREAM_PORT, NATIVE_PORT and MAPPED_PORT if needed.

The client offers 100, 300, 600 and 900 POST/s in ascending levels. Each level has three twenty-second runs per gateway and direct echo control. Gateway order is AB/BA/AB; direct control follows each pair. Warmup is sixty seconds at 300 POST/s per target. The workload is 64 bytes, eight additional headers, a valid writer token and repeated unrelated headers.

Sixty-four reusable connection workers execute requests with a three-second I/O timeout. Every scheduled arrival and every failure is retained. Scheduled latency includes client dispatch/backlog; worker-start service latency and dispatch delay are recorded separately. Percentiles of all attempts, valid-only percentiles, valid throughput, and the proportion meeting the objective are distinct metrics. Slow or invalid requests are never removed from SLO accounting.

The illustrative objective is predeclared: at least 99% of arrivals must receive a valid response within 20 ms of their scheduled arrival. It is a lens for interpreting this workload, not a universal production requirement. If the generator or echo limits a run, do not attribute that ceiling to the gateway or an abstraction.

Direct echo traffic uses the outgoing metadata shape and the same body. It diagnoses the shared destination/generator path with one fewer hop. It is not an identical route and its latency cannot be subtracted from gateway latency.

CPU comes from cgroup usage deltas and includes the container processes, JIT, GC and small collection-command overhead. Throttling is a CPU-quota diagnostic. GC counts/durations are pause events from whole-process logs. RSS is observed only at run boundaries, not continuously; it is not peak memory. The client process CPU fraction and dispatch delay help diagnose generator limits. Runtime versions, image identities and quotas are recorded.

The runtime-version probe clears JAVA_TOOL_OPTIONS only for its short-lived diagnostic JVM, so it cannot rotate the application's GC log. The application keeps its original options. GC cumulative counts/durations before and after each run and complete final GC logs are retained for verification.

The summary reports medians and full ranges of three per-run statistics, not confidence intervals or a maximum-capacity estimate. Levels are ascending and host workload is not fully controlled; preserve all runs and assess drift. A higher allocation rate does not imply the same percentage of HTTP latency increase.

## Executable reuse and policy change

```shell
python tools/compile_cores.py
python tools/probe_boundaries.py --docker-java21
```

The probe compiles original policies using an explicit empty external classpath, with Java 21 target bytecode, and runs the generated classes on Java 21 in Docker. It executes six metadata cases per variant, then repeats them with one generated Rules change permitting DELETE for writers. Production source and the host adapters remain unchanged. Both variants contain that policy change; the native host reuses rules, while the mapped host reuses rules and transformation processors.

The host is synchronous and metadata-only. It is not a new HTTP server, a substitute for reactive body handling, or evidence for complete framework migration. It proves a specific reuse/change property. It does not measure development hours or an overall level of isolation.

## Evidence and provenance

Current YAML/factory run: [complete summary](../results/performance-yaml-factories-2026-10-14.json), with matching JMH, HTTP, memory, functional and reuse snapshots and a source/evidence hash manifest. All measured phases completed on 2026-10-14. The raw compressed sample file retains 342,000 arrivals, including the direct control, with no selection by latency or outcome.

The public snapshot contains this source and these matching measurements. Previous Java-DSL sources, calibration runs and host-interrupted runs are preserved in the local editorial archive, outside the public repository. Their statistics are not mixed with this series. The summarizer rejects incomplete experiments and runs whose duration exceeds ten times the offered interval, so a host interruption cannot silently enter the final summary.

CPU quotas, memory limits, image identities, runtime versions, the benchmark JAR hash, source hashes and compressed-data hashes are recorded for audit. An image tag or a Java major version alone is not enough to infer the exact runtime used for a historical measurement.

These experiments do not measure long-term maintenance, defects, economic return, production security, arbitrary workloads or every possible mapper design. Reuse is a property that may or may not be needed by a real system; cost alone does not determine whether an abstraction pays off.

Sources: [OpenJDK JMH](https://github.com/openjdk/jmh), [forking sample](https://github.com/openjdk/jmh/blob/master/jmh-samples/src/main/java/org/openjdk/jmh/samples/JMHSample_12_Forking.java), [profilers sample](https://github.com/openjdk/jmh/blob/master/jmh-samples/src/main/java/org/openjdk/jmh/samples/JMHSample_35_Profilers.java), [Cockburn's original hexagonal architecture paper](https://alistair.cockburn.us/hexagonal-architecture), [Clean Architecture](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html).
