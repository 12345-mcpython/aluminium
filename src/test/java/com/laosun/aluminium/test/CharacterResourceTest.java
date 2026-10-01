package com.laosun.aluminium.test;

import com.google.gson.Gson;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.ResourceSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.exceptions.CharacterException;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * A character's <b>declared resources</b> (P8-8) and the {@code self_resource:<NAME>} condition that reads them.
 *
 * <p><b>The hole this closes.</b> Before this, {@code ResourceManager} could hold a value but no content could
 * declare one, and the condition DSL could not read one: {@code gain} answered {@code 0} for an unknown id and
 * {@code value} answered {@code 0} too, so a rule about 「充能」 was a no-op that reported nothing. 41 of the 97
 * character documents in the corpus gate something on a count, which is the largest single gap the scan found
 * (ROADMAP §13.7) — and a whole family of kits (Acheron's 【残梦】, Feixiao's 【飞黄】, Cyrene's 【追忆】) is built on
 * it.
 *
 * <p><b>Three things are pinned here</b>, each of which would otherwise be a rule that loads and quietly does the
 * wrong thing:
 * <ol>
 *   <li>the file shapes — the object form that declares resources, and the bare array that stays valid for every
 *       character with nothing but rules;</li>
 *   <li>that an <b>undeclared</b> resource fails both the read and the write, at build time, rather than reading as
 *       0;</li>
 *   <li>that {@code self_resource:} is read off the rule's <b>owner</b>, like {@code self_attr:} and unlike the
 *       event's actor.</li>
 * </ol>
 *
 * <p>The fixtures under {@code src/test/resources/characters/} are the loader's own fixtures — the same trick
 * {@code relic_sets/113.json} uses for relic rules — so the shapes are exercised through {@link TriggerTables}
 * rather than by calling Gson directly.
 */
public class CharacterResourceTest {
    private static final double EPS = 1e-6;

    /** 姬子: the shipped character whose file declares a resource. */
    private static final int HIMEKO = 1003;
    /** An ordinary character with no rules and no declarations ({@link TestCharacters}). */
    // \u2705 2026-09-30: a REAL character with no `resources` block (1002 is a bare rule list). This used to be a cid with
    // no content file at all, which no longer exists -- see TestCharacters. The assertions here are about DECLARATIONS, and
    // every one of them installs its own rule table, so the character’s own rules never enter the measurement.
    private static final int PLAIN = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final String CHARGE = "充能";

    /** The fixture cids (see {@code src/test/resources/characters/}). */
    private static final int OBJECT_FORM = 9001;
    private static final int OBJECT_WITHOUT_RULES = 9002;
    private static final int UNKNOWN_TOP_LEVEL_KEY = 9003;
    private static final int MISSPELLED_DECLARATION_KEY = 9004;
    private static final int ZERO_CAP = 9005;
    private static final int ARRAY_FORM = 9006;

    // ==================================================================
    // 1. The two file shapes
    // ==================================================================

    /** The object form: declarations and rules in one file, which is what a resource-bearing character needs. */
    @Test
    public void theObjectFormCarriesDeclarationsAndRules() {
        TriggerTable table = TriggerTables.of(OBJECT_FORM);

        Assertions.assertEquals(1, table.resources().size(), "one declaration");
        ResourceSpec declared = table.resources().getFirst();
        Assertions.assertEquals(CHARGE, declared.id());
        Assertions.assertEquals(3, declared.max(), "「上限3点」 is the cap, and it is stated, not inferred");
        Assertions.assertEquals(0, declared.initial(), "absent \"initial\" means 0");
        Assertions.assertEquals(1, table.ruleCount(TriggerEvent.BREAK), "and the rules are read from the same file");
        Assertions.assertEquals(Set.of(CHARGE), table.referencedResources(),
                "the rule names the resource, so the table knows what to check against the declarations");
    }

    /**
     * The bare array is still a valid file, and it <b>declares nothing</b>.
     *
     * <p>Worth pinning both ways: the thirteen files written before resources existed are arrays, and one of them
     * could otherwise start "declaring" a resource by accident the day a rule inside it mentions one.
     */
    @Test
    public void theArrayFormStillLoadsRulesAndDeclaresNothing() {
        TriggerTable table = TriggerTables.of(ARRAY_FORM);

        Assertions.assertTrue(table.resources().isEmpty(), "an array has nowhere to declare a resource");
        Assertions.assertEquals(1, table.ruleCount(TriggerEvent.BREAK));
        Assertions.assertEquals(Set.of(CHARGE), table.referencedResources(),
                "the rule still references one -- and that is exactly what the assembly check must catch");
    }

    /** An object with no {@code rules} is refused: the shape exists to hold both halves. */
    @Test
    public void anObjectWithoutRulesIsRefused() {
        IllegalStateException rejected = Assertions.assertThrows(IllegalStateException.class,
                () -> TriggerTables.of(OBJECT_WITHOUT_RULES));

        Assertions.assertTrue(rejected.getMessage().contains("rules"),
                "the message must name the missing half: " + rejected.getMessage());
    }

    /**
     * A key Gson would silently drop is refused at both levels.
     *
     * <p>The declaration-level case is the one that matters: {@code "intial": 1} would be read as "starts at 0",
     * a wrong number with nothing to report.
     */
    @Test
    public void unknownKeysAreRefusedRatherThanDropped() {
        IllegalStateException topLevel = Assertions.assertThrows(IllegalStateException.class,
                () -> TriggerTables.of(UNKNOWN_TOP_LEVEL_KEY));
        Assertions.assertTrue(topLevel.getMessage().contains("trigger"),
                "the misspelled \"trigger\" must be named: " + topLevel.getMessage());

        IllegalStateException inDeclaration = Assertions.assertThrows(IllegalStateException.class,
                () -> TriggerTables.of(MISSPELLED_DECLARATION_KEY));
        Assertions.assertTrue(inDeclaration.getMessage().contains("intial"),
                "so must \"intial\": " + inDeclaration.getMessage());
    }

    /** A cap that cannot hold anything is a mistake, and it is refused where the file is read. */
    @Test
    public void aZeroCapIsRefused() {
        IllegalStateException rejected = Assertions.assertThrows(IllegalStateException.class,
                () -> TriggerTables.of(ZERO_CAP));

        Assertions.assertTrue(rejected.getMessage().contains("max"),
                "the message must be about the cap: " + rejected.getMessage());
    }

    /**
     * The declaration's own validation runs on the <b>JSON</b> path, not only when built by hand.
     *
     * <p>Gson constructs records through their canonical constructor, which is the compact one — the alternative
     * would be a validator that silently never ran for real content — but it <b>wraps</b> what that constructor
     * throws, so the reason arrives as the cause. That is exactly why {@code TriggerTables} reports the deepest
     * message in the chain (see the zero-cap fixture case above, which asserts the sentence itself): a content
     * error must not be reduced to 「Failed to invoke constructor」.
     */
    @Test
    public void theDeclarationIsValidatedOnTheJsonPath() {
        ResourceSpec parsed = new Gson().fromJson("{\"id\":\"充能\",\"max\":3}", ResourceSpec.class);
        Assertions.assertEquals(CHARGE, parsed.id());
        Assertions.assertEquals(3, parsed.max());
        Assertions.assertEquals(0, parsed.initial(), "the compact constructor fills the absent initial");

        Assertions.assertTrue(rejectionOf("{\"id\":\"充能\",\"max\":0}").contains("max"), "a cap of 0");
        Assertions.assertTrue(rejectionOf("{\"id\":\"充能\"}").contains("max"), "no cap at all");
        Assertions.assertTrue(rejectionOf("{\"max\":3}").contains("id"), "no id");
        Assertions.assertTrue(rejectionOf("{\"id\":\"充 能\",\"max\":3}").contains("whitespace"),
                "a name with a space inside cannot be spelled in a condition");
        Assertions.assertTrue(rejectionOf("{\"id\":\"充能\",\"max\":3,\"initial\":4}").contains("initial"),
                "a start above the cap");
    }

    // ==================================================================
    // 2. Declared = registered on the character
    // ==================================================================

    /** The assembly point registers what the file declares, with the cap it declares. */
    @Test
    public void aDeclaredResourceIsRegisteredOnTheCharacter() {
        Character himeko = CharacterFactory.create(HIMEKO, LEVEL);

        Assertions.assertTrue(himeko.getResources().has(CHARGE), "she declares it in characters/1003.json");
        Assertions.assertEquals(0, himeko.getResources().value(CHARGE),
                "at 0: 「战斗开始时获得1点充能」 is a rule, so it is the battle that fills it");
        Assertions.assertEquals(3, himeko.getResources().get(CHARGE).getMax(), "with the declared cap");
    }

    /** And a character who declares nothing gets nothing — no resource appears out of the engine's sleeve. */
    @Test
    public void aCharacterWithoutDeclarationsHasNoResources() {
        Assertions.assertEquals(0, CharacterFactory.create(PLAIN, LEVEL).getResources().size());
    }

    // ==================================================================
    // 3. The condition: read, and read off the owner
    // ==================================================================

    /** The threshold decides, and the value is the owner's own. */
    @Test
    public void theThresholdDecidesWhetherTheRuleFires() {
        Assertions.assertEquals(0, fireAtCharge(2), "one short of the cap");
        Assertions.assertEquals(1, fireAtCharge(3), "exactly at it -- 「达到上限」 is >=, not ==");
    }

    /**
     * The resource is read off the rule's <b>owner</b>, not off whoever caused the event.
     *
     * <p>The same case {@code SelfAttributeConditionTest} pins for attributes: a rule on the charged character
     * must not be gated by somebody else's resource, or the fastest actor's stacks would decide this character's
     * mechanics with nothing in the log to say so.
     */
    @Test
    public void theResourceIsReadFromTheRulesOwnerNotFromTheActor() {
        Character charged = characterWithCharge(3);
        Character empty = characterWithCharge(0);
        charged.setTriggerTable(new TriggerTable(PLAIN, List.of(chargeThresholdRule())));
        empty.setTriggerTable(new TriggerTable(PLAIN, List.of()));
        Battle battle = new Battle(List.of(charged, empty), List.of(dummy()), new Random(0));
        battle.startBattle();

        Assertions.assertEquals(1, battle.fireTriggers(TriggerEvent.ALLY_ATTACK, empty, null, 1, 0),
                "the actor holds none, but the rule belongs to the charged owner");

        Character chargedAgain = characterWithCharge(0);
        Character fullActor = characterWithCharge(3);
        chargedAgain.setTriggerTable(new TriggerTable(PLAIN, List.of(chargeThresholdRule())));
        fullActor.setTriggerTable(new TriggerTable(PLAIN, List.of()));
        Battle mirror = new Battle(List.of(chargedAgain, fullActor), List.of(dummy()), new Random(0));
        mirror.startBattle();

        Assertions.assertEquals(0, mirror.fireTriggers(TriggerEvent.ALLY_ATTACK, fullActor, null, 1, 0),
                "and the other way round: the actor's stacks are none of this rule's business");
    }

    /**
     * ⚠ An <b>undeclared</b> resource fails the condition instead of reading as 0.
     *
     * <p>The threshold is deliberately {@code >= 0}, which <em>any</em> number would satisfy — so this test fails
     * the moment the read falls back to {@code ResourceManager.value}'s 0, which is the silent wrong answer the
     * whole declaration exists to prevent. (For a shipped character the state is unreachable: the assembly point
     * refuses a rule that names a resource its character never declares.)
     */
    @Test
    public void anUndeclaredResourceFailsTheConditionRatherThanReadingZero() {
        Character owner = CharacterFactory.create(PLAIN, LEVEL);
        TriggerSpec spec = TriggerSpecs.rule("ALLY_ATTACK", null,
                TriggerSpecs.modifyAttr("ATTACK", 0.12, 1));
        TriggerSpecs.set(spec, "when", List.of("self_resource:" + CHARGE + " >= 0"));
        owner.setTriggerTable(new TriggerTable(PLAIN, List.of(spec)));
        Battle battle = new Battle(List.of(owner), List.of(dummy()), new Random(0));
        battle.startBattle();

        Assertions.assertEquals(0, battle.fireTriggers(TriggerEvent.ALLY_ATTACK, owner, null, 1, 0),
                "cannot read it -> the condition fails, the same rule every other unreadable variable follows");
    }

    /** The shape of the variable is validated at load time, like every other condition in the DSL. */
    @Test
    public void aMalformedResourceVariableIsRejected() {
        IllegalArgumentException noName = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(PLAIN, List.of(ruleWithCondition("self_resource: >= 3"))));
        Assertions.assertTrue(noName.getMessage().contains("充能"),
                "the message must show the shape: " + noName.getMessage());

        IllegalArgumentException spaced = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(PLAIN, List.of(ruleWithCondition("self_resource:充 能 >= 3"))));
        Assertions.assertTrue(spaced.getMessage().contains("whitespace"),
                "a name with a space cannot be read back: " + spaced.getMessage());
    }

    // ==================================================================
    // 4. The assembly point refuses what the character cannot satisfy
    // ==================================================================

    /** A rule that <b>reads</b> an undeclared resource is refused when the character is built. */
    @Test
    public void aRuleThatReadsAnUndeclaredResourceIsRefusedAtAssembly() {
        TriggerTable table = new TriggerTable(HIMEKO, List.of(chargeThresholdRule()));

        CharacterException rejected = Assertions.assertThrows(CharacterException.class,
                () -> CharacterFactory.requireReadableResources(HIMEKO, table));
        Assertions.assertTrue(rejected.getMessage().contains(CHARGE), rejected.getMessage());
        Assertions.assertTrue(rejected.getMessage().contains("1003.json"),
                "and it must say where to declare it: " + rejected.getMessage());
    }

    /** So is a rule that only <b>writes</b> one: gaining nothing, silently, is the same wrong answer. */
    @Test
    public void aRuleThatGainsAnUndeclaredResourceIsRefusedAtAssembly() {
        TriggerSpec spec = TriggerSpecs.rule("BREAK", null, TriggerSpecs.gainResource(CHARGE, 1));
        TriggerTable table = new TriggerTable(HIMEKO, List.of(spec));

        Assertions.assertThrows(CharacterException.class,
                () -> CharacterFactory.requireReadableResources(HIMEKO, table));
    }

    /** The shipped character satisfies it, so the check above is not passing because it always throws. */
    @Test
    public void theShippedCharacterPassesTheAssemblyCheck() {
        Assertions.assertDoesNotThrow(
                () -> CharacterFactory.requireReadableResources(HIMEKO, TriggerTables.of(HIMEKO)));
    }

    /** A declaration nobody reads is not an error: the check is one-directional on purpose. */
    @Test
    public void aDeclaredButUnreadResourceIsAccepted() {
        TriggerTable table = new TriggerTable(HIMEKO,
                List.of(TriggerSpecs.rule("BREAK", null, TriggerSpecs.modifyAttr("ATTACK", 0.1, 1))),
                List.of(new ResourceSpec(CHARGE, 3)));
        Assertions.assertDoesNotThrow(() -> CharacterFactory.requireReadableResources(HIMEKO, table));
    }

    // ==================================================================
    // 5. Merging: the declarations must survive the relic-set merge
    // ==================================================================

    /**
     * {@code plus} carries the declarations across.
     *
     * <p>The dangerous shape is a character whose table has declarations but no rules of its own (a memosprite
     * summoner whose kit is all inside a relic set, say): an "is it empty?" early return that only looked at rules
     * would drop the declarations, and every {@code self_resource:} read in the merged table would then fail.
     */
    @Test
    public void mergingKeepsTheDeclarations() {
        TriggerSpec gain = TriggerSpecs.rule("BREAK", null, TriggerSpecs.gainResource(CHARGE, 1));
        TriggerTable declared = new TriggerTable(HIMEKO, List.of(), List.of(new ResourceSpec(CHARGE, 3)));
        TriggerTable withRules = new TriggerTable(HIMEKO, List.of(gain));

        TriggerTable merged = declared.plus(withRules);

        Assertions.assertEquals(List.of(new ResourceSpec(CHARGE, 3)), merged.resources(),
                "the character's declarations, kept through the merge");
        Assertions.assertEquals(Set.of(CHARGE), merged.referencedResources(), "and its referenced names too");
        Assertions.assertEquals(1, merged.ruleCount(TriggerEvent.BREAK));
    }

    /** Two sets of declarations have no defined owner, so the merge refuses rather than picking one. */
    @Test
    public void mergingTwoDeclaringTablesIsRefused() {
        TriggerTable one = new TriggerTable(HIMEKO, List.of(chargeThresholdRule()),
                List.of(new ResourceSpec(CHARGE, 3)));
        TriggerTable two = new TriggerTable(HIMEKO, List.of(chargeThresholdRule()),
                List.of(new ResourceSpec("残梦", 9)));

        IllegalArgumentException rejected = Assertions.assertThrows(IllegalArgumentException.class,
                () -> one.plus(two));
        Assertions.assertTrue(rejected.getMessage().contains("declare resources"), rejected.getMessage());
    }

    /** And one file declaring the same resource twice is refused, because the second would silently win. */
    @Test
    public void theSameResourceDeclaredTwiceIsRefused() {
        IllegalArgumentException rejected = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(HIMEKO, List.of(),
                        List.of(new ResourceSpec(CHARGE, 3), new ResourceSpec(CHARGE, 9))));

        Assertions.assertTrue(rejected.getMessage().contains("twice"), rejected.getMessage());
    }

    // ==================================================================
    // helpers
    // ==================================================================

    private static int fireAtCharge(int charge) {
        Character owner = characterWithCharge(charge);
        owner.setTriggerTable(new TriggerTable(PLAIN, List.of(chargeThresholdRule())));
        Battle battle = new Battle(List.of(owner), List.of(dummy()), new Random(0));
        battle.startBattle();
        return battle.fireTriggers(TriggerEvent.ALLY_ATTACK, owner, null, 1, 0);
    }

    /** A plain character carrying {@code charge} of 充能, registered exactly as the assembly point would. */
    private static Character characterWithCharge(int charge) {
        Character owner = CharacterFactory.create(PLAIN, LEVEL);
        owner.getResources().register(CHARGE, 3, charge);
        return owner;
    }

    /** A rule gated on 「充能达到上限」. */
    private static TriggerSpec chargeThresholdRule() {
        return ruleWithCondition("self_resource:" + CHARGE + " >= 3");
    }

    private static TriggerSpec ruleWithCondition(String condition) {
        TriggerSpec spec = TriggerSpecs.rule("ALLY_ATTACK", null,
                TriggerSpecs.modifyAttr("ATTACK", 0.12, 1));
        TriggerSpecs.set(spec, "when", List.of(condition));
        return spec;
    }

    /** Why a declaration was refused, unwrapped from whatever Gson put around the constructor's own failure. */
    private static String rejectionOf(String json) {
        RuntimeException wrapper = Assertions.assertThrows(RuntimeException.class,
                () -> new Gson().fromJson(json, ResourceSpec.class));
        Throwable cause = wrapper.getCause() == null ? wrapper : wrapper.getCause();
        return String.valueOf(cause.getMessage());
    }

    private static Enemy dummy() {
        return EnemyFactory.create(MONSTER, 90, 1);
    }
}
