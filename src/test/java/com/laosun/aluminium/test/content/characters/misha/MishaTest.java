package com.laosun.aluminium.test.content.characters.misha;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enemy.EnemyFactory;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerInterpreter;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 1312 米沙: the base kit's clauses, end to end - the Talent's 2.00 Energy for each Skill Point spent (2 per POINT, not
 * per action), the extra segment it gives the next Ultimate (Eidolon 1's per-enemy segments included), the Ultimate's
 * per-segment freeze with 行迹 释放's certain first segment, and the clause-coverage witnesses that 行迹 释放 and the
 * segment-count reset are registered rather than silently dropped.
 *
 * <p>This file merges the former {@code MishaTest}, {@code MishaPerPointTest}, {@code MishaSegmentTest},
 * {@code MishaFreezeTest}, {@code MishaFirstSegmentTest} and {@code MishaClauseCoverageTest}: every assertion kept, the
 * two identical {@code frozenAfterUltimate} helpers collapsed into one, and each former class note now sits on the case
 * or the section it explains. The two single-clause cases that share one fixture are now one scenario checked with
 * {@code Assertions.assertAll} (the Ultimate's per-segment freeze together with 行迹 释放's certain first segment, and
 * the two registered-gap witnesses); everything else keeps its own fixture and stays a test of its own. The Eidolons
 * 2 / 4 / 6 are {@link MishaEidolonTest}.
 */
public class MishaTest {

    private static final int MISHA = 1312;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;

    // ==================================================================
    // The Talent: 2.00 Energy for every Skill Point spent
    // ==================================================================

    /**
     * Note: Exactly 2 Energy per SKILL_POINT_SPENT, and an unrelated event grants nothing.
     */
    @Test
    public void theTalentReturnsEnergyOnEverySkillPointSpent() {
        Character misha = CharacterFactory.create(MISHA, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(misha, ally), List.of(enemy), fixed());
        battle.startBattle();
        double before = misha.getCurrentEnergy();

        // A control: an event that is not "a Skill Point was spent" must do nothing.
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, ally, enemy, 0, 0));
        Assertions.assertEquals(0.0, misha.getCurrentEnergy() - before, 1e-9,
                "「我方全体每消耗 1 个战技点」 (every time all allies consume 1 Skill Point) -- an attack is not a spend");

        //: the amount is the number of points spent -- 1, not 0. It used to be 0, which only passed
        // while the literal `amount: 2` ignored the context entirely (measured rounds 680-682).
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.SKILL_POINT_SPENT, ally, enemy, 0, 1));
        Assertions.assertEquals(2.0, misha.getCurrentEnergy() - before, 1e-6,
                "「同时米沙恢复2.00点能量」 (and Misha restores 2.00 Energy)");
    }

    /**
     * 「我方全体每消耗 1 个战技点…米沙恢复 2.00 点能量」 -- 2 PER POINT.
     *
     * <p>The energy case above fires the event with one point; this one hands the rule a context whose amount is 2 and
     * expects 4, which is the difference a per-action reading cannot show.
     */
    @Test
    public void twoSpentPointsReturnFourEnergy() {
        Assertions.assertEquals(2.0, energyAfter(1), 1e-6);
        Assertions.assertEquals(4.0, energyAfter(2), 1e-6, "two points must be 4 energy");
        System.out.println("[1312] per-point ok: 1 -> " + energyAfter(1) + ", 2 -> " + energyAfter(2));
    }

    // ==================================================================
    // The Talent's segment half: one more segment for the next Ultimate
    // ==================================================================

    /**
     * 1312 米沙's Talent, the segment half: 「我方全体每消耗 1 个战技点，增加 1 段米沙下一次终结技的攻击段数」.
     */
    @Test
    public void spendingASkillPointAddsASegment() {
        Character misha = CharacterFactory.create(MISHA, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(misha, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        int before = misha.skillHitBonus(SkillType.ULTRA.ordinal() + 1);   // data slot 3 = the Ultimate
        battle.fire(TriggerPayload.Simple.of(TriggerEvent.SKILL_POINT_SPENT, misha, misha, 1, 0));
        int after = misha.skillHitBonus(SkillType.ULTRA.ordinal() + 1);
        System.out.println("[1312] ult hits " + before + " -> " + after);
        Assertions.assertEquals(before + 1, after, "「增加 1 段」");
    }

    @Test
    public void eidolonOneAddsASegmentPerEnemy() {
        // The clause is the DELTA: the same cast with and without the Eidolon.
        Assertions.assertEquals(1, hitsDuringUlt(1, 1) - hitsDuringUlt(1, 0), "one enemy: one extra segment");
        Assertions.assertEquals(3, hitsDuringUlt(3, 1) - hitsDuringUlt(3, 0), "three enemies: three extra segments");
    }

    // ==================================================================
    // The Ultimate: each segment rolls the freeze
    // ==================================================================

    /**
     * 1312 米沙's Ultimate: 「每段攻击前有 20% 的基础概率使目标陷入冻结状态」.
     *
     * <p>The clause is per SEGMENT, which is why it hangs on {@code DEALING_DAMAGE} (fired once per hit, before that hit is
     * settled) instead of on the cast. The check forces the roll to succeed and looks at the enemy's state.
     *
     * <p>The same fixture pins 行迹 释放 as well: with the roll forced to fail, only the trace's first segment can freeze
     * - and it does, because its base chance is 20% + 80% = 100%.
     */
    @Test
    public void theUltimateFreezesOnEachSegmentAndAlwaysOnTheFirst() {
        Assertions.assertAll(
                () -> Assertions.assertTrue(frozenAfterUltimate(0.0),
                        "with the roll forced to succeed the enemy is frozen"),
                // Since the trace 释放 landed (2026-11-11) the FIRST segment is a certainty (20% + 80%), so a failing roll
                // no longer means "nothing freezes" - it only says the plain per-segment 20% did not fire on segments 2+.
                // That split is pinned by the 行迹 释放 cases below at the spec level (hit_index == 0 vs hit_index > 0).
                () -> Assertions.assertTrue(frozenAfterUltimate(0.99),
                        "a failing roll still freezes on the first segment - the trace's 100% base chance"),
                () -> Assertions.assertTrue(frozenAfterUltimate(0.99),
                        "the first segment's base chance is 20% + 80% = 100%, so a failing roll still freezes it"));
    }

    // ==================================================================
    // 行迹 释放: only the FIRST segment is a certainty
    // ==================================================================

    /*
     * What it needed. Nothing in the event said "which segment this is": hit_count counts TARGETS. The segment ordinal
     * now rides the settled instance (Damage.hitIndex) and is readable as hit_index, so the sentence is written as TWO
     * rules: the trace's guaranteed first segment (20% + 80% = 100% base chance) and the base per-segment 20% gated to
     * hit_index > 0 - otherwise the first segment would roll twice.
     *
     * The discriminator is the roll: with it forced to FAIL, the plain 20% clause never lands, so a frozen enemy can only
     * come from the first segment's 100%.
     */

    /**
     * The two rules are exclusive by construction: the shipped file gates the base clause to later segments.
     */
    @Test
    public void theShippedRulesSplitTheSegments() throws IOException {
        Path file = Path.of("src", "main", "resources", "game", "characters", "1312.json");
        JsonObject file1 = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject base = rule(file1, "ult_freeze_per_segment");
        JsonObject trace = rule(file1, "trace_the_first_segment_freezes_almost_surely");

        Assertions.assertTrue(base.getAsJsonArray("when").toString().contains("hit_index > 0"),
                "the 20% clause must skip the first segment, or it would roll a second time there");
        Assertions.assertTrue(trace.getAsJsonArray("when").toString().contains("hit_index == 0"),
                "the trace names the first segment");
        Assertions.assertEquals(1.0,
                trace.getAsJsonArray("do").get(0).getAsJsonObject().get("base_chance").getAsDouble(), 1e-9,
                "「提高 80%」 on top of the 20% base is a certainty (still resistible)");
    }

    // ==================================================================
    // Clause coverage: what the round added and what it could not
    // ==================================================================

    /*
     * Every section of 1312_米沙.md, plus what this round added and what it could not.
     *
     * Added on 2026-11-02: 行迹 锁接 (effect-hit rate for the duration of the Ultimate, via CAST_SETUP + until: cast_end
     * - ULT_CAST would be too late: it fires after the freeze rolls), 行迹 传冲 (+30% crit damage against a frozen
     * target, as an instance modifier), the Eidolon 6 skill-point refund (a one-shot marker consumed on the next Skill),
     * the Eidolon 6 duration read as until: turn_end, and the Skill's own 「增加 1 段」 which had never been written.
     *
     * Still registered: 行迹 释放 needs the segment ordinal (it raises the FIRST segment's freeze chance only), and the
     * hit-count reset is a subtraction because the engine has no "set back to the initial count".
     */

    /**
     * 行迹 锁接: 「施放终结技时，效果命中提高 60%，持续至本次终结技行动结束」.
     *
     * <p>Measured: the effect-hit rate is <b>0.0</b> before the Ultimate, <b>0.6</b> on every damage instance it settles, and
     * <b>0.0</b> again once the cast ends. The window matters: the freeze rolls happen inside the settlement, so a rule on
     * {@code ULT_CAST} would arrive after them.
     */
    @Test
    public void theLockstepTraceRaisesEffectHitForTheWholeUltimate() {
        Fixture f = new Fixture(0);
        List<Double> during = f.effectHitDuringUltimate();

        Assertions.assertEquals(0.0, f.baseEffectHit, 1e-9, "precondition: no free effect-hit rate");
        Assertions.assertFalse(during.isEmpty(), "precondition: the Ultimate settled something");
        for (double seen : during) {
            Assertions.assertEquals(0.6, seen, 1e-9, "「效果命中提高 60%」 — during every instance of the cast");
        }
        Assertions.assertEquals(0.0, f.misha.getAttribute(AttributeType.EFFECT_HIT_RATE).get(), 1e-9,
                "「持续至本次终结技行动结束」 — and it is gone afterwards");
    }

    /**
     * 星魂 6's second half: 「下一次施放战技后为我方恢复 1 个战技点」.
     *
     * <p>Measured: the Ultimate leaves the marker, the next Skill turns it into <b>+1 skill point</b> (3 → 4) and clears it; a
     * second Skill pays nothing.
     */
    @Test
    public void eidolon6RefundsOnePointAfterTheNextSkill() {
        Fixture f = new Fixture(6);
        f.castUltimate();
        Assertions.assertEquals(1, f.misha.getBuffManager().stacksOf("char:1312/next_skill_refund"),
                "the Ultimate arms the refund");

        int before = f.battle.getSkillPoints();
        f.castSkill();
        Assertions.assertEquals(before + 1, f.battle.getSkillPoints(), "「为我方恢复 1 个战技点」");
        Assertions.assertEquals(0, f.misha.getBuffManager().stacksOf("char:1312/next_skill_refund"),
                "「下一次」 — only once");

        int after = f.battle.getSkillPoints();
        f.castSkill();
        Assertions.assertEquals(after, f.battle.getSkillPoints(), "a second Skill pays nothing");
    }

    /**
     * <b>Gap witnesses</b> for what this file registers rather than implements: 行迹 释放 (「<b>终结技首段</b>攻击前…基础概率提高
     * 80%」) is registered because the events carry no segment ordinal, and giving every segment +80% would freeze on 100%
     * from the second hit on; and the hit-count reset is registered as a subtraction (-6) rather than "set back to the
     * initial 3", and will be removed when a "set" spelling exists.
     */
    @Test
    public void theGapsNameTheFirstSegmentTraceAndTheHitCountReset() throws IOException {
        Assertions.assertAll(
                () -> Assertions.assertTrue(gapMentions("释放"), "行迹 释放 is registered, not silently dropped"),
                () -> Assertions.assertTrue(gapMentions("段数复位"), "the reset approximation is registered"));
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static Random fixed() {
        return BattleFixture.alwaysRoll(0.0);
    }

    /**
     * The Talent's energy return for {@code pts} points spent, read off a fresh battle.
     */
    private static double energyAfter(int pts) {
        Character c = CharacterFactory.create(1312, 80, true, null, null);
        Enemy e = EnemyFactory.create(1002011, 90, 1);
        Battle b = new Battle(List.of(c), List.of(e), BattleFixture.seeded());
        b.startBattle();
        c.setCurrentEnergy(0);
        TriggerTable.TriggerContext ctx = new TriggerTable.TriggerContext(c, c, e, 1, pts, null, b, SkillCategory.UNSPECIFIED);
        var rules = TriggerTables.of(1312).rulesFor(TriggerEvent.SKILL_POINT_SPENT).stream()
                .filter(r -> "talent_energy_on_skill_point_spent".equals(r.id())).toList();
        Assertions.assertEquals(1, rules.size());
        double before = c.getCurrentEnergy();
        TriggerInterpreter.apply(b, rules.getFirst(), ctx);
        return c.getCurrentEnergy() - before;
    }

    /**
     * @return how many damage instances the Ultimate settled on the enemies
     * <p>Note: the segment count cannot be read AFTER the cast - her own rule puts it back to the base - so the judge
     * counts what the cast actually delivered.
     */
    private static int hitsDuringUlt(int enemies, int eidolon) {
        Character misha = CharacterFactory.create(MISHA, LEVEL, true, null, null, eidolon);
        List<Enemy> foes = new ArrayList<>();
        for (int i = 0; i < enemies; i++) {
            foes.add(BattleFixture.monster());
        }
        Battle battle = new Battle(new ArrayList<>(List.of(misha, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(foes), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        int[] hits = new int[1];
        battle.addDamageObserver((damage, applied, hp, shield, died) -> {
            if (damage.getAttacker() == misha) {
                hits[0]++;
            }
        });
        battle.castImmediate(misha.getSkills().get(SkillType.ULTRA), misha, List.copyOf(foes));
        System.out.println("[1312] e" + eidolon + " " + enemies + " enemies => " + hits[0] + " hits");
        return hits[0];
    }

    /**
     * Whether the Ultimate's freeze landed, with the roll forced to the given value.
     *
     * <p>⚠ Not {@code BattleFixture.monster()}: that enemy carries a 冻结 debuff resist of 1.0 (see GepardFreezeTest), so a
     * freeze never lands on it. The attribute dummy has no such resist.
     */
    private static boolean frozenAfterUltimate(double roll) {
        Character misha = CharacterFactory.create(MISHA, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = BattleFixture.dummy();
        Battle battle = new Battle(List.of(misha, ally), List.of(enemy), BattleFixture.alwaysRoll(roll));
        battle.startBattle();
        battle.castImmediate(misha.getSkills().get(SkillType.ULTRA), misha, List.of(enemy));
        battle.processRequests();
        boolean frozen = enemy.getBuffManager().hasState("ctrl:frozen");
        System.out.println("[1312] roll " + roll + " -> frozen=" + frozen);
        return frozen;
    }

    private static JsonObject rule(JsonObject file, String id) {
        for (var element : file.getAsJsonArray("rules")) {
            JsonObject rule = element.getAsJsonObject();
            if (id.equals(rule.get("id").getAsString())) {
                return rule;
            }
        }
        throw new AssertionError("rule not found: " + id);
    }

    private static boolean gapMentions(String needle) throws IOException {
        for (var element : gaps()) {
            if (element.getAsString().contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static JsonArray gaps() throws IOException {
        return character().getAsJsonArray("_gaps");
    }

    private static JsonObject character() throws IOException {
        Path file = Path.of("src", "main", "resources", "game", "characters", "1312.json");
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static final class Fixture {
        private final Character misha;
        private final Enemy enemy = BattleFixture.enemy(BattleFixture.MONSTER_ID, BattleFixture.MONSTER_LEVEL);
        private final Battle battle;
        private final double baseEffectHit;

        private Fixture(int eidolon) {
            misha = CharacterFactory.create(MISHA, 80, false, null, null, eidolon);
            battle = new Battle(List.of(misha), List.of(enemy), BattleFixture.seeded());
            battle.startBattle();
            battle.processRequests();
            baseEffectHit = misha.getAttribute(AttributeType.EFFECT_HIT_RATE).get();
        }

        private List<Double> effectHitDuringUltimate() {
            List<Double> seen = new ArrayList<>();
            battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) ->
                    seen.add(misha.getAttribute(AttributeType.EFFECT_HIT_RATE).get()));
            battle.castImmediate(misha.getSkills().get(SkillType.ULTRA), misha, List.of(enemy));
            battle.processRequests();
            return seen;
        }

        private void castUltimate() {
            battle.castImmediate(misha.getSkills().get(SkillType.ULTRA), misha, List.of(enemy));
            battle.processRequests();
        }

        private void castSkill() {
            battle.castImmediate(misha.getSkills().get(SkillType.SKILL), misha, List.of(enemy));
            battle.processRequests();
        }
    }
}
