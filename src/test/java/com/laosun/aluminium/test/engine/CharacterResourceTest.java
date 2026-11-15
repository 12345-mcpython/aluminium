package com.laosun.aluminium.test.engine;


import com.google.gson.Gson;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.ResourceSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.exceptions.CharacterException;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TestCharacters;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import com.laosun.aluminium.state.ResourceManager;

/**
 * A character's <b>declared resources</b> and the {@code self_resource:<NAME>} condition that reads them.
 *
 * <p><b>The hole this closes.</b> Without this, {@code ResourceManager} can hold a value but no content can
 * declare one, and the condition DSL cannot read one: {@code gain} answers {@code 0} for an unknown id and
 * {@code value} answers {@code 0} too, so a rule about "充能" (charge) is a no-op that reports nothing. 41 of the 9
 * character documents in the corpus gate something on a count, which is the largest single gap the scan found
 * ({@code AGENT.md}) - and a whole family of kits (Acheron's [残梦] (Dreamscape), Feixiao's [飞黄] (Flying Yellow), Cyrene's [追忆] (Recollection)) is built on
 * it.
 *
 * <p><b>Three things are pinned here</b>, each of which would otherwise be a rule that loads and quietly does the
 * wrong thing:
 * <ol>
 *   <li>the file shapes - the object form that declares resources, and the bare array that stays valid for every
 *       character with nothing but rules;</li>
 *   <li>that an <b>undeclared</b> resource fails both the read and the write, at build time, rather than reading as
 *       0;</li>
 *   <li>that {@code self_resource:} is read off the rule's <b>owner</b>, like {@code self_attr:} and unlike the
 *       event's actor.</li>
 * </ol>
 *
 * <p>The fixtures under {@code src/test/resources/game/characters/} are the loader's own fixtures - the same trick
 * {@code relic_sets/113.json} uses for relic rules - so the shapes are exercised through {@link TriggerTables}
 * rather than by calling Gson directly.
 *
 * <p><b>Scenario grouping.</b> Each {@code @Test} below is one scenario: the single-clause cases that observe the
 * same object (the same loader call, the same character, the same battle, the same merge or the same validation
 * point) are folded into one {@link Assertions#assertAll} whose entries each carry a message naming the clause.
 * Exception paths stay in their own scenarios, never mixed into a positive one.
 */
public class CharacterResourceTest {
    private static final double EPS = 1e-6;

    /**
     * Himeko: the shipped character whose file declares a resource.
     */
    private static final int HIMEKO = 1003;
    /**
     * An ordinary character with no rules and no declarations ({@link TestCharacters}).
     */
    // Note: a REAL character with no `resources` block (1002 is a bare rule list, and it has a content file -- see TestCharacters). The assertions here are about DECLARATIONS, and
    // every one of them installs its own rule table, so the character's own rules never enter the measurement.
    private static final int PLAIN = 1002;
    private static final int LEVEL = 80;
    private static final String CHARGE = "充能";
    /** Himeko's own charge, as her rule file spells it after the identity migration. */
    private static final String HIMEKO_CHARGE = "char:1003/charge";

    /**
     * The fixture cids (see {@code src/test/resources/game/characters/}).
     */
    private static final int OBJECT_FORM = 9001;
    private static final int OBJECT_WITHOUT_RULES = 9002;
    private static final int UNKNOWN_TOP_LEVEL_KEY = 9003;
    private static final int MISSPELLED_DECLARATION_KEY = 9004;
    private static final int ZERO_CAP = 9005;
    private static final int ARRAY_FORM = 9006;

    // ==================================================================
    // 1. The two file shapes, and the shapes that are refused
    // ==================================================================

    /**
     * The two file shapes: the object form declares resources and reads its rules from the same file, while the bare
     * array stays valid and <b>declares nothing</b>.
     *
     * <p>Worth pinning both ways: the thirteen files written before resources existed are arrays, and one of them
     * could otherwise start "declaring" a resource by accident the day a rule inside it mentions one.
     */
    @Test
    public void theTwoFileShapesLoadAsDeclared() {
        TriggerTable objectForm = TriggerTables.of(OBJECT_FORM);
        TriggerTable arrayForm = TriggerTables.of(ARRAY_FORM);
        List<ResourceSpec> declared = objectForm.resources();

        Assertions.assertAll("the two character file shapes",
                () -> Assertions.assertEquals(1, objectForm.resources().size(), "object form: one declaration"),
                () -> Assertions.assertEquals(CHARGE, declared.getFirst().id(), "object form: the declared resource is 充能"),
                () -> Assertions.assertEquals(3, declared.getFirst().max(),
                        "object form: 「上限3点」 (cap 3) is the cap, and it is stated, not inferred"),
                () -> Assertions.assertEquals(0, declared.getFirst().initial(), "object form: absent \"initial\" means 0"),
                () -> Assertions.assertEquals(1, objectForm.ruleCount(TriggerEvent.BREAK),
                        "object form: and the rules are read from the same file"),
                () -> Assertions.assertEquals(Set.of(CHARGE), objectForm.referencedResources(),
                        "object form: the rule names the resource, so the table knows what to check against the declarations"),
                () -> Assertions.assertTrue(arrayForm.resources().isEmpty(),
                        "array form: an array has nowhere to declare a resource"),
                () -> Assertions.assertEquals(1, arrayForm.ruleCount(TriggerEvent.BREAK),
                        "array form: the rule still loads"),
                () -> Assertions.assertEquals(Set.of(CHARGE), arrayForm.referencedResources(),
                        "array form: the rule still references one -- and that is exactly what the assembly check must catch"));
    }

    /**
     * A malformed file shape is refused where the file is read: an object without the {@code rules} half, a key Gson
     * would silently drop (at both levels), and a cap that cannot hold anything.
     *
     * <p>The declaration-level unknown key is the one that matters: {@code "intial": 1} would be read as "starts at
     * 0", a wrong number with nothing to report.
     */
    @Test
    public void malformedFileShapesAreRefusedAtLoad() {
        IllegalStateException noRules = Assertions.assertThrows(IllegalStateException.class,
                () -> TriggerTables.of(OBJECT_WITHOUT_RULES));
        IllegalStateException unknownTopLevel = Assertions.assertThrows(IllegalStateException.class,
                () -> TriggerTables.of(UNKNOWN_TOP_LEVEL_KEY));
        IllegalStateException misspelledDeclaration = Assertions.assertThrows(IllegalStateException.class,
                () -> TriggerTables.of(MISSPELLED_DECLARATION_KEY));
        IllegalStateException zeroCap = Assertions.assertThrows(IllegalStateException.class,
                () -> TriggerTables.of(ZERO_CAP));

        Assertions.assertAll("malformed file shapes are refused at load",
                () -> Assertions.assertTrue(noRules.getMessage().contains("rules"),
                        "object without rules: the message must name the missing half: " + noRules.getMessage()),
                () -> Assertions.assertTrue(unknownTopLevel.getMessage().contains("trigger"),
                        "unknown top-level key: the misspelled \"trigger\" must be named: " + unknownTopLevel.getMessage()),
                () -> Assertions.assertTrue(misspelledDeclaration.getMessage().contains("intial"),
                        "unknown declaration key: so must \"intial\": " + misspelledDeclaration.getMessage()),
                () -> Assertions.assertTrue(zeroCap.getMessage().contains("max"),
                        "zero cap: the message must be about the cap: " + zeroCap.getMessage()));
    }

    /**
     * The declaration's own validation runs on the <b>JSON</b> path, not only when built by hand.
     *
     * <p>Gson constructs records through their canonical constructor, which is the compact one - the alternative
     * would be a validator that silently never ran for real content - but it <b>wraps</b> what that constructor
     * throws, so the reason arrives as the cause. That is exactly why {@code TriggerTables} reports the deepest
     * message in the chain (see the zero-cap fixture case above, which asserts the sentence itself): a content
     * error must not be reduced to "Failed to invoke constructor".
     */
    @Test
    public void theDeclarationIsValidatedOnTheJsonPath() {
        ResourceSpec parsed = new Gson().fromJson("{\"id\":\"充能\",\"max\":3}", ResourceSpec.class);

        Assertions.assertAll("a declaration is validated on the JSON path",
                () -> Assertions.assertEquals(CHARGE, parsed.id(), "parsed: the id is read"),
                () -> Assertions.assertEquals(3, parsed.max(), "parsed: the cap is read"),
                () -> Assertions.assertEquals(0, parsed.initial(), "parsed: the compact constructor fills the absent initial"),
                () -> Assertions.assertTrue(rejectionOf("{\"id\":\"充能\",\"max\":0}").contains("max"), "a cap of 0"),
                () -> Assertions.assertTrue(rejectionOf("{\"id\":\"充能\"}").contains("max"), "no cap at all"),
                () -> Assertions.assertTrue(rejectionOf("{\"max\":3}").contains("id"), "no id"),
                () -> Assertions.assertTrue(rejectionOf("{\"id\":\"充 能\",\"max\":3}").contains("whitespace"),
                        "a name with a space inside cannot be spelled in a condition"),
                () -> Assertions.assertTrue(rejectionOf("{\"id\":\"充能\",\"max\":3,\"initial\":4}").contains("initial"),
                        "a start above the cap"));
    }

    // ==================================================================
    // 2. Declared = registered on the character
    // ==================================================================

    /**
     * The assembly point registers what the file declares, with the cap it declares - and a character who declares
     * nothing gets nothing, no resource appears out of the engine's sleeve.
     */
    @Test
    public void aDeclaredResourceIsRegisteredOnTheCharacter() {
        Character himeko = CharacterFactory.create(HIMEKO, LEVEL);
        Character plain = CharacterFactory.create(PLAIN, LEVEL);

        Assertions.assertAll("declared = registered on the character",
                () -> Assertions.assertTrue(himeko.getResources().has(HIMEKO_CHARGE),
                        "himeko: she declares it in characters/1003.json"),
                () -> Assertions.assertEquals(0, himeko.getResources().value(HIMEKO_CHARGE),
                        "himeko: at 0: 「战斗开始时获得1点充能」 (gain 1 charge at battle start) is a rule, so it is the battle that fills it"),
                () -> Assertions.assertEquals(3, himeko.getResources().get(HIMEKO_CHARGE).getMax(),
                        "himeko: with the declared cap"),
                () -> Assertions.assertEquals(0, plain.getResources().size(),
                        "plain: a character without declarations has no resources"));
    }

    // ==================================================================
    // 3. The condition: read, and read off the owner
    // ==================================================================

    /**
     * The threshold decides, the value is the owner's own, and an <b>undeclared</b> resource fails the condition
     * instead of reading as 0.
     *
     * <p>The undeclared part uses a threshold deliberately {@code >= 0}, which <em>any</em> number would satisfy - so
     * that case fails the moment the read falls back to {@code ResourceManager.value}'s 0, which is the silent wrong
     * answer the whole declaration exists to prevent. (For a shipped character the state is unreachable: the assembly
     * point refuses a rule that names a resource its character never declares.)
     *
     * <p>The owner part is the same case {@code SelfAttributeConditionTest} pins for attributes: a rule on the
     * charged character must not be gated by somebody else's resource, or the fastest actor's stacks would decide
     * this character's mechanics with nothing in the log to say so.
     */
    @Test
    public void theResourceConditionReadsTheOwnersValue() {
        int oneShort = fireAtCharge(2);
        int atCap = fireAtCharge(3);

        Character charged = characterWithCharge(3);
        Character empty = characterWithCharge(0);
        charged.setTriggerTable(new TriggerTable(PLAIN, List.of(chargeThresholdRule())));
        empty.setTriggerTable(new TriggerTable(PLAIN, List.of()));
        Battle battle = new Battle(List.of(charged, empty), List.of(dummy()), BattleFixture.seeded());
        battle.startBattle();
        int ownerFires = battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, empty, null, 1, 0));

        Character chargedAgain = characterWithCharge(0);
        Character fullActor = characterWithCharge(3);
        chargedAgain.setTriggerTable(new TriggerTable(PLAIN, List.of(chargeThresholdRule())));
        fullActor.setTriggerTable(new TriggerTable(PLAIN, List.of()));
        Battle mirror = new Battle(List.of(chargedAgain, fullActor), List.of(dummy()), BattleFixture.seeded());
        mirror.startBattle();
        int actorOnly = mirror.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, fullActor, null, 1, 0));

        int undeclared = fireWithUndeclaredResource();

        Assertions.assertAll("self_resource: reads the rule owner's value",
                () -> Assertions.assertEquals(0, oneShort, "threshold: one short of the cap"),
                () -> Assertions.assertEquals(1, atCap,
                        "threshold: exactly at it -- 「达到上限」 (reaching the cap) is >=, not =="),
                () -> Assertions.assertEquals(1, ownerFires,
                        "owner: the actor holds none, but the rule belongs to the charged owner"),
                () -> Assertions.assertEquals(0, actorOnly,
                        "owner: and the other way round: the actor's stacks are none of this rule's business"),
                () -> Assertions.assertEquals(0, undeclared,
                        "undeclared: cannot read it -> the condition fails, the same rule every other unreadable variable follows"));
    }

    /**
     * The wiring a table can hold is validated at construction time: the shape of the condition variable (like every
     * other condition in the DSL) and a resource declared twice in one file, because the second would silently win.
     */
    @Test
    public void theTriggerTableRefusesMalformedResourceWiring() {
        IllegalArgumentException noName = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(PLAIN, List.of(ruleWithCondition("self_resource: >= 3"))));
        IllegalArgumentException spaced = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(PLAIN, List.of(ruleWithCondition("self_resource:充 能 >= 3"))));
        IllegalArgumentException twice = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(HIMEKO, List.of(),
                        List.of(new ResourceSpec(CHARGE, 3), new ResourceSpec(CHARGE, 9))));

        Assertions.assertAll("the table refuses malformed resource wiring",
                () -> Assertions.assertTrue(noName.getMessage().contains("充能"),
                        "variable shape: the message must show the shape: " + noName.getMessage()),
                () -> Assertions.assertTrue(spaced.getMessage().contains("whitespace"),
                        "variable shape: a name with a space cannot be read back: " + spaced.getMessage()),
                () -> Assertions.assertTrue(twice.getMessage().contains("twice"),
                        "declared twice: " + twice.getMessage()));
    }

    // ==================================================================
    // 4. The assembly point refuses what the character cannot satisfy
    // ==================================================================

    /**
     * A rule that <b>reads</b> an undeclared resource is refused when the character is built, and so is a rule that
     * only <b>writes</b> one: gaining nothing, silently, is the same wrong answer.
     */
    @Test
    public void theAssemblyRefusesRulesOnUndeclaredResources() {
        TriggerTable reads = new TriggerTable(HIMEKO, List.of(chargeThresholdRule()));
        TriggerSpec gain = TriggerSpecs.rule("BREAK", null, TriggerSpecs.gainResource(CHARGE, 1));
        TriggerTable writes = new TriggerTable(HIMEKO, List.of(gain));

        CharacterException refusedRead = Assertions.assertThrows(CharacterException.class,
                () -> CharacterFactory.requireReadableResources(HIMEKO, reads));
        Assertions.assertThrows(CharacterException.class,
                () -> CharacterFactory.requireReadableResources(HIMEKO, writes));

        Assertions.assertAll("the assembly refuses a rule on an undeclared resource",
                () -> Assertions.assertTrue(refusedRead.getMessage().contains(CHARGE),
                        "read: " + refusedRead.getMessage()),
                () -> Assertions.assertTrue(refusedRead.getMessage().contains("1003.json"),
                        "read: and it must say where to declare it: " + refusedRead.getMessage()));
    }

    /**
     * The shipped character satisfies it, so the check above is not passing because it always throws - while a
     * declaration nobody reads is not an error, because the check is one-directional on purpose.
     */
    @Test
    public void theShippedCharacterPassesTheAssemblyCheck() {
        TriggerTable unread = new TriggerTable(HIMEKO,
                List.of(TriggerSpecs.rule("BREAK", null, TriggerSpecs.modifyAttr("ATTACK", 0.1, 1))),
                List.of(new ResourceSpec(CHARGE, 3)));

        Assertions.assertAll("the assembly check accepts what the character can satisfy",
                () -> Assertions.assertDoesNotThrow(
                        () -> CharacterFactory.requireReadableResources(HIMEKO, TriggerTables.of(HIMEKO)),
                        "shipped character: the check is not passing because it always throws"),
                () -> Assertions.assertDoesNotThrow(
                        () -> CharacterFactory.requireReadableResources(HIMEKO, unread),
                        "unread declaration: the check is one-directional on purpose"));
    }

    // ==================================================================
    // 5. Merging: the declarations must survive the relic-set merge
    // ==================================================================

    /**
     * {@code plus} carries the declarations across, keeps two disjoint declarations (the character's own counter and
     * the light cone's) - and refuses only a name declared on both sides, because that one would have two owners.
     *
     * <p>The dangerous shape is a character whose table has declarations but no rules of its own (a memosprite
     * summoner whose kit is all inside a relic set, say): an "is it empty?" early return that only looked at rules
     * would drop the declarations, and every {@code self_resource:} read in the merged table would then fail.
     */
    @Test
    public void mergingKeepsDeclarationsAndRefusesASecondOwner() {
        TriggerSpec gain = TriggerSpecs.rule("BREAK", null, TriggerSpecs.gainResource(CHARGE, 1));
        TriggerTable declared = new TriggerTable(HIMEKO, List.of(), List.of(new ResourceSpec(CHARGE, 3)));
        TriggerTable withRules = new TriggerTable(HIMEKO, List.of(gain));

        TriggerTable merged = declared.plus(withRules);

        TriggerTable one = new TriggerTable(HIMEKO, List.of(chargeThresholdRule()),
                List.of(new ResourceSpec(CHARGE, 3)));
        TriggerTable two = new TriggerTable(HIMEKO, List.of(chargeThresholdRule()),
                List.of(new ResourceSpec("char:1308/residual_dream", 9)));

        TriggerTable both = one.plus(two);
        IllegalArgumentException clash = Assertions.assertThrows(IllegalArgumentException.class,
                () -> one.plus(new TriggerTable(HIMEKO, List.of(chargeThresholdRule()),
                        List.of(new ResourceSpec(CHARGE, 9)))));

        Assertions.assertAll("the relic-set merge keeps declarations and refuses a second owner",
                () -> Assertions.assertEquals(List.of(new ResourceSpec(CHARGE, 3)), merged.resources(),
                        "declarations: the character's declarations, kept through the merge"),
                () -> Assertions.assertEquals(Set.of(CHARGE), merged.referencedResources(),
                        "declarations: and its referenced names too"),
                () -> Assertions.assertEquals(1, merged.ruleCount(TriggerEvent.BREAK),
                        "declarations: with the merged rule"),
                () -> Assertions.assertEquals(2, both.resources().size(),
                        "two owners: two disjoint declarations both survive: " + both.resources()),
                () -> Assertions.assertTrue(clash.getMessage().contains("one id, one owner"),
                        "two owners: only a name declared on both sides is refused: " + clash.getMessage()));
    }

    // ==================================================================
    // helpers
    // ==================================================================

    private static int fireAtCharge(int charge) {
        Character owner = characterWithCharge(charge);
        owner.setTriggerTable(new TriggerTable(PLAIN, List.of(chargeThresholdRule())));
        Battle battle = new Battle(List.of(owner), List.of(dummy()), BattleFixture.seeded());
        battle.startBattle();
        return battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, owner, null, 1, 0));
    }

    /**
     * Fires the {@code self_resource:} condition on a character that never declared the resource: the read must fail
     * the condition instead of falling back to {@code ResourceManager.value}'s 0.
     */
    private static int fireWithUndeclaredResource() {
        Character owner = CharacterFactory.create(PLAIN, LEVEL);
        TriggerSpec spec = TriggerSpecs.rule("ALLY_ATTACK", null,
                TriggerSpecs.modifyAttr("ATTACK", 0.12, 1));
        TriggerSpecs.set(spec, "when", List.of("self_resource:" + CHARGE + " >= 0"));
        owner.setTriggerTable(new TriggerTable(PLAIN, List.of(spec)));
        Battle battle = new Battle(List.of(owner), List.of(dummy()), BattleFixture.seeded());
        battle.startBattle();
        return battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, owner, null, 1, 0));
    }

    /**
     * A plain character carrying {@code charge} of charge (充能), registered exactly as the assembly point would.
     */
    private static Character characterWithCharge(int charge) {
        Character owner = CharacterFactory.create(PLAIN, LEVEL);
        owner.getResources().register(CHARGE, 3, charge);
        return owner;
    }

    /**
     * A rule gated on "充能达到上限" (charge reaching its cap).
     */
    private static TriggerSpec chargeThresholdRule() {
        return ruleWithCondition("self_resource:" + CHARGE + " >= 3");
    }

    private static TriggerSpec ruleWithCondition(String condition) {
        TriggerSpec spec = TriggerSpecs.rule("ALLY_ATTACK", null,
                TriggerSpecs.modifyAttr("ATTACK", 0.12, 1));
        TriggerSpecs.set(spec, "when", List.of(condition));
        return spec;
    }

    /**
     * Why a declaration was refused, unwrapped from whatever Gson put around the constructor's own failure.
     */
    private static String rejectionOf(String json) {
        RuntimeException wrapper = Assertions.assertThrows(RuntimeException.class,
                () -> new Gson().fromJson(json, ResourceSpec.class));
        Throwable cause = wrapper.getCause() == null ? wrapper : wrapper.getCause();
        return String.valueOf(cause.getMessage());
    }

    private static Enemy dummy() {
        return BattleFixture.monster();
    }
}
