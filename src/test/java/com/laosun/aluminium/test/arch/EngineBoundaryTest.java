package com.laosun.aluminium.test.arch;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * The engine's handle must not leak into the layers under it.
 *
 * <p><b>What this pins.</b> {@code com.laosun.aluminium.Battle} is the whole runtime - rosters, queue, damage,
 * resources, RNG. Every class that holds one can call anything on it, which is how 367 call sites from 21 classes
 * accumulated, and why {@code buff}, {@code entity}, {@code event} and {@code trigger} all had to be read as part of
 * "the battle" rather than on their own. The refactor replaces that handle with narrow ports
 * ({@code engine.port}); this case is the ratchet that stops new holders appearing while that happens.
 *
 * <p><b>Why it does not fail when the list shrinks.</b> {@link #KNOWN} is a backlog, not a permission. A case that
 * fails when a backlog entry is removed turns every correct step into a red - the trap {@code ContentProvenanceTest}
 * used to have. The stale entries are reported in the failure message of the <em>other</em> direction instead, so a
 * new holder shows both facts at once.
 *
 * <p><b>Scope.</b> It reads the source, not the class files: a reference is an {@code import} of {@code Battle} or the
 * inline fully-qualified name. Javadoc mentions are deliberately not counted - a {@code {@link Battle#aggroOf}} line
 * is prose about the engine, not a dependency on it.
 */
public class EngineBoundaryTest {

    private static final Path MAIN = Path.of("src", "main", "java");
    private static final String ROOT_PACKAGE = "com.laosun.aluminium";
    private static final String BATTLE = "com.laosun.aluminium.Battle";
    private static final String BATTLE_PACKAGE = "com.laosun.aluminium.battle.";

    /** Packages that may hold the engine's handle: the engine itself, and the two factories that build one. */
    private static final Set<String> ALLOWED = Set.of("", "battle", "utils");

    /**
     * The backlog: packages that still hold a {@code Battle} and must stop. Each one is a target of the port work.
     *
     * <p>Where each shows up:
     * <ul>
     *   <li>{@code buff} - {@code AbstractBuff}/{@code BuffManager} and four buff classes, for {@code fireStateEnded};</li>
     *   <li>{@code event} - the eleven hooks take {@code Battle} as their first argument;</li>
     *   <li>{@code trigger} - the interpreter executes effects against the engine, which is the point of it;</li>
     *   <li>{@code entity} - {@code CanHit.onKill(Battle, ...)} and friends;</li>
     *   <li>{@code damage} / {@code skill} / {@code enemy} / {@code data} / {@code ai} - one or two files each.</li>
     * </ul>
     */
    private static final Set<String> KNOWN = Set.of(
            "ai", "buff", "damage", "data", "enemy", "entity", "event", "skill", "trigger");

    @Test
    public void noPackageOutsideTheEngineHoldsABattle() throws IOException {
        Map<String, Set<String>> holders = holdersOf(BATTLE);
        assertNoNewHolders("Battle", holders, ALLOWED, KNOWN);
    }

    @Test
    public void noPackageOutsideTheEngineReachesIntoTheBattlePackage() throws IOException {
        Map<String, Set<String>> holders = holdersOf(BATTLE_PACKAGE);
        // The engine's own services, the root package (Battle wires them), and the one factory that assembles a run.
        assertNoNewHolders("battle.*", holders, ALLOWED, Set.of());
    }

    /**
     * Asserts every holder is allowed or known, and that nothing unexpected appeared.
     *
     * @param what    what was searched for, for the message
     * @param holders package to files that reference it
     * @param allowed packages that may always do so
     * @param known   packages that still do and must stop
     */
    private static void assertNoNewHolders(String what, Map<String, Set<String>> holders,
                                           Set<String> allowed, Set<String> known) {
        Map<String, Set<String>> offenders = new TreeMap<>();
        for (var entry : holders.entrySet()) {
            String pkg = entry.getKey();
            if (allowed.contains(pkg) || known.contains(pkg)) {
                continue;
            }
            offenders.put(pkg, entry.getValue());
        }
        Set<String> stale = new TreeSet<>();
        for (String pkg : known) {
            if (!holders.containsKey(pkg)) {
                stale.add(pkg);
            }
        }
        if (!offenders.isEmpty()) {
            StringBuilder message = new StringBuilder("these packages hold a ").append(what)
                    .append(" and are neither allowed nor known:\n");
            for (var entry : offenders.entrySet()) {
                message.append("  ").append(entry.getKey()).append(": ")
                        .append(String.join(", ", entry.getValue())).append('\n');
            }
            if (!stale.isEmpty()) {
                message.append("  (also stale in KNOWN, delete: ").append(String.join(", ", stale)).append(")\n");
            }
            message.append("Route the call through engine.port instead of widening the allowlist.");
            Assertions.fail(message.toString());
        }
    }

    /**
     * Every package (top segment under {@code com.laosun.aluminium}) that has a file referencing {@code needle}.
     *
     * @param needle the fully-qualified name or package prefix to look for
     * @return package name to the file names that reference it
     */
    private static Map<String, Set<String>> holdersOf(String needle) throws IOException {
        Map<String, Set<String>> holders = new TreeMap<>();
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path file : files.filter(each -> each.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                if (!source.contains(needle)) {
                    continue;
                }
                String pkg = packageOf(source);
                if (pkg == null || !pkg.startsWith(ROOT_PACKAGE)) {
                    continue;
                }
                String sub = pkg.equals(ROOT_PACKAGE) ? "" : pkg.substring(ROOT_PACKAGE.length() + 1);
                if (sub.contains(".")) {
                    sub = sub.substring(0, sub.indexOf('.'));
                }
                holders.computeIfAbsent(sub, key -> new TreeSet<>()).add(file.getFileName().toString());
            }
        }
        return holders;
    }

    /**
     * @param source a compilation unit
     * @return its package, or {@code null} when it declares none
     */
    private static String packageOf(String source) {
        for (String line : source.lines().limit(40).toList()) {
            String trimmed = line.trim();
            if (trimmed.startsWith("package ")) {
                return trimmed.substring("package ".length(), trimmed.indexOf(';')).trim();
            }
        }
        return null;
    }
}
