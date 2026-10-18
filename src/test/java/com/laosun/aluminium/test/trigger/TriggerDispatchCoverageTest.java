package com.laosun.aluminium.test.trigger;

import com.laosun.aluminium.models.trigger.TriggerOperator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Every WIRED op has a dispatch arm, and the dispatcher switches on the ENUM (not on the op string).
 *
 * <p>Why this cannot be left to the compiler: the enum switch does catch a misspelled {@code case} (that was the point
 * of moving off strings), but it cannot catch an op that was <b>never written at all</b> - that one falls into the
 * {@code default} arm and only fails when somebody's rule happens to use it. This test turns that into a load-order
 * fact: one arm per wired op, counted from the source.
 */
public class TriggerDispatchCoverageTest {

    private static final Path DISPATCHER =
            Path.of("src/main/java/com/laosun/aluminium/models/trigger/TriggerInterpreter.java");

    @Test
    public void thereIsOneArmPerWiredOp() throws IOException {
        String source = Files.readString(DISPATCHER);
        int from = source.indexOf("switch (operator)");
        Assertions.assertTrue(from > 0, "the dispatcher must switch on the TriggerOperator enum");

        int arms = 0;
        Matcher matcher = Pattern.compile("case ([A-Z_]+) ->").matcher(source.substring(from));
        while (matcher.find()) {
            arms++;
        }

        Assertions.assertEquals(TriggerOperator.wiredNames().size(), arms,
                "one dispatch arm per wired op: a new enum constant without an arm would only fail at run time");
    }

    @Test
    public void everyWiredOpIsARealEnumConstant() {
        for (String name : TriggerOperator.wiredNames()) {
            Assertions.assertNotNull(TriggerOperator.parse(name), name + " must parse back to a constant");
        }
    }
}
