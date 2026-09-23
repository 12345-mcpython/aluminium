package com.laosun.aluminium.test;

import com.laosun.aluminium.Constant;
import com.laosun.aluminium.beans.StageBean;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * The lazy-loading guard rail for P7-4: {@code stage.json} (9 MB / about 29,000 stages) must not be parsed
 * when {@code Constant} is initialized, but should wait until stages are really needed for the first time.
 *
 * <p>Why it deserves a guard rail: {@code stage.json} is larger than all the other data put together.
 * Moving it back into the static block would mean paying ~35 MB of heap + tens of milliseconds on **every**
 * {@code Constant} initialization.
 *
 * <p><b>How it is made independent of test-case order</b>: it does not look at runtime state but directly
 * at whether the static initializer block of {@code Constant.class} contains any stage-related symbol.
 * Runtime state (how many times it was parsed) **inevitably** depends on which test class ran first in the
 * shared JVM — my first version was written that way: green when run alone, red in the full suite. The class
 * file, however, is dead and nobody can change it.
 */
public class StageLazyLoadTest {

    /**
     * The stage table is parsed only once (cache semantics), and what is returned is the very same instance.
     */
    @Test
    public void stagesAreParsedOnceAndCached() {
        Map<Integer, StageBean> first = Constant.stages();
        Map<Integer, StageBean> second = Constant.stages();

        Assertions.assertSame(first, second, "repeated access MUST hit the same cache entry");
        Assertions.assertTrue(Constant.stageLoadAttempts() <= 1,
                "stage.json is parsed at most once, actually " + Constant.stageLoadAttempts() + " times");
    }

    /**
     * The core guard rail: the **static initializer block** of {@link Constant} must not mention the stage
     * table.
     *
     * <p>Why only the static block may be sliced out and the whole class must not be scanned: the body of
     * the {@code stages()} method will naturally contain {@code Constant$StageHolder.LOADED} (that is the
     * lazy-loading read point), so scanning the whole class would necessarily produce a false positive.
     *
     * <p>The method: disassemble {@code Constant.class}, start at the {@code static {}} line, collect until
     * the next member declaration, and assert that this stretch contains neither {@code stage.json} nor
     * {@code StageHolder}.
     *
     * <p>The reverse assertion is that {@code Constant$StageHolder} **really does** contain
     * {@code stage.json}, otherwise this test would degenerate into an empty assertion of "the string is not
     * in the project at all".
     *
     * <p>Independent of test-case order, and also independent of whether the data files were generated
     * (it only looks at the compilation output).
     */
    @Test
    public void staticInitializerDoesNotTouchTheStageTable() {
        String disassembly = disassemble(Constant.class.getName());
        Assumptions.assumeTrue(disassembly != null, "javap unavailable, skipping the bytecode-level guard rail");

        String clinit = sliceStaticInitializer(disassembly);
        Assertions.assertFalse(clinit.isBlank(), "the static block was not sliced out — did the javap output format change?");
        Assertions.assertFalse(clinit.contains("stage.json"),
                "stage.json appears in Constant's static block → lazy loading is broken:\n" + clinit);
        Assertions.assertFalse(clinit.contains("StageHolder"),
                "StageHolder is referenced in Constant's static block → lazy loading is broken:\n" + clinit);
        // The compiler will inline stages()/load(), so the two checks above cannot catch
        // "calls stages() while it is at it". What truly cannot appear in the static block is
        // "parsing this file" itself.
        Assertions.assertFalse(clinit.contains("Method stages:"),
                "stages() is called in Constant's static block → lazy loading is broken:\n" + clinit);
        Assertions.assertFalse(clinit.contains("Method load:"),
                "load() is called in Constant's static block → lazy loading is broken:\n" + clinit);

        String holder = disassemble("com.laosun.aluminium.Constant$StageHolder");
        Assumptions.assumeTrue(holder != null, "could not obtain a disassembly of StageHolder");
        Assertions.assertTrue(holder.contains("stage.json"),
                "the lazy-loading carrier should contain stage.json, otherwise this test is meaningless");
    }

    // ==================================================================

    /** Disassemble the given class with the current JDK's javap; return {@code null} when unavailable (the caller skips). */
    private static String disassemble(String className) {
        try {
            String javap = System.getProperty("java.home") + "/bin/javap";
            ProcessBuilder builder = new ProcessBuilder(javap, "-p", "-c",
                    "-classpath", System.getProperty("java.class.path"), className);
            builder.redirectErrorStream(true);
            Process process = builder.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return process.waitFor() == 0 ? output : null;
        } catch (IOException | InterruptedException e) {
            return null;
        }
    }

    /**
     * Slice out the {@code static {}} stretch (javap labels the static initializer block as
     * {@code static {}}).
     *
     * <p>javap's indentation rule: the static block itself is written flush left as {@code static {}};, the
     * block body is indented by 4 spaces, and the next member declaration is flush left (indented 2 spaces,
     * e.g. {@code public static final ...}).
     * So "the first non-blank line with indent ≤ 2" marks the end of the block.
     */
    private static String sliceStaticInitializer(String disassembly) {
        StringBuilder clinit = new StringBuilder();
        boolean inside = false;
        for (String line : disassembly.lines().toList()) {
            if (line.strip().equals("static {};")) {
                inside = true;
                continue;
            }
            if (inside) {
                int indent = line.length() - line.stripLeading().length();
                if (!line.isBlank() && indent <= 2) {
                    break;                                  // next member, the static block ends
                }
                clinit.append(line).append('\n');
            }
        }
        return clinit.toString();
    }
}
