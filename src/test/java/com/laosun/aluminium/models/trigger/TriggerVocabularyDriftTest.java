package com.laosun.aluminium.trigger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TRIGGER.md §3.5 and the loader must name the same vocabulary - both directions, or the guard does nothing.
 *
 * <p><b>Why the document keeps a table at all.</b> The rest of {@code TRIGGER.md} refuses to copy the operator
 * table into prose, and says why: a second copy drifts and nothing notices. This table is a deliberate exception,
 * because its value is not the NAMES but the one-line "what it reads" beside each name, and that summary exists
 * nowhere else in one place - in the engine the reading is a switch with a comment per branch. Pointing at the
 * source instead would either lose the summary or rebuild it inside javadoc, i.e. copy it somewhere less
 * readable for the people it is for. So the copy stays, and this guard is what makes it safe: a closed
 * vocabulary is mechanically comparable, so "did the two drift" is a question a test can answer.
 *
 * <p>⚠ What this catches that a reviewer would not. Adding a variable to {@code NUMERIC_VARIABLES} compiles,
 * ships and works - and the author has no reason to know a prose table elsewhere needs the same name. The
 * failure is invisible until someone reads the doc and writes a rule the doc forbids, or cannot find one the doc
 * omits. Neither shows up in any other test.
 *
 * <p>⚠ Both directions are asserted. Only checking "every code name is documented" would let the doc keep
 * advertising a name that was removed - worse than a gap, because the author believes it.
 */
public class TriggerVocabularyDriftTest {

    private static final Path TRIGGER_MD = Path.of("TRIGGER.md");

    /** The §3.5 section, from its heading to the next one. */
    private static String section() throws Exception {
        String md = Files.readString(TRIGGER_MD);
        int start = md.indexOf("### 3.5");
        Assertions.assertTrue(start > 0, "TRIGGER.md no longer has a §3.5 heading; this guard cannot find its table");
        int next = md.indexOf("\n### ", start + 5);
        return next > 0 ? md.substring(start, next) : md.substring(start);
    }

    /**
     * The vocabulary the section names: every backticked identifier in the FIRST cell of a row.
     *
     * <p>⚠ The first cell only. A name mentioned in the prose column ("see {@code target_debuff_count}") is not a
     * table entry, and counting it would let a deleted row survive because a neighbouring sentence still says the
     * word. Backticks are the delimiter precisely because the prose also uses bold around some names.
     */
    private static Set<String> documentedVariables() throws Exception {
        Set<String> out = new TreeSet<>();
        Matcher row = Pattern.compile("^\\|\\s*([^|]+?)\\s*\\|", Pattern.MULTILINE).matcher(section());
        while (row.find()) {
            Matcher name = Pattern.compile("`([a-z_]+)`").matcher(row.group(1));
            while (name.find()) {
                out.add(name.group(1));
            }
        }
        return out;
    }

    /** The same for the prefix table, where the cell reads {@code `prefix:parameter`}. */
    private static Set<String> documentedPrefixes() throws Exception {
        Set<String> out = new TreeSet<>();
        Matcher row = Pattern.compile("^\\|\\s*([^|]+?)\\s*\\|", Pattern.MULTILINE).matcher(section());
        while (row.find()) {
            Matcher name = Pattern.compile("`([a-z_]+):").matcher(row.group(1));
            while (name.find()) {
                out.add(name.group(1) + ":");
            }
        }
        return out;
    }

    /** Every {@code *_PREFIX} constant in the parser, by reflection - so this guard holds no second list. */
    private static Map<String, String> prefixConstants() throws Exception {
        Map<String, String> out = new LinkedHashMap<>();
        for (Field field : TriggerConditionParser.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || field.getType() != String.class
                    || !field.getName().endsWith("_PREFIX")) {
                continue;
            }
            field.setAccessible(true);
            String value = (String) field.get(null);
            if (value != null && value.endsWith(":")) {
                out.put(field.getName(), value);
            }
        }
        return out;
    }

    @Test
    public void theDocumentedVariablesAreExactlyTheLoadedOnes() throws Exception {
        Set<String> documented = documentedVariables();
        Set<String> loaded = new TreeSet<>(TriggerConditionParser.NUMERIC_VARIABLES);

        Set<String> onlyInCode = new TreeSet<>(loaded);
        onlyInCode.removeAll(documented);
        Set<String> onlyInDoc = new TreeSet<>(documented);
        onlyInDoc.removeAll(loaded);

        Assertions.assertTrue(onlyInCode.isEmpty(),
                "these variables load but §3.5 does not list them, so nobody can find them: " + onlyInCode);
        Assertions.assertTrue(onlyInDoc.isEmpty(),
                "§3.5 lists these but the loader does not accept them, so a rule written from the doc is "
                        + "refused at start-up: " + onlyInDoc);
    }

    @Test
    public void theDocumentedPrefixesAreExactlyTheLoadedOnes() throws Exception {
        Set<String> documented = documentedPrefixes();
        Set<String> loaded = new TreeSet<>(prefixConstants().values());

        Set<String> onlyInCode = new TreeSet<>(loaded);
        onlyInCode.removeAll(documented);
        Set<String> onlyInDoc = new TreeSet<>(documented);
        onlyInDoc.removeAll(loaded);

        Assertions.assertTrue(onlyInCode.isEmpty(),
                "these parameterised prefixes load but §3.5 does not list them: " + onlyInCode);
        Assertions.assertTrue(onlyInDoc.isEmpty(),
                "§3.5 lists these prefixes but no constant declares them: " + onlyInDoc);
    }

    /**
     * The probe the two cases above rest on: the section is found, both tables parse, and the parse is not empty.
     * Without this, a renamed heading turns both cases into "two empty sets are equal" - green, and silent.
     */
    @Test
    public void theGuardSeesBothTables() throws Exception {
        Map<String, String> constants = prefixConstants();
        Assertions.assertTrue(constants.size() >= 5,
                "found only " + constants.size() + " prefix constants: " + constants.keySet());
        Assertions.assertEquals(constants.size(), new TreeSet<>(constants.values()).size(),
                "two prefix constants hold the same value, so one of them is dead: " + constants);

        Set<String> variables = documentedVariables();
        Set<String> prefixes = documentedPrefixes();
        Assertions.assertTrue(variables.size() >= 15, "parsed only " + variables + " from the variable table");
        Assertions.assertTrue(prefixes.size() >= 5, "parsed only " + prefixes + " from the prefix table");
        Assertions.assertEquals(TriggerConditionParser.NUMERIC_VARIABLES.size(), variables.size(),
                "same set, different size - a name is listed twice in the doc: " + variables);
        Assertions.assertEquals(constants.size(), prefixes.size(),
                "same set, different size - a prefix is listed twice in the doc: " + prefixes);
    }
}
