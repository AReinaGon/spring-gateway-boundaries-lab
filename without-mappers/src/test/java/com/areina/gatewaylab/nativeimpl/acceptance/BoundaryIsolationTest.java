package com.areina.gatewaylab.nativeimpl.acceptance;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Reuse real production policies in a second JVM with no external classpath. */
class BoundaryIsolationTest {
    @TempDir Path temporary;

    @ParameterizedTest(name = "independent host, allow DELETE = {0}")
    @ValueSource(booleans = {false, true})
    void reusesPoliciesWithoutSpring(boolean allowDelete) throws Exception {
        Path production = Path.of("src/main/java/com/areina/gatewaylab/nativeimpl").toAbsolutePath();
        Path rules = production.resolve("infrastructure/Rules.java");
        String original = Files.readString(rules);
        if (allowDelete) {
            String anchor = "        if (method.equals(\"POST\") && role.equals(\"writer\")) return \"write\";";
            assertEquals(1, original.split(java.util.regex.Pattern.quote(anchor), -1).length - 1);
            rules = temporary.resolve("sources/Rules.java");
            Files.createDirectories(rules.getParent());
            Files.writeString(rules, original.replace(anchor, anchor
                    + "\n        if (method.equals(\"DELETE\") && role.equals(\"writer\")) return \"delete\";"));
        }

        Path emptyClasspath = Files.createDirectory(temporary.resolve("empty-classpath"));
        Path classes = Files.createDirectory(temporary.resolve("classes"));
        var sources = new ArrayList<Path>(List.of(rules));
        String host = "NativePolicyProbe";
        try (var input = getClass().getResourceAsStream("/" + host + ".java")) {
            assertNotNull(input);
            Path hostSource = temporary.resolve(host + ".java");
            Files.copy(input, hostSource);
            sources.add(hostSource);
        }
        var arguments = new ArrayList<>(List.of("--release", "21", "-classpath", emptyClasspath.toString(), "-d", classes.toString()));
        sources.forEach(source -> arguments.add(source.toString()));
        var diagnostics = new ByteArrayOutputStream();
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "Run the suite with a JDK");
        assertEquals(0, compiler.run(null, diagnostics, diagnostics, arguments.toArray(String[]::new)), diagnostics.toString(StandardCharsets.UTF_8));

        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        var command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", executable).toString(), "-classpath", classes.toString()));
        if (allowDelete) command.add("-Dlab.allowDelete=true");
        command.add(host);
        Path outputFile = temporary.resolve("host-output.txt");
        var process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(outputFile.toFile()).start();
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        if (!finished) process.destroyForcibly();
        assertTrue(finished, "The independent host timed out");
        String output = Files.readString(outputFile);
        assertEquals(0, process.exitValue(), output);
        var lines = output.lines().toList();
        int[] statuses = {200, 400, 401, 403, 413, allowDelete ? 200 : 403};
        assertEquals(statuses.length, lines.size(), output);
        for (int index = 0; index < statuses.length; index++)
            assertTrue(lines.get(index).startsWith(index + ":" + statuses[index] + ":"), output);
        assertEquals(original, Files.readString(production.resolve("infrastructure/Rules.java")), "Production source must stay unchanged");
    }
}
