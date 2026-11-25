package com.laosun.aluminium.test.trigger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * How many ops read their arguments once, at load time, instead of re-reading the bean on every hit.
 *
 * <p><b>Why a ratchet and not a rule.</b> "Parse the arguments" is the second half of the op migration, and it lands
 * one op at a time: each conversion changes a handler's {@code OpHandler<OpArgs>} into {@code OpHandler<XxxArgs>}. This
 * case names the ops that have moved, so the number can only grow - and so a conversion cannot be quietly undone by a
 * later edit that reverts the type parameter.
 *
 * <p><b>What it does not check.</b> Whether the parse is <i>complete</i> - an args record with one field where three
 * were read is still a conversion. The compiler enforces that the run-time half uses the record; this case only counts
 * who has made the move.
 */
public class ArgsMigrationRatchetTest {

    private static final Path HANDLERS = Path.of("src", "main", "java", "com", "laosun", "aluminium", "trigger");

    /**
     * Ops whose arguments are parsed. Add one line when an op moves; never remove a line to make this pass.
     *
     * <ul>
     *   <li>{@code IGNORE_WEAKNESS} - the cast count, and the "unstated means one" decision, are read once;</li>
     *   <li>{@code RESET_RULE_CHANCE} - the rule id it names;</li>
     *   <li>{@code START_COUNTDOWN} - the stated name (or none) and the Speed;</li>
     *   <li>{@code SUMMON} - which of the three shapes, and the memosprite's name and Speed;</li>
     *   <li>{@code RAISE_SKILL_LEVEL} - which of the three slot spellings, and how many levels;</li>
     *   <li>{@code ADVANCE} / {@code DELAY} / {@code GAIN_SKILL_POINT} - the one magnitude each states, held as a
     *       {@code Magnitude} so the run-time half never names the bean;</li>
     *   <li>{@code HEAL} / {@code CONSUME_HP} - the same, resolved through the grant path.</li>
     * </ul>
     *
     * <p><b>What is NOT on this list on purpose.</b> {@code BOOST_DAMAGE} reads its magnitude <i>and</i> its
     * {@code per_stack} multiplier, and stacking is a different cluster with its own reader; carrying only half of it
     * would move the type without moving the knowledge. It waits for {@code per_stack} to be parsed too.
     */
    private static final Set<String> PARSED = Set.of(
            "IGNORE_WEAKNESS",
            "RESET_RULE_CHANCE",
            "START_COUNTDOWN",
            "SUMMON",
            "RAISE_SKILL_LEVEL",
            "ADVANCE",
            "DELAY",
            "GAIN_SKILL_POINT",
            "HEAL",
            "CONSUME_HP",
            "SHIELD");

    @Test
    public void theOpsThatParseTheirArgumentsAreStillParsingThem() throws IOException {
        Set<String> parsedNow = parsedOps();
        Set<String> lost = new TreeSet<>(PARSED);
        lost.removeAll(parsedNow);
        Assertions.assertTrue(lost.isEmpty(),
                "these ops used to parse their arguments and now read the bean again: " + lost
                        + " - the args record is the point of the conversion, not an optional extra");
    }

    @Test
    public void noOpRevertsToTheUntypedShapeWithoutFailing() throws IOException {
        // The count is the progress bar. It is asserted as "at least", so a new conversion does not have to update
        // this file - only a reversion does.
        Assertions.assertTrue(parsedOps().size() >= PARSED.size(),
                "parsed ops went from " + PARSED.size() + " to " + parsedOps().size());
    }

    /**
     * Reads every {@code *Op.java} in the trigger package and answers which handlers take a typed args record.
     *
     * @return the op names whose {@code OpHandler<...>} is not the untyped {@code OpArgs}
     * @throws IOException when the sources cannot be read
     */
    private static Set<String> parsedOps() throws IOException {
        Pattern handler = Pattern.compile("implements OpHandler<([A-Za-z0-9_]+)>");
        Pattern opName = Pattern.compile("return TriggerOperator\\.([A-Z_0-9]+);");
        Set<String> parsed = new TreeSet<>();
        TreeMap<String, String> untyped = new TreeMap<>();
        try (Stream<Path> files = Files.walk(HANDLERS)) {
            for (Path file : files.filter(each -> each.getFileName().toString().endsWith("Op.java")).toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                Matcher matcher = handler.matcher(source);
                if (!matcher.find()) {
                    continue;
                }
                Matcher op = opName.matcher(source);
                String name = op.find() ? op.group(1) : file.getFileName().toString();
                if ("OpArgs".equals(matcher.group(1))) {
                    untyped.put(name, file.getFileName().toString());
                } else {
                    parsed.add(name);
                }
            }
        }
        // Reported through the assertion of the other case; kept here so an unreadable file is loud rather than silent.
        Assertions.assertFalse(untyped.isEmpty() && parsed.isEmpty(), "no handler was found at all");
        return parsed;
    }
}
