package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Queue;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

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
