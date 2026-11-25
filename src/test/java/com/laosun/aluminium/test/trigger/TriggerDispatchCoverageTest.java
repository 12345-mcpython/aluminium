package com.laosun.aluminium.test.trigger;

import com.laosun.aluminium.trigger.OpRegistry;
import com.laosun.aluminium.trigger.TriggerOperator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Every WIRED op is performed exactly once, by its handler.
 *
 * <p><b>Why this cannot be left to the compiler.</b> The enum switch did catch a misspelled {@code case}, but it could
 * not catch an op that was <b>never written at all</b> - that one fell into the {@code default} arm and only failed when
 * somebody's rule happened to use it. An op with a handler <b>and</b> a switch arm was the mirror-image fault and a
 * worse one: both would run, or one of them silently never would, and which was which depended on a line in the
 * dispatcher rather than on the op.
 *
 * <p><b>The migration is over.</b> {@code validateOne} / {@code applyOne} carry no op switch any more, so
 * {@link #arms()} should answer empty - it still reads the source, because that is what makes the claim checkable
 * rather than assumed: if an arm is ever reintroduced beside a handler, the first case fails. The two-source shape was
 * deliberate while the registry grew one op at a time; what it asserts now is "the registry covers
 * {@code wiredNames()}", one source, with the second only ever allowed to be empty.
 */
public class TriggerDispatchCoverageTest {

    private static final Path DISPATCHER =
            Path.of("src/main/java/com/laosun/aluminium/trigger/TriggerInterpreter.java");

    @Test
    public void everyWiredOpIsDispatchedExactlyOnce() throws IOException {
        Assertions.assertTrue(arms().isEmpty(),
                "the dispatcher still has op switch arms: " + arms() + " - one op has one path, and it is the registry");
        Set<String> arms = arms();
        Set<String> handled = OpRegistry.handledNames();

        Set<String> twice = new TreeSet<>(arms);
        twice.retainAll(handled);
        Assertions.assertTrue(twice.isEmpty(),
                "these ops are performed by BOTH a switch arm and a handler: " + twice
                        + " - one op has one path; delete one of them");

        Set<String> missing = new TreeSet<>(TriggerOperator.wiredNames());
        missing.removeAll(arms);
        missing.removeAll(handled);
        Assertions.assertTrue(missing.isEmpty(),
                "these wired ops are performed by NEITHER the switch nor a handler: " + missing);
    }

    @Test
    public void everyHandlerNamesAWiredOp() {
        Set<String> wired = TriggerOperator.wiredNames();
        Set<String> handled = OpRegistry.handledNames();
        Assertions.assertTrue(wired.containsAll(handled),
                "a handler is registered for an op that is not wired: " + minus(handled, wired)
                        + "; a handler performs what the engine can do, so a planned op cannot have one");
        for (String name : handled) {
            Assertions.assertNotNull(TriggerOperator.parse(name), name + " must parse back to a constant");
        }
    }

    @Test
    public void everyWiredOpIsARealEnumConstant() {
        for (String name : TriggerOperator.wiredNames()) {
            Assertions.assertNotNull(TriggerOperator.parse(name), name + " must parse back to a constant");
        }
    }

    /**
     * The ops a legacy {@code switch (operator)} still performs, read from the dispatcher's source.
     *
     * <p>Empty is the finished state: the registry is then the only dispatcher. The method is kept rather than deleted
     * so that re-introducing a switch arm - the shape that let an op be performed twice, or performed and not
     * validated - fails this case instead of quietly working.
     *
     * <p>A grouped arm ({@code case A, B ->}) is split, so the answer is a set of ops rather than a set of arms.
     *
     * @return the op names the switch dispatches, empty when there is no switch
     * @throws IOException when the dispatcher cannot be read
     */
    private static Set<String> arms() throws IOException {
        String source = Files.readString(DISPATCHER);
        Set<String> names = new TreeSet<>();
        int from = source.indexOf("switch (operator)");
        if (from < 0) {
            return names;
        }
        Matcher matcher = Pattern.compile("case ([A-Z_, ]+) ->").matcher(source.substring(from));
        while (matcher.find()) {
            for (String name : matcher.group(1).split(",")) {
                String trimmed = name.trim();
                if (!trimmed.isEmpty()) {
                    names.add(trimmed);
                }
            }
        }
        return names;
    }

    /**
     * @param from  the set to subtract from
     * @param other the set to subtract
     * @return a readable difference
     */
    private static Set<String> minus(Set<String> from, Set<String> other) {
        Set<String> answer = new TreeSet<>(from);
        answer.removeAll(other);
        return answer;
    }
}
