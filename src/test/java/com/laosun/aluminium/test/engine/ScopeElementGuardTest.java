package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.test.support.TriggerSpecs;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * The two refusals that make {@code scope_element} safe, both found by an AUDIT rather than by a failing number.
 *
 * <p>The sibling of {@link DamageTypeScopeValidationTest}, and for the same reason: an argument the loader accepts and the
 * runtime ignores is worse than one it refuses, because the rule looks right.
 *
 * <p>⚠ <b>Gap 1 -- a closed set.</b> {@code DamageElement.fromString} answers {@code null} for "Unknown" and for typos alike, and
 * the interpreter turns that null into "no scope". So a misspelt element would have produced a zone applying to EVERY element: a
 * wrong number with no symptom. The other arguments in that class are closed sets for exactly this reason.
 *
 * <p>⚠ <b>Gap 2 -- only the reading op may state it.</b> The engine has no global "unknown argument" check: refusals are
 * per-op, so a field one op READS is silently IGNORED by the other forty. {@code damage_type} already had this half
 * ({@code DAMAGE_TYPE_READERS}) after an op accepted it and ignored it; {@code scope_element} was first written without it and was
 * accepted by every op, which this case pins.
 */
public class ScopeElementGuardTest {
    private static final int OWNER = 1203;

    private static TriggerSpec scoped(String op, String element) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", op);
        TriggerSpecs.set(effect, "percent", 0.1);
        TriggerSpecs.set(effect, "turns", 2);
        TriggerSpecs.set(effect, "target", "all_enemies");
        TriggerSpecs.set(effect, "scopeElement", element);   // the JAVA field name, not the JSON one
        return TriggerSpecs.rule("BATTLE_START", null, effect);
    }

    private static TriggerTable table(TriggerSpec rule) {
        return new TriggerTable(OWNER, List.of(rule));
    }

    /**
     * ⭐ The op that READS it accepts a real element.
     */
    @Test
    public void theReadingOpAcceptsARealElement() {
        Assertions.assertNotNull(table(scoped("MODIFY_DAMAGE_TAKEN", "Fire")),
                "MODIFY_DAMAGE_TAKEN really does scope a damage-taken zone by element");
        System.out.println("[scope_element] MODIFY_DAMAGE_TAKEN + Fire accepted");
    }

    /**
     * ⭐ A misspelt element is refused, rather than quietly meaning "every element".
     */
    @Test
    public void anUnknownElementIsRefused() {
        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> table(scoped("MODIFY_DAMAGE_TAKEN", "FIREFLY")),
                "an unknown element must not load: it would WIDEN the zone to every element instead of narrowing it");
        String message = String.valueOf(refused.getMessage());
        System.out.println("[scope_element] FIREFLY refused: " + message.substring(0, Math.min(110, message.length())));
        Assertions.assertTrue(message.contains("FIREFLY"), "the message names the offending spelling");
    }

    /**
     * ⭐ An op that does not read it refuses it, rather than accepting and ignoring it.
     */
    @Test
    public void anOpThatDoesNotReadItIsRefused() {
        for (String op : List.of("APPLY_BUFF", "GAIN_ENERGY", "HEAL", "MODIFY_ATTR")) {
            IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                    () -> table(scoped(op, "Fire")),
                    op + " does not read scope_element, so stating it must fail at load time");
            String message = String.valueOf(refused.getMessage());
            System.out.println("[scope_element] " + op + " refused: "
                    + message.substring(0, Math.min(90, message.length())));
            Assertions.assertTrue(message.contains("scope_element"), "the message names the offending field");
        }
    }
}
