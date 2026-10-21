package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Queue;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * The engine artifact must not print: a library that writes to stdout is a library every front end
 * has to suppress.
 *
 * <p>The demo owns its narration now; {@code Battle.printHp} / {@code Battle.printBattle} and
 * {@code Queue.printActionQueue} were removed. This guard keeps a future helper from quietly bringing
 * them back.
 */
public class EngineApiSurfaceTest {

    /**
     * Neither the battle nor the action queue exposes a {@code print*} method.
     */
    @Test
    public void theEngineHasNoConsolePrinters() {
        assertNoPrintMethods(Battle.class);
        assertNoPrintMethods(Queue.class);
    }

    /**
     * Production code must enter triggers through {@code Battle.fire(TriggerPayload...)}, not the legacy
     * {@code battle.fireTriggers(...)} overloads. Only {@code Battle}'s own adapters may call them.
     *
     * @throws IOException when the source tree cannot be read
     */
    @Test
    public void productionDoesNotUseTheLegacyFireOverloads() throws IOException {
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            List<String> offenders = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> {
                        try {
                            return Files.readString(path).contains("battle.fireTriggers(");
                        } catch (IOException e) {
                            throw new java.io.UncheckedIOException(e);
                        }
                    })
                    .map(Path::toString)
                    .toList();
            Assertions.assertTrue(offenders.isEmpty(),
                    "production must use Battle.fire(TriggerPayload...): " + offenders);
        }
    }

    /**
     * @param type the class to inspect
     * @throws AssertionError when a public {@code print*} method exists
     */
    private static void assertNoPrintMethods(Class<?> type) {
        List<String> printers = Arrays.stream(type.getMethods())
                .map(Method::getName)
                .filter(name -> name.startsWith("print"))
                .toList();
        Assertions.assertTrue(printers.isEmpty(),
                type.getSimpleName() + " must not print; it still exposes " + printers);
    }
}
