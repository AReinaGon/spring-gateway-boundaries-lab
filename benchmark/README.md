# Gateway chain benchmark

An optional Java JMH harness compiles production source from both independent applications. Neither application depends on this module.

## Build and list benchmarks

From the repository root:

```shell
mvn -B -f benchmark/pom.xml -DskipTests clean package
java -jar benchmark/target/gateway-chain-benchmark.jar -l
```

To run the configured fixtures and collect allocation data locally:

```shell
java -jar benchmark/target/gateway-chain-benchmark.jar -prof gc -f 3
```

Use explicit output paths under the ignored results/ directory if saving a run. Generated measurements and analysis reports are not part of the repository.

## Measured operation

The harness crosses two body sizes (64 and 1,024 bytes), three additional-header counts (8, 32 and 96), and the two implementations. Setup constructs the actual GatewayFilterFactories outside the measured operation. An operation builds a simulated request, executes all four filters, checks the outgoing metadata and consumes the body.

This measures the complete in-process operation, including framework request construction. It does not isolate a mapper's exclusive cost or measure YAML loading, startup, routing, networking, HTTP capacity or container resident memory. GC allocation per operation and elapsed time are different properties.

Record the exact source revision, JDK, JVM arguments, hardware, warmup, repetitions and concurrent host activity when interpreting a run. A short smoke run only checks that the harness executes; it cannot support a numerical conclusion.
