package com.laosun.aluminium.test;

import com.laosun.aluminium.Constant;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.RelicSet;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.data.RelicTriggerTables;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.RelicType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.RelicSuit;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.utils.CharacterFactory;
import com.laosun.aluminium.utils.RelicFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Relic-set trigger rules (P10-3 follow-up): the loader, the assembly point, and the registry of
 * abilities the op vocabulary cannot express yet.
 *
 * <p>Relic set bonuses that are not plain stats are "when &lt;event&gt;, do &lt;something the engine
 * can already do&gt;", which is exactly a {@link TriggerTable}. So a set's rules live in
 * {@code resources/relic_sets/<setId>.json} — the same JSON shape as a character's — keyed by the
 * <b>worn piece count</b> they need, and the assembly point
 * ({@code CharacterFactory.effectiveTriggerTable}) merges them into the character's own table.
 *
 * <h2>What is piloted here, and why it needs a test of its own</h2>
 * <ul>
 *   <li><b>The threshold is honoured.</b> A rule filed under {@code "4"} must not apply at three
 *       pieces — the failure mode is silent (the buff simply arrives one piece early);</li>
 *   <li><b>A missing file is an empty rule set, a broken one throws.</b> The distinction is what makes
 *       a typo in a hand-written file visible at load instead of never;</li>
 *   <li><b>Nothing is silently forgotten.</b> Every ability-only bonus in the shipped
 *       {@code relic_sets.json} is either covered by a rule file or listed in
 *       {@code _unmodelled.json} with the capability it is missing. That partition is the point of
 *       this class: "no rule file written" and "somebody forgot" must not look the same.</li>
 * </ul>
 */
public class RelicTriggerTableTest {

    /** "Passerby of Wandering Cloud" — 4-piece: at battle start, +1 skill point. */
    private static final int PASSERBY = 101;
    /** "Hunter of Glacial Forest" — 4-piece: after the Ultimate, +25% CRIT DMG for 2 turns. */
    private static final int GLACIAL = 104;
    /** "Band of Sizzling Thunder" — 4-piece: on Skill, +20% ATK for 1 turn. */
    private static final int SIZZLING = 109;
    /** "Eagle of Twilight Line" — 4-piece: after the Ultimate, advance forward 25%. */
    private static final int EAGLE = 110;
    /** "Champion of Streetwise Boxing" — 4-piece: on attacking or being hit, +5% ATK, up to 5 stacks. */
    private static final int CHAMPION = 105;
    /** City of Converging Stars (planar), whose 2-piece is authorable now that {@code FOLLOW_UP} exists. */
    private static final int CONVERGING_STARS = 326;
    /** The Ashblazing Grand Duke, whose 2-piece needs the follow-up-only damage boost attribute. */
    private static final int ASHBLAZING = 115;
    /**
     * 「星如我见的领航员」 — 4-piece: a Skill/Ultimate DMG boost that stacks to 3 and loses one per turn.
     *
     * <p>Authored on 2026-09-27, the day two engine changes made it expressible at all: three scoped
     * DMG-boost attributes (which scope the +18%) and the {@code REMOVE_STACK} op (the "removes 1 stack"
     * half). Before either, the ability could only have been modelled by dropping part of its text.
     */
    private static final int NAVIGATOR = 131;
    /**
     * 「戍卫风雪的铁卫」 — its 2-piece ("Reduces DMG taken by 8%") needed a damage-taken zone the data could
     * not reach; authored on 2026-09-27 once {@code MODIFY_DAMAGE_TAKEN} existed. Its 4-piece stays
     * registered: it heals a <b>percentage of Max HP</b>, which {@code HEAL}'s fixed amount cannot express.
     */
    private static final int GUARD_OF_SNOW = 106;

    /** A set id no rule file can exist for (it is not even in {@code relic_sets.json}). */
    private static final int UNKNOWN_SET = 999_999;

    /** The cavern sets used here all have a 2-piece and a 4-piece tier. */
    private static final int TWO_PIECE = 2;
    private static final int FOUR_PIECE = 4;

    /** A character with its own trigger file, used to prove the merge keeps both sides. */
    private static final int TRIBBIE = 1403;
    /** A character with no trigger file: the merge must still yield the set's rules. */
    private static final int HIMEKO = 1003;

    private static final int STAR = 5;
    private static final int LEVEL = 15;

    /**
     * The ability-only bonuses the op vocabulary can currently express, as {@code set/threshold} keys.
     *
     * <p>Pinned because it is the deliverable, not because the engine depends on the number: a new
     * rule file (or a lost one) must show up here and in {@code _unmodelled.json} in the same change.
     */
    private static final Set<String> AUTHORED = Set.of(
            PASSERBY + "/" + FOUR_PIECE,
            GLACIAL + "/" + FOUR_PIECE,
            SIZZLING + "/" + FOUR_PIECE,
            EAGLE + "/" + FOUR_PIECE,
            CHAMPION + "/" + FOUR_PIECE,
            CONVERGING_STARS + "/" + TWO_PIECE,
            ASHBLAZING + "/" + TWO_PIECE,
            NAVIGATOR + "/" + FOUR_PIECE,
            GUARD_OF_SNOW + "/" + TWO_PIECE,
            GUARD_OF_SNOW + "/" + FOUR_PIECE);

    /**
     * How many ability-only bonuses the shipped file still cannot express.
     *
     * <p>35 ability-only bonuses in total, so this number and {@link #AUTHORED}'s size must always sum
     * to it — that sum is the invariant, the individual values are just where the line currently sits.
     * It went 28 → <b>27</b> on 2026-09-27 (set 131, once {@code REMOVE_STACK} existed), then to <b>26</b>
     * (set 106's 2-piece, once {@code MODIFY_DAMAGE_TAKEN} existed), and then to <b>25</b> when the same
     * set's 4-piece became authorable — a percentage-of-Max-HP heal, which the scaled {@code HEAL} spelling
     * made expressible.
     */
    private static final int STILL_REGISTERED = 25;

    // ==================================================================
    // 1. The shipped rule files
    // ==================================================================

    /** Every authored set file loads, declares its threshold, and carries a rule on the right event. */
    @Test
    public void theShippedRuleFilesAreLoaded() {
        assertRuleOn(PASSERBY, TriggerEvent.BATTLE_START);
        assertRuleOn(GLACIAL, TriggerEvent.ULT_CAST);
        assertRuleOn(SIZZLING, TriggerEvent.SKILL_CAST);
        assertRuleOn(EAGLE, TriggerEvent.ULT_CAST);
        // 105 is the one ability whose text is a disjunction ("attacks **or** is hit"), so it needs a
        // rule on each of the two events; both are really there, or half the ability is missing.
        assertRuleOn(CHAMPION, TriggerEvent.ALLY_ATTACK);
        assertRuleOn(CHAMPION, TriggerEvent.TAKING_HIT);

        // The provenance rule: a number has to be traceable to its document.
        TriggerTable passerby = RelicTriggerTables.of(PASSERBY).at(FOUR_PIECE);
        var rule = passerby.matching(TriggerEvent.BATTLE_START,
                new TriggerTable.TriggerContext(CharacterFactory.create(HIMEKO, 80), null, null, 0, 0))
                .getFirst();
        Assertions.assertTrue(rule.source().contains("101") && rule.source().contains("Ability51011"),
                "the source must name the set and the ability it came from: " + rule.source());
        Assertions.assertEquals("GAIN_SKILL_POINT", rule.effects().getFirst().getOp());
        Assertions.assertEquals(1.0, rule.effects().getFirst().getAmount(), 1e-9,
                "the description states 1 Skill Point literally (param is empty)");
    }

    /** A 4-piece rule file is inactive below four pieces — that is what the threshold key means. */
    @Test
    public void aRuleAppliesOnlyFromItsThresholdUpwards() {
        RelicTriggerTables.Rules rules = RelicTriggerTables.of(PASSERBY);

        Assertions.assertEquals(Set.of(FOUR_PIECE), rules.thresholds(),
                "the shipped file declares exactly the 4-piece tier");
        Assertions.assertTrue(rules.at(0).isEmpty(), "no pieces, no rules");
        Assertions.assertTrue(rules.at(3).isEmpty(), "three pieces is one short of the 4-piece bonus");
        Assertions.assertFalse(rules.at(4).isEmpty(), "four pieces satisfies it");
        Assertions.assertFalse(rules.at(6).isEmpty(), "six pieces still satisfies it");
    }

    /**
     * Every authored number survives the JSON round trip.
     *
     * <p>This is the project's recurring failure mode (see {@code ROADMAP} §4.2): a misspelled field
     * leaves a value at {@code null}/0, the file still loads, and the rule quietly does the wrong
     * thing. Here that would mean a buff with no duration (rejected at load) or a modifier of 0 (a
     * silent no-op), so the values are pinned per ability rather than derived.
     */
    @Test
    public void authoredNumbersSurviveJsonBinding() {
        EffectSpec glacial = firstEffect(GLACIAL, TriggerEvent.ULT_CAST);
        Assertions.assertEquals("MODIFY_ATTR", glacial.getOp());
        Assertions.assertEquals("CRIT_ATTACK", glacial.getAttribute());
        Assertions.assertEquals(0.25, glacial.getPercent(), 1e-9, "param #1 is 0.25");
        Assertions.assertEquals(2, glacial.getTurns(), "param #2 is 2 turns");

        EffectSpec sizzling = firstEffect(SIZZLING, TriggerEvent.SKILL_CAST);
        Assertions.assertEquals("MODIFY_ATTR", sizzling.getOp());
        Assertions.assertEquals("ATTACK", sizzling.getAttribute());
        Assertions.assertEquals(0.2, sizzling.getPercent(), 1e-9, "param #1 is 0.2");
        Assertions.assertEquals(1, sizzling.getTurns(), "param #2 is 1 turn");

        EffectSpec eagle = firstEffect(EAGLE, TriggerEvent.ULT_CAST);
        Assertions.assertEquals("ADVANCE", eagle.getOp());
        Assertions.assertEquals(0.25, eagle.getPercent(), 1e-9, "param #1 is 0.25");

        EffectSpec passerby = firstEffect(PASSERBY, TriggerEvent.BATTLE_START);
        Assertions.assertEquals("GAIN_SKILL_POINT", passerby.getOp());
        Assertions.assertEquals(1.0, passerby.getAmount(), 1e-9, "the description states a literal 1");
    }

    // ==================================================================
    // 2. The loader's contract: lazy, cached, absent = empty, broken = loud
    // ==================================================================
    /** A set with no file is an ordinary empty rule set, not an error. */
    @Test
    public void aSetWithoutAFileIsAnEmptyRuleSet() {
        Assertions.assertFalse(RelicTriggerTables.exists(UNKNOWN_SET));
        RelicTriggerTables.Rules rules = RelicTriggerTables.of(UNKNOWN_SET);
        Assertions.assertTrue(rules.isEmpty());
        Assertions.assertTrue(rules.at(FOUR_PIECE).isEmpty());
    }

    /** A set file is read once and cached; {@code clearCache} is the only way to re-read it. */
    @Test
    public void aSetFileIsReadOnceAndCached() {
        RelicTriggerTables.clearCache();
        Assertions.assertEquals(0, RelicTriggerTables.loadCount(), "a cleared cache has read nothing");

        RelicTriggerTables.of(PASSERBY);
        Assertions.assertEquals(1, RelicTriggerTables.loadCount());
        RelicTriggerTables.of(PASSERBY);
        Assertions.assertSame(RelicTriggerTables.of(PASSERBY), RelicTriggerTables.of(PASSERBY),
                "a second lookup must return the compiled table, not compile it again");
        Assertions.assertEquals(1, RelicTriggerTables.loadCount(), "…so the file is still read once");

        RelicTriggerTables.of(UNKNOWN_SET);
        Assertions.assertEquals(1, RelicTriggerTables.loadCount(),
                "asking for a set with no file must not count as a read");
    }

    /** Cumulative tiers: four pieces keeps the 2-piece rules as well. */
    @Test
    public void thresholdsAreCumulative() {
        TriggerSpec twoPieceRule = rule("BATTLE_START", gainSkillPoint(1));
        TriggerSpec fourPieceRule = rule("BATTLE_START", gainSkillPoint(2));
        Map<String, List<TriggerSpec>> file = new LinkedHashMap<>();
        file.put("2", List.of(twoPieceRule));
        file.put("4", List.of(fourPieceRule));

        RelicTriggerTables.Rules rules = RelicTriggerTables.parse(PASSERBY, file, "test-file");

        Assertions.assertEquals(1, rules.at(TWO_PIECE).ruleCount(TriggerEvent.BATTLE_START));
        Assertions.assertEquals(1, rules.at(3).ruleCount(TriggerEvent.BATTLE_START),
                "three pieces has only the 2-piece tier");
        Assertions.assertEquals(2, rules.at(FOUR_PIECE).ruleCount(TriggerEvent.BATTLE_START),
                "four pieces has both tiers: the 2-piece rule plus the 4-piece one");
        Assertions.assertEquals(2, rules.at(6).ruleCount(TriggerEvent.BATTLE_START),
                "six pieces still has both tiers");
    }

    /**
     * A malformed file is reported rather than skipped.
     *
     * <p>Each rejection here is a real way a hand-written rule file goes wrong, and none of them would
     * produce a visible symptom on its own: a bad key, a tier with no rules, a tier the set does not
     * have, an unknown event, and an event with no emitter.
     */
    @Test
    public void aMalformedFileIsRejectedInsteadOfSkipped() {
        TriggerSpec good = rule("BATTLE_START", gainSkillPoint(1));

        IllegalStateException badKey = Assertions.assertThrows(IllegalStateException.class,
                () -> RelicTriggerTables.parse(PASSERBY, Map.of("four", List.of(good)), "test-file"),
                "a piece count that is not a number must be reported");
        Assertions.assertTrue(badKey.getMessage().contains("four"), badKey.getMessage());

        Assertions.assertThrows(IllegalStateException.class,
                () -> RelicTriggerTables.parse(PASSERBY, Map.of("0", List.of(good)), "test-file"),
                "a piece count of 0 makes no sense");
        Assertions.assertThrows(IllegalStateException.class,
                () -> RelicTriggerTables.parse(PASSERBY, Map.of("4", List.of()), "test-file"),
                "a declared tier with no rules would silently do nothing");
        Assertions.assertThrows(IllegalStateException.class,
                () -> RelicTriggerTables.parse(PASSERBY, Map.of("3", List.of(good)), "test-file"),
                "set 101 has no 3-piece bonus, so a rule filed under it could never fire");

        IllegalArgumentException unknownEvent = Assertions.assertThrows(IllegalArgumentException.class,
                () -> RelicTriggerTables.parse(PASSERBY, Map.of("4", List.of(rule("NO_SUCH_EVENT",
                        gainSkillPoint(1)))), "test-file"));
        Assertions.assertTrue(unknownEvent.getMessage().contains("NO_SUCH_EVENT"),
                unknownEvent.getMessage());

        // The "declared but not emitted yet" guard, for the relic-file path. Every event the enum
        // declares is wired today (pinned by TriggerTableTest.everyDeclaredTriggerEventIsEmitted), so
        // the case is skipped rather than asserting a stale event name; the load-time rejection itself
        // is covered there, where it belongs.
        TriggerEvent unwiredEvent = Arrays.stream(TriggerEvent.values())
                .filter(event -> !event.isWired())
                .findFirst().orElse(null);
        if (unwiredEvent != null) {
            IllegalArgumentException unwired = Assertions.assertThrows(IllegalArgumentException.class,
                    () -> RelicTriggerTables.parse(PASSERBY,
                            Map.of("4", List.of(rule(unwiredEvent.value(), gainSkillPoint(1)))),
                            "test-file"));
            Assertions.assertTrue(unwired.getMessage().contains("not emitted"),
                    "a rule on an event the engine never fires must be rejected at load: "
                            + unwired.getMessage());
        }
    }

    // ==================================================================
    // 3. The assembly point
    // ==================================================================

    /** Four pieces of the set put its rules on the character; fewer pieces do not. */
    @Test
    public void theCharacterCarriesTheSetRulesOnlyWhenEnoughPiecesAreWorn() {
        Character fourPieces = CharacterFactory.create(HIMEKO, 80, true, null,
                RelicFactory.suit(PASSERBY, STAR, LEVEL));

        Assertions.assertNotNull(fourPieces.getTriggerTable(), "the trigger table is never null");
        Assertions.assertEquals(1, fourPieces.getTriggerTable().ruleCount(TriggerEvent.BATTLE_START),
                "four pieces of set 101 must bring its battle-start rule");

        Character threePieces = CharacterFactory.create(HIMEKO, 80, true, null,
                partialSuit(PASSERBY, RelicType.HEAD, RelicType.HAND, RelicType.BODY));
        Assertions.assertEquals(0, threePieces.getTriggerTable().ruleCount(TriggerEvent.BATTLE_START),
                "three pieces is one short of the 4-piece bonus");

        Character unequipped = CharacterFactory.create(HIMEKO, 80);
        Assertions.assertEquals(0, unequipped.getTriggerTable().ruleCount(TriggerEvent.BATTLE_START),
                "a character without relics behaves exactly as before relic rules existed");
        Assertions.assertSame(TriggerTables.of(HIMEKO), unequipped.getTriggerTable(),
                "…which is literally its own (empty) table, not a merged copy");
    }

    /** The merge keeps <b>both</b> sides: the character's own rules and the set's. */
    @Test
    public void theCarriedTableHasTheCharacterRulesAndTheSetRules() {
        Character tribbie = CharacterFactory.create(TRIBBIE, 80, true, null,
                RelicFactory.suit(PASSERBY, STAR, LEVEL));

        Assertions.assertEquals(2, tribbie.getTriggerTable().ruleCount(TriggerEvent.BATTLE_START),
                "1403's own trace (30 energy at battle start) plus set 101's 4-piece rule");
        Assertions.assertEquals(1, tribbie.getTriggerTable().ruleCount(TriggerEvent.ALLY_ATTACK),
                "her ALLY_ATTACK rule must survive the merge");
    }

    /** A set with no rule file contributes nothing, even at four pieces. */
    @Test
    public void aSetWithoutRulesContributesNothing() {
        Character musketeer = CharacterFactory.create(HIMEKO, 80, true, null,
                RelicFactory.suit(102, STAR, LEVEL));

        Assertions.assertSame(TriggerTables.of(HIMEKO), musketeer.getTriggerTable(),
                "set 102 has no rule file, so the character's table is untouched (no empty merge copy)");
    }

    // ==================================================================
    // 4. Nothing is silently forgotten
    // ==================================================================

    /**
     * <b>The registry guard.</b> Every ability-only relic bonus in the shipped file is either
     * <b>authored</b> (its set has a rule file with a tier at that piece count) or <b>registered</b> in
     * {@code relic_sets/_unmodelled.json} with the capability it is missing.
     *
     * <p>The counts are pinned because they <em>are</em> the deliverable: 35 ability-only bonuses,
     * {@code AUTHORED.size()} expressible with today's op vocabulary,
     * {@link #STILL_REGISTERED} not. A change on either side must be deliberate.
     */
    @Test
    public void everyAbilityOnlyBonusIsEitherAuthoredOrRegistered() {
        Set<String> authored = new LinkedHashSet<>();
        Set<String> registered = new LinkedHashSet<>();
        for (RelicTriggerTables.Unmodelled entry : RelicTriggerTables.unmodelled()) {
            registered.add(entry.setId() + "/" + entry.require());
        }

        Set<String> bothOrNeither = new LinkedHashSet<>();
        int abilityOnly = 0;
        for (RelicSet set : Constant.RELIC_SETS.values()) {
            for (RelicSet.Effect effect : set.effects()) {
                if (!effect.properties().isEmpty()) {
                    continue;
                }
                Assertions.assertTrue(effect.hasAbility(),
                        "set " + set.setId() + "'s " + effect.require() + "-piece bonus has neither stats "
                                + "nor an ability: nothing would ever apply it");
                abilityOnly++;
                String key = set.setId() + "/" + effect.require();
                boolean hasRules = RelicTriggerTables.of(set.setId()).thresholds().contains(effect.require());
                if (hasRules) {
                    authored.add(key);
                    Assertions.assertFalse(registered.contains(key),
                            key + " has a rule file AND is registered as unmodelled; one of the two is wrong");
                } else {
                    Assertions.assertTrue(registered.contains(key),
                            "set " + set.setId() + " (" + effect.ability() + ") is written with no rule file "
                                    + "and is not listed in " + RelicTriggerTables.UNMODELLED_RESOURCE
                                    + ": \"not authored\" and \"forgotten\" must not look the same");
                    bothOrNeither.add(key);
                }
            }
        }

        Assertions.assertEquals(35, abilityOnly, "the shipped file's ability-only bonuses");
        Assertions.assertEquals(AUTHORED, authored,
                "the abilities the op vocabulary can currently express; a new one means a new rule file");
        Assertions.assertEquals(STILL_REGISTERED, registered.size(),
                "the abilities registered as unmodelled; a new one means a new rule file or a new registry "
                        + "entry in the same change");
        Assertions.assertEquals(STILL_REGISTERED, bothOrNeither.size(),
                "every registered ability must be a real one");
        Assertions.assertEquals(35, authored.size() + registered.size(),
                "35 = authored + registered: the partition is the invariant, not either number alone");

        // Every registered entry must carry the reason, or the gap cannot be acted on.
        for (RelicTriggerTables.Unmodelled entry : RelicTriggerTables.unmodelled()) {
            Assertions.assertFalse(entry.reason().isBlank(),
                    "set " + entry.setId() + " ability " + entry.ability() + " is registered without a reason");
        }
    }

    /** The registry itself rejects an entry that would leave a reader without an actionable reason. */
    @Test
    public void aRegistryEntryWithoutAReasonIsRejected() {
        Map<String, List<RelicTriggerTables.Unmodelled>> entry = Map.of(String.valueOf(PASSERBY),
                List.of(new RelicTriggerTables.Unmodelled(PASSERBY, FOUR_PIECE, "Ability51011", "  ")));

        IllegalStateException blank = Assertions.assertThrows(IllegalStateException.class,
                () -> RelicTriggerTables.indexUnmodelled(entry, "test-file"));
        Assertions.assertTrue(blank.getMessage().contains("reason"), blank.getMessage());

        IllegalStateException noAbility = Assertions.assertThrows(IllegalStateException.class,
                () -> RelicTriggerTables.indexUnmodelled(Map.of(String.valueOf(PASSERBY),
                        List.of(new RelicTriggerTables.Unmodelled(PASSERBY, FOUR_PIECE, "", "because"))),
                        "test-file"));
        Assertions.assertTrue(noAbility.getMessage().contains("ability"), noAbility.getMessage());

        IllegalStateException badKey = Assertions.assertThrows(IllegalStateException.class,
                () -> RelicTriggerTables.indexUnmodelled(Map.of("passerby",
                        List.of(new RelicTriggerTables.Unmodelled(PASSERBY, FOUR_PIECE, "A", "because"))),
                        "test-file"));
        Assertions.assertTrue(badKey.getMessage().contains("not a number"), badKey.getMessage());

        Assertions.assertTrue(RelicTriggerTables.indexUnmodelled(null, "test-file").isEmpty(),
                "an absent registry is an empty list, not an error");
    }

    /**
     * The registry is not empty <b>and</b> names abilities that really exist in the data.
     *
     * <p>Guards the other direction from the partition test: a stale registry entry for an ability that
     * has since been authored (or that never existed) would make the partition look healthy while
     * hiding a gap.
     */
    @Test
    public void theRegistryNamesRealAbilities() {
        Set<String> realAbilities = new LinkedHashSet<>();
        for (RelicSet set : Constant.RELIC_SETS.values()) {
            for (RelicSet.Effect effect : set.effects()) {
                if (effect.properties().isEmpty() && effect.hasAbility()) {
                    realAbilities.add(set.setId() + "|" + effect.require() + "|" + effect.ability());
                }
            }
        }

        List<RelicTriggerTables.Unmodelled> entries = RelicTriggerTables.unmodelled();
        Assertions.assertFalse(entries.isEmpty(), "the registry must be loaded from the classpath");
        for (RelicTriggerTables.Unmodelled entry : entries) {
            Assertions.assertTrue(
                    realAbilities.contains(entry.setId() + "|" + entry.require() + "|" + entry.ability()),
                    "the registry lists " + entry.setId() + "/" + entry.require() + " " + entry.ability()
                            + ", which is not an ability-only bonus in relic_sets.json");
        }
    }

    /**
     * A 2-piece ability with no rule file is registered too, so the registry is not a 4-piece-only list.
     *
     * <p>Six of the 35 ability-only bonuses sit at the 2-piece tier (every planar ornament set's only
     * ability is one, and two cavern sets have an ability-only 2-piece tier), and missing them would hide
     * a fifth of the gap. It was eight before 326 (City of Converging Stars) and 115 (The Ashblazing
     * Grand Duke) became authorable, and <b>five</b> since 2026-09-27, when 106 (Guard of Wuthering Snow)
     * joined them — its 2-piece needed a damage-taken zone, which {@code MODIFY_DAMAGE_TAKEN} provided.
     */
    @Test
    public void theRegistryCoversTheTwoPiecesTierToo() {
        long twoPiece = RelicTriggerTables.unmodelled().stream().filter(e -> e.require() == TWO_PIECE).count();
        Assertions.assertEquals(5, twoPiece,
                "the ability-only bonuses at the 2-piece tier that are not expressible yet");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /** The single effect of the rule a set files under a threshold for an event. */
    private static EffectSpec firstEffect(int setId, TriggerEvent event) {
        Character owner = CharacterFactory.create(HIMEKO, 80);
        List<TriggerTable.CompiledRule> rules = RelicTriggerTables.of(setId).at(FOUR_PIECE)
                .matching(event, new TriggerTable.TriggerContext(owner, owner, null, 0, 0));
        Assertions.assertEquals(1, rules.size(),
                "set " + setId + " must have exactly one " + event + " rule at the 4-piece tier");
        return rules.getFirst().effects().getFirst();
    }

    private static void assertRuleOn(int setId, TriggerEvent event) {        Assertions.assertTrue(RelicTriggerTables.exists(setId), "set " + setId + " must have a rule file");
        RelicTriggerTables.Rules rules = RelicTriggerTables.of(setId);
        Assertions.assertTrue(rules.thresholds().contains(FOUR_PIECE),
                "set " + setId + "'s ability is a 4-piece one, so its rules are filed under \"4\"");
        Assertions.assertEquals(1, rules.at(FOUR_PIECE).ruleCount(event),
                "set " + setId + " must carry exactly one rule on " + event);
    }

    /** A suit wearing exactly the given slots of one set (no planar pieces). */
    private static RelicSuit partialSuit(int setId, RelicType... slots) {
        RelicSuit suit = new RelicSuit();
        for (RelicType slot : slots) {
            suit.addToSuit(RelicFactory.piece(setId, slot, STAR, LEVEL));
        }
        return suit;
    }

    private static TriggerSpec rule(String on, EffectSpec effect) {
        TriggerSpec spec = new TriggerSpec();
        set(spec, "on", on);
        set(spec, "doEffects", List.of(effect));
        set(spec, "source", "RelicTriggerTableTest");
        return spec;
    }

    private static EffectSpec gainSkillPoint(double amount) {
        EffectSpec effect = new EffectSpec();
        set(effect, "op", "GAIN_SKILL_POINT");
        set(effect, "amount", amount);
        return effect;
    }

    /**
     * Sets a private field reflectively — the trigger beans are Lombok {@code @Getter} only, and the
     * project's convention is to build them this way in tests instead of widening the production API.
     */
    private static void set(Object target, String field, Object value) {
        try {
            var declared = target.getClass().getDeclaredField(field);
            declared.setAccessible(true);
            declared.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot set " + field, e);
        }
    }
}
