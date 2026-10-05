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
 * {@code resources/relic_sets/<setId>.json} - the same JSON shape as a character's - keyed by the
 * <b>worn piece count</b> they need, and the assembly point
 * ({@code CharacterFactory.effectiveTriggerTable}) merges them into the character's own table.
 *
 * <h2>What is piloted here, and why it needs a test of its own</h2>
 * <ul>
 *   <li><b>The threshold is honoured.</b> A rule filed under {@code "4"} must not apply at three
 *       pieces - the failure mode is silent (the buff simply arrives one piece early);</li>
 *   <li><b>A missing file is an empty rule set, a broken one throws.</b> The distinction is what makes
 *       a typo in a hand-written file visible at load instead of never;</li>
 *   <li><b>Nothing is silently forgotten.</b> Every ability-only bonus in the shipped
 *       {@code relic_sets.json} is either covered by a rule file or listed in
 *       {@code _unmodelled.json} with the capability it is missing. That partition is the point of
 *       this class: "no rule file written" and "somebody forgot" must not look the same.</li>
 * </ul>
 */
public class RelicTriggerTableTest {

    /** "Passerby of Wandering Cloud" - 4-piece: at battle start, +1 skill point. */
    private static final int PASSERBY = 101;
    /** "Hunter of Glacial Forest" - 4-piece: after the Ultimate, +25% CRIT DMG for 2 turns. */
    private static final int GLACIAL = 104;
    /** "Band of Sizzling Thunder" - 4-piece: on Skill, +20% ATK for 1 turn. */
    private static final int SIZZLING = 109;
    /** "Eagle of Twilight Line" - 4-piece: after the Ultimate, advance forward 25%. */
    private static final int EAGLE = 110;
    /** "Champion of Streetwise Boxing" - 4-piece: on attacking or being hit, +5% ATK, up to 5 stacks. */
    private static final int CHAMPION = 105;
    /** City of Converging Stars (planar), whose 2-piece is authorable now that {@code FOLLOW_UP} exists. */
    private static final int CONVERGING_STARS = 326;
    /** The Ashblazing Grand Duke, whose 2-piece needs the follow-up-only damage boost attribute. */
    private static final int ASHBLAZING = 115;
    /**
     * "星如我见的领航员" - 4-piece: a Skill/Ultimate DMG boost that stacks to 3 and loses one per turn.
     *
     * <p>Authored on 2026-09-2, the day two engine changes made it expressible at all: three scoped
     * DMG-boost attributes (which scope the +18%) and the {@code REMOVE_STACK} op (the "removes 1 stack"
     * half). Before either, the ability could only have been modelled by dropping part of its text.
     */
    private static final int NAVIGATOR = 131;
    /**
     * "戍卫风雪的铁卫" - its 2-piece ("Reduces DMG taken by 8%") needed a damage-taken zone the data could
     * not reach; authored on 2026-09-2once {@code MODIFY_DAMAGE_TAKEN} existed. Its 4-piece stays
     * registered: it heals a <b>percentage of Max HP</b>, which {@code HEAL}'s fixed amount cannot express.
     */
    private static final int GUARD_OF_SNOW = 106;

    // The seven MIXED (stat + ability) planar 2-pieces authored on 2026-09-2, when `self_attr` and
    // ADVANCE made their conditional sentence expressible. Before that they were not in this test's
    // world at all -- see MIXED_STAT_AND_ABILITY.
    /** Space Sealing Station (太空封印站): ATK +12%; SPD >= 120 -> ATK +12% more. */
    private static final int SPACE_SEALING_STATION = 301;
    /** Fleet of the Ageless (不老者的仙舟): Max HP +12%; SPD >= 120 -> the whole party's ATK +8%. */
    private static final int FLEET_OF_THE_AGELESS = 302;
    /** Belobog of the Architects (筑城者的贝洛伯格): DEF +15%; effect hit rate >= 50% -> DEF +15% more. */
    private static final int BELOBOG = 304;
    /** Inert Salsotto (停转的萨尔索图): CRIT Rate +8%; current CRIT Rate >= 50% -> Ultimate and follow-up attack DMG +15%. */
    private static final int INERT_SALSOTTO = 306;
    /** Talia - Kingdom of Banditry (盗贼公国塔利亚): break effect +16%; SPD >= 145 -> break effect +20% more. */
    private static final int TALIA = 307;
    /** Sprightly Vonwacq (生命的翁瓦克): energy regeneration rate +5%; SPD >= 120 -> action advance 40% at battle start. */
    private static final int SPRIGHTLY_VONWACQ = 308;
    /** Rutilant Arena (繁星竞技场): CRIT Rate +8%; current CRIT Rate >= 0% -> basic attack and Skill DMG +20%. */
    private static final int CELESTIAL_DIFFERENTIATOR = 309;
    /** Celestial Differentiator (星体差分机): CRIT DMG +16%; CRIT DMG >= 120% -> CRIT Rate +60% until after the first attack (the first user of `until`). */
    private static final int STELLAR_DIFFERENTIATOR = 305;
    /** The Wondrous BananAmusement Park (奇想蕉乐园): CRIT DMG +16%; a summon on the field -> CRIT DMG +32% more (the first user of `self_summon_count`). */
    private static final int BANANA_PARADISE = 318;
    /** Hero of Triumphant Song (凯歌祝捷的英豪): ATK +12%; a memosprite on the field -> SPD +6%, when the memosprite attacks -> both sides' CRIT DMG +30% for 2 turns. */
    private static final int HERO_OF_TRIUMPHANT_SONG = 123;
    /** Poet of Mourning Collapse (哀歌覆国的诗人): SPD -8%; SPD before battle <110/<95 -> CRIT Rate +20%/+32%, the memosprite shares it. */
    private static final int POET = 124;
    /** Bone Collection's Serene Demesne (谧宁拾骨地): Max HP +12%; Max HP >= 5000 -> the wearer's and the memosprite's CRIT DMG +28%. */
    private static final int SERENE_DEMESNE = 319;
    /** Giant Tree of Rapt Brooding (渊思寂虑的巨树): SPD +6%; SPD >=135/180 -> the wearer's and the memosprite's healing +12%/20%. */
    private static final int RAPT_BROODING = 320;
    /** Firesmith of Lava-Forging (熔岩锻铸的火匠): Skill DMG +12%; after the Ultimate, the next attack's Fire DMG +12%. */
    private static final int FIRESMITH = 107;
    /** The Wind-Soaring Valorous (风举云飞的勇烈): CRIT Rate +6%; after a follow-up attack, Ultimate DMG +36% for 1 turn. */
    private static final int VALOROUS = 120;
    /** Hero of the Shattered World (再创天地的救世主): after a basic attack/Skill, a memosprite on the field -> the wearer's and the memosprite's Max HP +24%, the whole party's DMG +15%. */
    private static final int SHATTERED_WORLD = 127;
    /** Glamoth's Iron Cavalry Regiment (苍穹战线格拉默): ATK +12%; SPD >= 135/160 -> DMG dealt +12%/18%. */
    private static final int GLAMOTH = 311;
    /** Musketeer of Wild Wheat (野穗伴行的快枪手): SPD +6%; basic attack DMG +10%. */
    private static final int MUSKETEER = 102;
    /** Thief of Shooting Meteor (流星追迹的怪盗): break effect +16%; after breaking a weakness, restore 3 energy. */
    private static final int THIEF = 111;
    /** Pioneer Diver of Dead Waters (死水深潜的先驱): DMG dealt to an enemy affected by a negative state +12%. */
    private static final int PIONEER = 117;
    /** Scholar Lost in Erudition (识海迷坠的学者): Skill and Ultimate DMG +20%; after the Ultimate, the next Skill +25%. */
    private static final int SCHOLAR = 122;
    /** Broken Keel (折断的龙骨): effect resistance +10%; when effect resistance >= 30%, the whole party's CRIT DMG +10%. */
    private static final int BROKEN_KEEL = 310;
    /** Knight of Purity Palace (净庭教宗的圣骑士): DEF +15%; raise the shield the wearer provides by 20%. */
    private static final int KNIGHT_OF_PURITY = 103;
    /** Self-Enshrouded Recluse (自匿星芒的隐士): the shield provided is raised by 10%; 12% more, and when an ally holds a shield the wearer provided, CRIT DMG +15%. */
    private static final int RECLUSE = 128;

    /**
     * A set with a registered ability and <b>no</b> rule file - used by the "nothing to merge" case, which must
     * therefore pick a set that is still unwritten (102 was that set until it was authored on 2026-09-28).
     */
    private static final int NO_RULE_SET = 99002;

    /** A set id no rule file can exist for (it is not even in {@code relic_sets.json}). */
    private static final int UNKNOWN_SET = 999_999;

    /** The cavern sets used here all have a 2-piece and a 4-piece tier. */
    private static final int TWO_PIECE = 2;
    private static final int FOUR_PIECE = 4;

    /** A character with its own trigger file, used to prove the merge keeps both sides. */
    private static final int TRIBBIE = 1403;
    /**
     * An ordinary character with <b>no trigger rules of their own</b>, so every rule the tests below see comes
     * from the relic set: the merge must still yield the set's rules, and the counts must be the set's alone.
     *
     * <p>Note: Looked up rather than named since 2026-09-2: it was Himeko (1003) until her kit was authored, which put
     * a {@code BATTLE_START} rule of her own into the counts pinned below. See {@link TestCharacters}.
     */
    // 2026-09-30: a REAL character whose own file carries NO BATTLE_START rule (this suite counts them), so the count it
    // reads is the RELIC SET's alone. The old witness (a cid with no content file) no longer exists once every character ships.
    private static final int NO_RULES = 1402;

    private static final int STAR = 5;
    private static final int LEVEL = 15;

    /**
     * The ability-only bonuses the op vocabulary can currently express, as {@code set/threshold} keys.
     *
     * <p>Pinned because it is the deliverable, not because the engine depends on the number: a new
     * rule file (or a lost one) must show up here and in {@code _unmodelled.json} in the same change.
     *
     * <p>Spans <b>both</b> kinds of ability-bearing effect: the pure ones ({@code properties} empty) and the
     * mixed ones ({@code properties} plus an ability).
     */
    private static final Set<String> AUTHORED = Set.of("126/4", "327/2", "130/4", "317/2", "325/2", "324/2", "129/4",
            
            "312/2", "119/4",
            "303/2",
            "113/4",
            "323/2",
            "116/4",
            "117/4",
            "112/4",
            "108/4",
            "315/2",
            "313/2",
            PASSERBY + "/" + FOUR_PIECE,
            GLACIAL + "/" + FOUR_PIECE,
            SIZZLING + "/" + FOUR_PIECE,
            EAGLE + "/" + FOUR_PIECE,
            CHAMPION + "/" + FOUR_PIECE,
            CONVERGING_STARS + "/" + TWO_PIECE,
            ASHBLAZING + "/" + TWO_PIECE,
            NAVIGATOR + "/" + FOUR_PIECE,
            GUARD_OF_SNOW + "/" + TWO_PIECE,
            GUARD_OF_SNOW + "/" + FOUR_PIECE,
            // The mixed (stat + ability) effects authored on 2026-09-2with `self_attr` / `ADVANCE`:
            // seven planar 2-pieces whose conditional sentence had no spelling before.
            SPACE_SEALING_STATION + "/" + TWO_PIECE,
            FLEET_OF_THE_AGELESS + "/" + TWO_PIECE,
            BELOBOG + "/" + TWO_PIECE,
            INERT_SALSOTTO + "/" + TWO_PIECE,
            TALIA + "/" + TWO_PIECE,
            SPRIGHTLY_VONWACQ + "/" + TWO_PIECE,
            CELESTIAL_DIFFERENTIATOR + "/" + TWO_PIECE,
            // Authored on 2026-09-2once a rule could ask "do I have a summon out?"
            // (`self_summon_count`, and `target: "summon"` to address it).
            BANANA_PARADISE + "/" + TWO_PIECE,
            // Authored once a buff could end on an EVENT instead of a turn boundary (`"until"`).
            STELLAR_DIFFERENTIATOR + "/" + TWO_PIECE,
            // Authored on 2026-09-28 in the content pass over the `Writable now:` backlog: the five whose
            // every clause already had a spelling, with the numbers read from `param` (four of the registered
            // reasons carried a figure read off the English sentence instead - see each file's note).
            POET + "/" + FOUR_PIECE,
            SERENE_DEMESNE + "/" + TWO_PIECE,
            RAPT_BROODING + "/" + TWO_PIECE,
            FIRESMITH + "/" + FOUR_PIECE,
            VALOROUS + "/" + FOUR_PIECE,
            SHATTERED_WORLD + "/" + FOUR_PIECE,
            GLAMOTH + "/" + TWO_PIECE,
            MUSKETEER + "/" + FOUR_PIECE,
            THIEF + "/" + FOUR_PIECE,
            PIONEER + "/" + TWO_PIECE,
            SCHOLAR + "/" + FOUR_PIECE,
            BROKEN_KEEL + "/" + TWO_PIECE,
            // Authored once "the attacker is MY summon" became expressible (`actor == summon`) and a summon's
            // attack became an event the data can subscribe to (`SUMMON_ATTACK`).
            HERO_OF_TRIUMPHANT_SONG + "/" + FOUR_PIECE,
            // Authored on 2026-09-2once a cast event could say WHICH SIDE the unit it aimed at is on
            // (`target is_ally`): "对己方角色施放终结技/战技时" (sets 114, 118, 121) fired on every cast
            // without it, because a damaging ultimate aimed at an enemy carries a target too.
            "114/4",
            "118/4",
            "121/4",
            // Authored on 2026-09-29 (round 95) once `ally_count` existed and it was measured
            // that SUMMONED fires from a settle, so the memosprite half works.
            "321/2",
            // Authored on 2026-09-29 (round 9): `actor is_ally` covers the wearer AND their
            // memosprite, and `per_turn` supplies "每回合最多触发1次".
            "125/4",
            // Authored on 2026-09-2once a derived value could read MAX ENERGY
            // (`self_max_energy`, both as a condition and as a scale).
            "328/2",
            // Authored once a condition could ask about the PARTY, not just the owner
            // or the event (`self has_same_path_ally`).
            "314/2",
            // Authored once a condition could ask about the TARGET's weakness element
            // (`target has_weakness Fire`).
            "316/2",
            // Authored once a damage category could be named as an ATTRIBUTE
            // (`DOT_DAMAGE_BOOST`, the sibling of the follow-up one).
            "322/2",
            // Authored on 2026-09-28, the day a shield could remember WHO created it: "提供的护盾量提高 X%" is the
            // giver's own number (`AttributeType.SHIELD_BOOST`, read by `Battle.boostedShield` from the provider).
            // Note: Set 128's 4-piece ships one of its two sentences: the other one asks, per ally, whether the shield
            // that ally holds is the wearer's, and a whole-rule condition cannot say that.
            "103/4",
            "128/2",
            "128/4",
        "115/4");

    /**
     * How many ability-only bonuses the shipped file still cannot express.
     *
     * <p>The invariant is {@code PURE + MIXED = AUTHORED.size() + STILL_REGISTERED}; the individual values
     * are just where the line currently sits. It went 28 to <b>2</b> on 2026-09-2(set 131, once
     * {@code REMOVE_STACK} existed), then to <b>26</b> (set 106's 2-piece, once
     * {@code MODIFY_DAMAGE_TAKEN} existed), then to <b>25</b> when the same set's 4-piece became
     * authorable - and then to <b>4</b> when the MIXED effects joined the partition (see
     * {@link #MIXED_STAT_AND_ABILITY}); those 22 mixed entries are not a regression, they are the half of
     * the data this test could not see before (ROADMAP M-25). It went to <b>44</b> on 2026-09-28, when set 123
     * was authored (see {@link #HERO_OF_TRIUMPHANT_SONG}), and to <b>22</b> on 2026-09-28 when the shield-amount
     * family became authorable (sets 103 and 128, three entries - see {@link #KNIGHT_OF_PURITY}).
     */
    private static final int STILL_REGISTERED = 1;

    /** Ability-only bonuses: {@code properties} empty, the ability is the whole effect. */
    private static final int PURE_ABILITY_ONLY = 35;

    /**
     * Stat + ability bonuses: an unconditional {@code properties} stat <b>plus</b> an ability.
     *
     * <p>Note: <b>This half was invisible until 2026-09-2</b> (ROADMAP M-25). The partition below used to skip
     * every effect with a non-empty {@code properties}, so the common planar-ornament shape
     * ("攻击力提高 12%。当速度 >= 120 时，攻击力额外提高 12%") had its first clause applied by
     * {@code RelicSuit} and its second clause <b>silently dropped</b>, with no registry entry to say so - 
     * "no rule file written" and "somebody forgot" looked exactly alike for that shape. Pinned as its own
     * denominator so the next data update that adds one shows up here.
     */
    private static final int MIXED_STAT_AND_ABILITY = 29;

    /**
     * How many registered abilities the vocabulary <b>could</b> express today.
     *
     * <p>The registry's {@code reason} is prose, and prose cannot be checked - except for this one bit, which
     * the authors are asked to spell as a leading {@code "Writable now:"}. Pinning the count keeps the
     * backlog honest in both directions: writing one of these files must lower it deliberately, and a new
     * entry cannot quietly claim to be blocked when the capability exists.
     */
    private static final String WRITABLE_PREFIX = "Writable now:";
    private static final int WRITABLE_NOW = 0;

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
        // 105 is the one ability whose text is a disjunction ("attacks or is hit"), so it needs a
        // rule on each of the two events; both are really there, or half the ability is missing.
        assertRuleOn(CHAMPION, TriggerEvent.ALLY_ATTACK);
        assertRuleOn(CHAMPION, TriggerEvent.TAKING_HIT);

        // The provenance rule: a number has to be traceable to its document.
        TriggerTable passerby = RelicTriggerTables.of(PASSERBY).at(FOUR_PIECE);
        var rule = passerby.matching(TriggerEvent.BATTLE_START,
                new TriggerTable.TriggerContext(CharacterFactory.create(NO_RULES, 80), null, null, 0, 0))
                .getFirst();
        Assertions.assertTrue(rule.source().contains("101") && rule.source().contains("Ability51011"),
                "the source must name the set and the ability it came from: " + rule.source());
        Assertions.assertEquals("GAIN_SKILL_POINT", rule.effects().getFirst().getOp());
        Assertions.assertEquals(1.0, rule.effects().getFirst().getAmount(), 1e-9,
                "the description states 1 Skill Point literally (param is empty)");
    }

    /** A 4-piece rule file is inactive below four pieces - that is what the threshold key means. */
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
        Character fourPieces = CharacterFactory.create(NO_RULES, 80, true, null,
                RelicFactory.suit(PASSERBY, STAR, LEVEL));

        Assertions.assertNotNull(fourPieces.getTriggerTable(), "the trigger table is never null");
        Assertions.assertEquals(1, fourPieces.getTriggerTable().ruleCount(TriggerEvent.BATTLE_START),
                "four pieces of set 101 must bring its battle-start rule");

        Character threePieces = CharacterFactory.create(NO_RULES, 80, true, null,
                partialSuit(PASSERBY, RelicType.HEAD, RelicType.HAND, RelicType.BODY));
        Assertions.assertEquals(0, threePieces.getTriggerTable().ruleCount(TriggerEvent.BATTLE_START),
                "three pieces is one short of the 4-piece bonus");

        Character unequipped = CharacterFactory.create(NO_RULES, 80);
        Assertions.assertEquals(0, unequipped.getTriggerTable().ruleCount(TriggerEvent.BATTLE_START),
                "a character without relics behaves exactly as before relic rules existed");
        Assertions.assertSame(TriggerTables.of(NO_RULES), unequipped.getTriggerTable(),
                "…which is literally its own (empty) table, not a merged copy");
    }

    /** The merge keeps <b>both</b> sides: the character's own rules and the set's. */
    @Test
    public void theCarriedTableHasTheCharacterRulesAndTheSetRules() {
        Character tribbie = CharacterFactory.create(TRIBBIE, 80, true, null,
                RelicFactory.suit(PASSERBY, STAR, LEVEL));

        Assertions.assertEquals(4, tribbie.getTriggerTable().ruleCount(TriggerEvent.BATTLE_START),
                "1403's own trace (30 energy at battle start) plus set 101's 4-piece rule");
        Assertions.assertEquals(1, tribbie.getTriggerTable().ruleCount(TriggerEvent.ALLY_ATTACK),
                "her ALLY_ATTACK rule must survive the merge");
    }

    /** A set with no rule file contributes nothing, even at four pieces. */
    @Test
    public void aSetWithoutRulesContributesNothing() {
        Character wearer = CharacterFactory.create(NO_RULES, 80, true, null,
                RelicFactory.suit(NO_RULE_SET, STAR, LEVEL));

        Assertions.assertSame(TriggerTables.of(NO_RULES), wearer.getTriggerTable(),
                "set " + NO_RULE_SET + " has no rule file, so the character's table is untouched (no empty "
                        + "merge copy). It names the synthetic set 99002 (release_version \"test\"), which "
                        + "exists only in the test-side copy of relic_sets.json, so it can never be authored "
                        + "-- that is what ended the 102 -> 132 treadmill");
    }

    // ==================================================================
    // 4. Nothing is silently forgotten
    // ==================================================================

    /**
     * <b>The registry guard.</b> Every ability-bearing relic bonus in the shipped file is either
     * <b>authored</b> (its set has a rule file with a tier at that piece count) or <b>registered</b> in
     * {@code relic_sets/_unmodelled.json} with the capability it is missing.
     *
     * <p>The counts are pinned because they <em>are</em> the deliverable:
     * {@link #PURE_ABILITY_ONLY} + {@link #MIXED_STAT_AND_ABILITY} effects,
     * {@code AUTHORED.size()} expressible with today's op vocabulary,
     * {@link #STILL_REGISTERED} not. A change on either side must be deliberate.
     *
     * <p>Note: <b>Both kinds are walked, and that is the point of the 2026-09-2revision.</b> The loop used to
     * {@code continue} on any effect with a non-empty {@code properties}, which silently excluded 29
     * "stat + ability" effects - the majority shape for planar ornaments, whose conditional half was being
     * dropped without a registry entry (ROADMAP M-25). The classification is now explicit and both
     * denominators are asserted, so an effect cannot escape the partition by acquiring a stat.
     */
    @Test
    public void everyAbilityBearingBonusIsEitherAuthoredOrRegistered() {
        Set<String> authored = new LinkedHashSet<>();
        Set<String> registered = new LinkedHashSet<>();
        for (RelicTriggerTables.Unmodelled entry : RelicTriggerTables.unmodelled()) {
            registered.add(entry.setId() + "/" + entry.require());
        }

        Set<String> bothOrNeither = new LinkedHashSet<>();
        int pure = 0;
        int mixed = 0;
        for (RelicSet set : Constant.RELIC_SETS.values()) {
            for (RelicSet.Effect effect : set.effects()) {
                if (!effect.hasAbility()) {
                    Assertions.assertFalse(effect.properties().isEmpty(),
                            "set " + set.setId() + "'s " + effect.require() + "-piece bonus has neither "
                                    + "stats nor an ability: nothing would ever apply it");
                    continue;
                }
                if (effect.properties().isEmpty()) {
                    pure++;
                } else {
                    mixed++;
                }
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

        Assertions.assertEquals(PURE_ABILITY_ONLY, pure, "the shipped file's pure ability-only bonuses");
        Assertions.assertEquals(MIXED_STAT_AND_ABILITY, mixed, "the shipped file's stat + ability bonuses");
        Assertions.assertEquals(AUTHORED, authored,
                "the abilities the op vocabulary can currently express; a new one means a new rule file");
        Assertions.assertEquals(STILL_REGISTERED, registered.size(),
                "the abilities registered as unmodelled; a new one means a new rule file or a new registry "
                        + "entry in the same change");
        Assertions.assertEquals(STILL_REGISTERED, bothOrNeither.size(),
                "every registered ability must be a real one");
        Assertions.assertEquals(PURE_ABILITY_ONLY + MIXED_STAT_AND_ABILITY,
                authored.size() + registered.size(),
                "the partition is the invariant, not either number alone: pure + mixed = authored + registered");

        // Every registered entry must carry the reason, or the gap cannot be acted on.
        for (RelicTriggerTables.Unmodelled entry : RelicTriggerTables.unmodelled()) {
            Assertions.assertFalse(entry.reason().isBlank(),
                    "set " + entry.setId() + " ability " + entry.ability() + " is registered without a reason");
        }
    }

    /**
     * The registered abilities the vocabulary could express <b>today</b> are labelled and counted.
     *
     * <p>A registry entry whose missing capability has since been built is the one way this file rots: the
     * engine gains an op, and 44 entries keep saying "needs an op". So the authors spell those as a leading
     * {@code "Writable now:"} and the count is pinned - writing one of them has to lower the number on
     * purpose, and an <em>unblocked</em> ability cannot hide inside a reason nobody re-reads.
     */
    @Test
    public void theWritableBacklogIsLabelledAndCounted() {
        List<RelicTriggerTables.Unmodelled> writable = RelicTriggerTables.unmodelled().stream()
                .filter(entry -> entry.reason().startsWith(WRITABLE_PREFIX))
                .toList();

        Assertions.assertEquals(WRITABLE_NOW, writable.size(),
                "the registered abilities the current vocabulary can already express: "
                        + writable.stream().map(e -> e.setId() + "/" + e.require()).toList());
        for (RelicTriggerTables.Unmodelled entry : writable) {
            Assertions.assertFalse(
                    RelicTriggerTables.of(entry.setId()).thresholds().contains(entry.require()),
                    "set " + entry.setId() + "/" + entry.require() + " is labelled writable but has a rule file");
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
     *
     * <p>Note: Walks <b>every</b> ability-bearing effect, not just the pure ones - otherwise the 22 registered
     * mixed entries would all look stale, which is how this case failed the first time the M-25 entries
     * landed (it had the same {@code properties().isEmpty()} filter the partition test used to have).
     */
    @Test
    public void theRegistryNamesRealAbilities() {
        Set<String> realAbilities = new LinkedHashSet<>();
        for (RelicSet set : Constant.RELIC_SETS.values()) {
            for (RelicSet.Effect effect : set.effects()) {
                if (effect.hasAbility()) {
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
                            + ", which is not an ability-bearing bonus in relic_sets.json");
        }
    }

    /**
     * A 2-piece ability with no rule file is registered too, so the registry is not a 4-piece-only list.
     *
     * <p>Every planar ornament set's ability sits at the 2-piece tier, and that is where most of the mixed
     * (stat + ability) shape lives, so missing that tier would hide most of the gap. It was eight before 326
     * (City of Converging Stars) and 115 (The Ashblazing Grand Duke) became authorable, <b>five</b> since
     * 2026-09-2(set 106 joined them), and <b>22</b> once the 1mixed 2-piece abilities that no longer
     * count as invisible were registered. It is <b>11</b> since 2026-09-28, when 128's 2-piece
     * ("提供的护盾量提高10%") became authorable with the shield-amount boost.
     */
    @Test
    public void theRegistryCoversTheTwoPiecesTierToo() {
        long twoPiece = RelicTriggerTables.unmodelled().stream().filter(e -> e.require() == TWO_PIECE).count();
        Assertions.assertEquals(0, twoPiece,
                "the ability-bearing bonuses at the 2-piece tier that are not expressible yet (11 until set "
                        + "128's 2-piece was authored on 2026-09-28)");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /** The single effect of the rule a set files under a threshold for an event. */
    private static EffectSpec firstEffect(int setId, TriggerEvent event) {
        Character owner = CharacterFactory.create(NO_RULES, 80);
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
     * Sets a private field reflectively - the trigger beans are Lombok {@code @Getter} only, and the
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
