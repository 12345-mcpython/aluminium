package com.laosun.aluminium.test.trigger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Every op parses its arguments once, at load time, instead of re-reading the bean on every hit.
 *
 * <p><b>Why this is the terminal form of the migration.</b> It started as a ratchet - a growing list of converted ops -
 * because the conversion landed one op at a time. That list is gone: what is left is the <b>closure</b>, "no handler
 * takes the untyped {@code OpArgs} unless it is on the list below", plus that list's own reason for existing.
 *
 * <p><b>Why some ops genuinely take nothing.</b> An op whose whole effect is "do this to the target" has no arguments
 * to parse: its sentence states a target and nothing else. Forcing an args record onto those would declare fields that
 * no line reads - the {@code as_attack} defect (validated, dumped, never consulted) wearing a new hat. So they keep
 * {@code OpArgs.NONE}, and this case names them so a <b>new</b> op cannot join them by accident.
 */
public class ArgsMigrationRatchetTest {

    private static final Path HANDLERS = Path.of("src", "main", "java", "com", "laosun", "aluminium", "trigger");

    /**
     * The ops that state no arguments at all, with the sentence each one is.
     *
     * <p>Adding a line here means "this op's JSON can only say WHERE, never WHAT". Every line is a claim about the
     * vocabulary, so each carries its own reason.
     */
    private static final Map<String, String> NO_ARGUMENTS = Map.of(
            "EXTRA_TURN", "the sentence is \"grant one more turn\" - the target is the whole effect",
            "INSERT_ACTION", "the sentence is \"insert an action\" - order comes from the queue, not from the effect",
            "DEPART", "the sentence is \"leave the field\" - nothing to measure or scope",
            "RETURN", "the sentence is \"come back\" - the inverse of DEPART, equally bare",
            "DRAW_ENERGY_BANK", "the whole store is drawn; no amount is stated because none could be",
            "RESET_RESOURCE_CAP", "the cap goes back to its declared value - the declaration is the argument",
            "DISMISS_SUMMON", "the sentence is \"the memosprite leaves\" - the target IS the summon");

    @Test
    public void everyOpParsesItsArgumentsUnlessItsSentenceStatesNone() throws IOException {
        Set<String> untyped = untypedOps();
        Set<String> unexplained = new TreeSet<>(untyped);
        unexplained.removeAll(NO_ARGUMENTS.keySet());
        Assertions.assertTrue(unexplained.isEmpty(),
                "these ops take the untyped OpArgs but state no reason to: " + unexplained
                        + " - either parse their arguments into a record, or add a line to NO_ARGUMENTS saying why"
                        + " the sentence has none");
    }

    @Test
    public void theNoArgumentsListDoesNotRot() throws IOException {
        Set<String> untyped = untypedOps();
        Set<String> stale = new TreeSet<>(NO_ARGUMENTS.keySet());
        stale.removeAll(untyped);
        Assertions.assertTrue(stale.isEmpty(),
                "these ops are on NO_ARGUMENTS but no longer take the untyped OpArgs (renamed, deleted, or converted): "
                        + stale + " - a list nobody prunes stops being a statement about the vocabulary");
    }

    /**
     * Reads every {@code *Op.java} in the trigger package and answers which handlers still take the untyped record.
     *
     * @return the op names whose {@code OpHandler<...>} is exactly {@code OpArgs}
     * @throws IOException when the sources cannot be read
     */
    private static Set<String> untypedOps() throws IOException {
        Pattern handler = Pattern.compile("implements OpHandler<([A-Za-z0-9_]+)>");
        Pattern opName = Pattern.compile("return TriggerOperator\\.([A-Z_0-9]+);");
        Set<String> untyped = new TreeSet<>();
        int handlers = 0;
        try (Stream<Path> files = Files.walk(HANDLERS)) {
            for (Path file : files.filter(each -> each.getFileName().toString().endsWith("Op.java")).toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                Matcher matcher = handler.matcher(source);
                if (!matcher.find()) {
                    continue;
                }
                handlers++;
                if (!"OpArgs".equals(matcher.group(1))) {
                    continue;
                }
                Matcher op = opName.matcher(source);
                untyped.add(op.find() ? op.group(1) : file.getFileName().toString());
            }
        }
        // A scan that matched nothing would make both cases above vacuously green.
        Assertions.assertTrue(handlers > 0, "no *Op.java handler was found to inspect");
        return untyped;
    }


}
