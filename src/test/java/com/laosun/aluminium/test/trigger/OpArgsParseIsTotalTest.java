package com.laosun.aluminium.test.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Every op's {@code parse} must accept an effect that states <b>nothing</b>.
 *
 * <p><b>Why this is a guard and not a convention.</b> {@code parse} runs <b>before</b> validation, so it sees whatever
 * the file wrote - including the half-written row the validator exists to refuse. A parse that throws there reports the
 * wrong thing: the author gets an engine crash instead of "you did not give a duration". Twice now the cause was the
 * same Java trap:
 *
 * <pre>{@code permanent ? UNBOUNDED_DURATION_PLACEHOLDER : effect.getTurns()}</pre>
 *
 * <p>a {@code ?:} mixing an {@code int} constant with a boxed {@code Integer} field is evaluated as {@code int} and
 * <b>unboxes the null</b>, so a rule that states no duration dies with an NPE before its own validator can refuse it.
 * The first occurrence was {@code RESIST_DEBUFF} (1207 驭空's charge shield), the second {@code APPLY_BUFF}; both were
 * found by a test that happened to cover the shape, and neither by the compiler. This case covers the shape directly:
 * an empty effect is exactly "the author stated nothing", and {@code parse} must survive it so validation can speak.
 *
 * <p><b>What it does not check.</b> Whether the parsed record is <i>correct</i> - that is each op's own judge. This is
 * only "parse is total", the weakest property a pure function owes its caller.
 */
public class OpArgsParseIsTotalTest {

    private static final Path HANDLERS =
            Path.of("src", "main", "java", "com", "laosun", "aluminium", "trigger");
    private static final String PACKAGE = "com.laosun.aluminium.trigger.";

    @Test
    public void everyParseSurvivesAnEffectThatStatesNothing() throws IOException {
        List<String> failures = new ArrayList<>();
        for (String name : handlerClassNames()) {
            Object handler;
            Method parse;
            try {
                Class<?> type = Class.forName(PACKAGE + name);
                var constructor = type.getDeclaredConstructor();
                constructor.setAccessible(true);
                handler = constructor.newInstance();
                parse = type.getMethod("parse", EffectSpec.class);
                parse.setAccessible(true);
            } catch (ReflectiveOperationException | RuntimeException setup) {
                failures.add(name + ": could not be exercised (" + setup + ")");
                continue;
            }
            EffectSpec empty = new EffectSpec();
            try {
                parse.invoke(handler, empty);
            } catch (InvocationTargetException wrapped) {
                Throwable cause = wrapped.getCause();
                failures.add(name + " -> " + cause.getClass().getSimpleName() + ": " + cause.getMessage());
            } catch (ReflectiveOperationException | RuntimeException other) {
                failures.add(name + ": could not be invoked (" + other + ")");
            }
        }
        Assertions.assertTrue(failures.isEmpty(),
                "these ops' parse throws on an effect that states nothing, so a half-written rule would crash instead"
                        + " of being refused by its own validator: " + failures);
    }

    @Test
    public void theGuardActuallyFoundTheHandlers() throws IOException {
        // A scan that silently matched nothing would make the case above vacuously green.
        Assertions.assertFalse(handlerClassNames().isEmpty(), "no *Op.java handler was found to exercise");
    }

    /**
     * @return the simple names of every {@code *Op.java} in the trigger package
     * @throws IOException when the sources cannot be read
     */
    private static List<String> handlerClassNames() throws IOException {
        List<String> names = new ArrayList<>();
        try (Stream<Path> files = Files.walk(HANDLERS)) {
            for (Path file : files.filter(each -> each.getFileName().toString().endsWith("Op.java")).toList()) {
                String simple = file.getFileName().toString().replace(".java", "");
                String source = Files.readString(file, StandardCharsets.UTF_8);
                if (source.contains("implements OpHandler<") && !simple.equals("OpHandler")) {
                    names.add(simple);
                }
            }
        }
        return names;
    }
}
