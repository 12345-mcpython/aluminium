package com.laosun.aluminium.test.trigger;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.PredicateSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.trigger.Predicates;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The <b>predicate</b> system ({@code when_predicates}): composed, nestable, nameable conditions - the OR, the nesting
 * and the reuse that a flat {@code when} list cannot express.
 *
 * <p><b>Why each case is here.</b>
 * <ol>
 *   <li><b>{@code any_of} is the capability that did not exist</b>: {@code when} is an implicit AND, so "an ally, or
 *       the wearer" previously needed two rules that then had to agree about everything else. The case pins that one
 *       branch passing is enough.</li>
 *   <li><b>{@code all_of} is the AND, and it must be a real AND</b>: with one branch false the whole predicate fails -
 *       otherwise "all of" would be a synonym for "any of" and every composed guard would silently loosen.</li>
 *   <li><b>{@code not} on a party condition keeps the party rule</b>: when the party does not exist the condition
 *       <b>fails</b>, negated or not. This is the one place where the obvious implementation (a plain {@code !test})
 *       is wrong, and it is wrong in the direction that makes a rule fire.</li>
 *   <li><b>{@code ref} resolves a named predicate</b>, and a name that is not declared, or a loop, is a load-time
 *       failure rather than a rule that quietly never fires.</li>
 * </ol>
 */
public class PredicateTest {

    private static final int OWNER = 1001;
    private static final int LEVEL = 80;

    /**
     * {@code any_of}: the OR a {@code when} list cannot state.
     */
    @Test
    public void anyOfPassesWhenOneBranchPasses() {
        PredicateSpec either = anyOf(entity("target", "{\"is_ally\": true}"), entity("self", "{\"is_dead\": true}"));

        Assertions.assertTrue(test(either, context(true), null), "the first branch holds");
    }

    /**
     * {@code all_of}: every branch must hold.
     */
    @Test
    public void allOfFailsWhenOneBranchFails() {
        PredicateSpec both = allOf(entity("actor", "{\"is_ally\": true}"), entity("target", "{\"is_dead\": true}"));

        Assertions.assertFalse(test(both, context(false), null),
                "the actor branch holds but the target is the monster, so the AND must fail");
    }

    /**
     * {@code not} on a party condition: a missing party still FAILS, it is not "inverted into a match".
     */
    @Test
    public void notOnAPartyConditionStillFailsWhenThePartyIsMissing() {
        // "target is_ally" with no target in the event: the positive spelling fails, and so must the negation.
        TriggerTable.TriggerContext withoutTarget = new TriggerTable.TriggerContext(
                CharacterFactory.create(OWNER, LEVEL), CharacterFactory.create(OWNER, LEVEL), null,
                0, 0, null, null, SkillCategory.UNSPECIFIED);

        PredicateSpec notAlly = not(entity("target", "{\"is_ally\": true}"));
        Assertions.assertFalse(test(notAlly, withoutTarget, null),
                "with no target the condition fails, and its negation must fail too - never match");
    }

    /**
     * {@code ref}: a named predicate resolves; an unknown name is refused at load.
     */
    @Test
    public void aNamedPredicateResolvesAndUnknownNamesAreRefused() {
        Map<String, PredicateSpec> registry = new LinkedHashMap<>();
        registry.put("is_self", entity("target", "{\"is_ally\": true}"));

        Assertions.assertTrue(test(ref("is_self"), context(true), registry));

        IllegalArgumentException unknown = Assertions.assertThrows(IllegalArgumentException.class,
                () -> test(ref("nope"), context(true), registry));
        Assertions.assertTrue(unknown.getMessage().contains("nope"), unknown.getMessage());
    }

    /**
     * A loop between named predicates is refused at load, not left to fire forever.
     */
    @Test
    public void aLoopBetweenNamedPredicatesIsRefused() {
        Map<String, PredicateSpec> registry = new LinkedHashMap<>();
        registry.put("a", ref("b"));
        registry.put("b", ref("a"));

        IllegalArgumentException loop = Assertions.assertThrows(IllegalArgumentException.class,
                () -> test(ref("a"), context(true), registry));
        Assertions.assertTrue(loop.getMessage().contains("loop"), loop.getMessage());
    }

    /**
     * An unknown type is a load-time failure with the closed set in the message.
     */
    @Test
    public void anUnknownTypeIsRefused() {
        PredicateSpec bogus = new PredicateSpec();
        TriggerSpecs.set(bogus, "type", "sometimes");

        IllegalArgumentException error = Assertions.assertThrows(IllegalArgumentException.class,
                () -> test(bogus, context(true), null));
        Assertions.assertTrue(error.getMessage().contains("all_of"), error.getMessage());
    }

    // --- fixtures ---------------------------------------------------------------------------------------------------

    private static boolean test(PredicateSpec spec, TriggerTable.TriggerContext ctx, Map<String, PredicateSpec> registry) {
        return Predicates.compile(spec, rule(), registry).test(ctx);
    }

    private static TriggerSpec rule() {
        return TriggerSpecs.rule("TAKING_HIT", null);
    }

    /**
     * A context whose target is either the owner itself or a monster, and whose actor is the owner.
     */
    private static TriggerTable.TriggerContext context(boolean targetIsSelf) {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        Battle battle = new Battle(List.of(owner), List.of(BattleFixture.monster()), BattleFixture.seeded());
        Enemy monster = BattleFixture.monster();
        var target = targetIsSelf ? owner : monster;
        return new TriggerTable.TriggerContext(owner, owner, target, 0, 0, null, battle, SkillCategory.UNSPECIFIED);
    }

    /**
     * One entity predicate: which unit, and its sub-predicate map as JSON text (parsed by Gson, so the case reads the
     * same shape the data files write).
     */
    private static PredicateSpec entity(String subject, String json) {
        PredicateSpec spec = new PredicateSpec();
        TriggerSpecs.set(spec, "type", "entity");
        TriggerSpecs.set(spec, "entity", subject);
        TriggerSpecs.set(spec, "json", com.google.gson.JsonParser.parseString(json).getAsJsonObject());
        return spec;
    }

    private static PredicateSpec allOf(PredicateSpec... terms) {
        return terms("all_of", terms);
    }

    private static PredicateSpec anyOf(PredicateSpec... terms) {
        return terms("any_of", terms);
    }

    private static PredicateSpec terms(String type, PredicateSpec... terms) {
        PredicateSpec spec = new PredicateSpec();
        TriggerSpecs.set(spec, "type", type);
        TriggerSpecs.set(spec, "terms", List.of(terms));
        return spec;
    }

    private static PredicateSpec not(PredicateSpec term) {
        PredicateSpec spec = new PredicateSpec();
        TriggerSpecs.set(spec, "type", "not");
        TriggerSpecs.set(spec, "term", term);
        return spec;
    }

    private static PredicateSpec ref(String name) {
        PredicateSpec spec = new PredicateSpec();
        TriggerSpecs.set(spec, "type", "ref");
        TriggerSpecs.set(spec, "ref", name);
        return spec;
    }
}
