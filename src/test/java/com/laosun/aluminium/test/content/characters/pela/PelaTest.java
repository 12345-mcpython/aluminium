package com.laosun.aluminium.test.content.characters.pela;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.buff.DotBuff;
import com.laosun.aluminium.buff.StateBuff;
import com.laosun.aluminium.buff.VulnerabilityBuff;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
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
 * 1106 佩拉 (Pela): the stat traces, Eidolons 1/2/4/6, both traces (追歼's next-attack boost and 痛击's boost against a
 * debuffed target), the technique's two halves, the ultimate's defence reduction and its superposition with the
 * technique, and the rule-table readings those clauses rest on.
 *
 * <p><b>Clause coverage: every section of {@code 1106_佩拉.md}</b>, plus the readings that pin the clauses no machine
 * guard can see.
 *
 * <p><b>What was added on 2026-11-02</b> (three of her six Eidolons had never been read):
 * <ul>
 *   <li><b>Eidolon 1</b> — 「敌方目标被消灭时，佩拉恢复 5 点能量」. Note the sentence names <b>no killer</b>, so the rule carries
 *       <b>no</b> {@code actor == self}: measured, an ally's kill pays her too (E0 pays nothing). The only gate is
 *       {@code !target is_ally}, so our own deaths do not pay.</li>
 *   <li><b>Eidolon 6</b> — the additional 40%-ATK instance against a debuffed target, same event and condition as the talent.</li>
 *   <li><b>Eidolon 2</b> — +10% SPD for 2 turns after the dispel.</li>
 *   <li><b>The technique's damage half</b> — 「对敌方随机单体造成等同于佩拉 80% 攻击力的冰属性伤害」 had never been written
 *       (only its defence-down half was).</li>
 * </ul>
 *
 * <p><b>「施放战技<b>解除增益效果时</b>」 became exact on 2026-11-02</b>: the engine gained {@code target_buff_count} — what a
 * dispel would <b>really</b> remove ({@code BuffManager.dispellableBuffCount()}, which shares its predicate with
 * {@code removeBuffs}) — so the trace and Eidolon 2 fire only when the target had something to strip. ⚠ The dispel rule
 * therefore has to sit <b>last</b> among the {@code SKILL_CAST} rules: conditions are evaluated per rule, in file order.
 *
 * <p><b>Eidolon 4</b> — 「施放战技时，有 100% 基础概率使敌方目标冰属性抗性降低 12%，持续 2 回合」. ⚠ Registered
 * approximation: the engine's {@code RESISTANCE_REDUCTION} is all-type, so this lowers every element's resistance, not
 * only ice.
 *
 * <p><b>Trace 痛击</b> — 「对处于负面效果的敌方目标造成的伤害提高 20%」, measured as the DELTA of the boost zone between an
 * enemy with a real debuff and one without (her own panel already carries a boost). The condition reads a DEBUFF count,
 * and a plain counter is not a debuff — so the judge puts a real {@code VulnerabilityBuff} on the enemy, and reads the
 * boost zone rather than the settled damage (a vulnerability also changes what lands, which would blur the 20%).
 *
 * <p><b>Trace 追歼</b> — 「施放战技解除增益效果时，下一次攻击造成的伤害提高 20%」: a boost that lasts until the next attack,
 * so the fixture has to give the enemy something to dispel.
 *
 * <p><b>Defence stacking</b> — both the ultimate's -40% and the technique's -20% defence reduction have to count; with
 * both sources present the two differences must add. The technique state comes from
 * {@code battle.markTechniqueUsed(owner)} BEFORE {@code startBattle()} — the engine's own entry point.
 *
 * <p><b>The two "in a negative effect" clauses and the party's effect hit rate</b> need nothing new:
 * {@code target_debuff_count} is the numerical variable that reads how many negative buffs the event's target carries,
 * and this file is its first shipped reader (both the talent's energy and the damage bonus of the trace 痛击 are gated
 * on it). What is <b>registered</b> is the Skill's "removes an enemy's buff" (the engine's {@code DISPEL} cleanses our
 * own side's debuffs; removing an <i>enemy's</i> buff is the opposite direction) and the ultimate's [通解], which is a
 * state with a 100% <b>base chance</b> (a roll the {@code APPLY_BUFF} path does not have).
 *
 * <p>Merged from the former {@code PelaClauseCoverageTest}, {@code PelaEidolonFourTest},
 * {@code PelaDefenceStackingTest}, {@code PelaTraceNextAttackTest}, {@code PelaTraceTest},
 * {@code PelaTraceHittingDebuffedTest} and {@code PelaDebuffTest}; every test, assertion and Javadoc of measurement is
 * preserved.
 */
public class PelaTest {

    private static final int PELA = 1106;
    /** The ally of the Eidolon / technique / rule fixtures (1002). */
    private static final int ALLY = 1002;
    /** The ally of the trace fixtures (1001) — a different unit from {@link #ALLY}, so both ids stay named. */
    private static final int TRACE_ALLY = 1001;
    private static final int LEVEL = 80;
    private static final int OWNER = PELA;

    // ==================================================================
    // Clause coverage
    // ==================================================================

    /**
     * 「生命值/防御力/效果抵抗 +N%」are folded in; her five ice-damage traces add up to 22.4%.
     */
    @Test
    public void theStatTracesAreFoldedIntoThePanel() {
        Character pela = CharacterFactory.create(PELA, 80);
        Assertions.assertEquals(0.224, pela.getAttribute(AttributeType.ICE_DAMAGE_BOOST).get(), 1e-9,
                "3.20 + 3.20 + 4.80 + 4.80 + 6.40 = 22.4% of ice damage");
        Assertions.assertEquals(0.1, pela.getAttribute(AttributeType.EFFECT_HIT_RATE).get(), 1e-9,
                "the +4% and +6% effect-hit traces");
        Assertions.assertEquals(546.84 * 1.18, pela.getAttribute(AttributeType.ATTACK).get(), 1e-6,
                "ATK: the Lv80 base 546.84 plus the +4/+6/+8% attack traces");
    }

    /**
     * Eidolon 1: 「敌方目标被消灭时，佩拉恢复 5 点能量」 — and the killer is <b>not</b> part of the sentence.
     *
     * <p>Measured: at E1 an <b>ally's</b> kill pays her 5 (at E0 it pays nothing), and the same fixture without a kill pays
     * nothing — so the reading is about the kill, not about the fixture.
     */
    @Test
    public void eidolon1PaysForAnyEnemyKill() {
        Fixture e1 = new Fixture(1);
        double before = e1.pela.getCurrentEnergy();
        e1.killBy(e1.ally);
        Assertions.assertEquals(5.0, e1.pela.getCurrentEnergy() - before, 1e-9,
                "「敌方目标被消灭时」 — no `actor == self`: somebody else's kill pays her too");

        Fixture e0 = new Fixture(0);
        double e0Before = e0.pela.getCurrentEnergy();
        e0.killBy(e0.ally);
        Assertions.assertEquals(0.0, e0.pela.getCurrentEnergy() - e0Before, 1e-9,
                "control: it is Eidolon 1");

        Fixture none = new Fixture(1);
        double noneBefore = none.pela.getCurrentEnergy();
        none.battle.processRequests();
        Assertions.assertEquals(0.0, none.pela.getCurrentEnergy() - noneBefore, 1e-9,
                "control: nothing died");
    }

    /**
     * The other half of Eidolon 1's gate: our own deaths must not pay, which is what {@code !target is_ally} is for.
     *
     * <p>Structural rather than behavioural: a dead unit's own table is skipped, so killing Pela herself cannot show it.
     */
    @Test
    public void eidolon1ExcludesOurOwnDeaths() throws IOException {
        JsonObject rule = rule("eidolon1_energy_on_an_enemy_kill");
        Assertions.assertTrue(rule.getAsJsonArray("when").toString().contains("!target is_ally"),
                "the gate must be `!target is_ally` (not `target is_enemy`, which the vocabulary does not have)");
        Assertions.assertFalse(rule.getAsJsonArray("when").toString().contains("actor == self"),
                "and there must be NO killer gate: 「敌方目标被消灭时」 says nothing about who killed it");
    }

    /**
     * Eidolon 6: 「施放攻击后，若敌方目标处于负面效果，则对其造成…40% 攻击力的冰属性附加伤害」.
     *
     * <p>Measured with the engine's own {@code ALLY_ATTACK} announcement on a clean target: the extra instance is
     * <b>126.01593746437902</b> at E6 with a DOT on the enemy and exactly <b>0</b> at E0 or with no DOT. (A real basic
     * attack was measured first, but its own weakness break debuffs the target on impact — which makes the "no debuff"
     * half impossible to build that way, and is exactly why the announcement is fired directly here.)
     */
    @Test
    public void eidolon6PaysOnlyAgainstADebuffedTarget() {
        Fixture e6 = new Fixture(6);
        e6.enemy.getBuffManager().addBuff(new DotBuff(e6.pela, DamageElement.FIRE, 1, 99));
        double before = e6.enemy.getCurrentHp();
        e6.announceAttack();
        Assertions.assertEquals(126.01593746437902, before - e6.enemy.getCurrentHp(), 1e-6,
                "40% of her ATK, settled as an ADDITIONAL instance");

        Fixture clean = new Fixture(6);
        double cleanBefore = clean.enemy.getCurrentHp();
        clean.announceAttack();
        Assertions.assertEquals(0.0, cleanBefore - clean.enemy.getCurrentHp(), 1e-9,
                "control: no negative effect ⇒ 「若敌方目标处于负面效果」 is false");

        Fixture e0 = new Fixture(0);
        e0.enemy.getBuffManager().addBuff(new DotBuff(e0.pela, DamageElement.FIRE, 1, 99));
        double e0Before = e0.enemy.getCurrentHp();
        e0.announceAttack();
        Assertions.assertEquals(0.0, e0Before - e0.enemy.getCurrentHp(), 1e-9,
                "control: it is Eidolon 6");
    }

    /**
     * Eidolon 2: 「施放战技解除增益效果时，速度提高 10%」 — and the "when it really dispelled" gate is now exact.
     *
     * <p>Measured three ways: target carrying a dispellable buff ⇒ 105 → <b>115.5</b> (and the buff is gone); nothing to
     * dispel ⇒ <b>105</b> (the clause does not fire); E1 ⇒ 105 even with a buff.
     */
    @Test
    public void eidolon2NeedsSomethingToDispel() {
        Fixture e2 = new Fixture(2);
        e2.enemy.getBuffManager().addBuff(new StateBuff("敌方增益", 2));
        e2.castSkill();
        Assertions.assertEquals(115.50000000000001, e2.pela.getAttribute(AttributeType.SPEED).get(), 1e-9,
                "105 × 1.1 — 「解除增益效果时」 and the target really had one");
        Assertions.assertTrue(e2.enemy.getBuffManager().activeBuffs().isEmpty(), "...and the skill did dispel it");

        Fixture nothing = new Fixture(2);
        nothing.castSkill();
        Assertions.assertEquals(105.0, nothing.pela.getAttribute(AttributeType.SPEED).get(), 1e-9,
                "nothing to dispel ⇒ the clause does not fire (before 2026-11-02 this landed anyway)");

        Fixture e1 = new Fixture(1);
        e1.enemy.getBuffManager().addBuff(new StateBuff("敌方增益", 2));
        e1.castSkill();
        Assertions.assertEquals(105.0, e1.pela.getAttribute(AttributeType.SPEED).get(), 1e-9,
                "control: it is Eidolon 2");
    }

    /**
     * The trace 追歼 reads the same gate: 「施放战技解除增益效果时，下一次攻击造成的伤害提高 20%」.
     *
     * <p>Measured: +0.2 with a buff to dispel, +0.0 without. ({@code theSkillArmsTheNextAttackOnly} below holds the
     * "spent by the next attack" half.)
     */
    @Test
    public void theTraceNeedsSomethingToDispel() {
        Fixture withBuff = new Fixture(0);
        withBuff.enemy.getBuffManager().addBuff(new StateBuff("敌方增益", 2));
        withBuff.castSkill();
        Assertions.assertEquals(0.2, withBuff.pela.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get(), 1e-9,
                "「下一次攻击造成的伤害提高 20%」");

        Fixture nothing = new Fixture(0);
        nothing.castSkill();
        Assertions.assertEquals(0.0, nothing.pela.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get(), 1e-9,
                "nothing to dispel ⇒ no boost");
    }

    /**
     * The technique's two halves: 「对敌方随机单体造成 80% 攻击力的冰伤」 + 「防御力降低 20%」.
     *
     * <p>Measured: the enemy starts at <b>16498.2960225</b> and takes <b>216.634139685956</b> at battle start, and its
     * DEFENCE falls from 1100 to <b>880</b> (exactly −20%).
     */
    @Test
    public void theTechniqueLandsBothHalves() {
        Fixture tech = new Fixture(0, true);
        Assertions.assertEquals(216.634139685956, tech.enemy.getMaxHp() - tech.enemy.getCurrentHp(), 1e-6,
                "「对敌方随机单体造成等同于佩拉 80% 攻击力的冰属性伤害」");
        Assertions.assertEquals(879.9999600000001, tech.enemy.getAttribute(AttributeType.DEFENCE).get(), 1e-6,
                "「防御力降低 20%」 — 1100 × 0.8");

        Fixture plain = new Fixture(0);
        Assertions.assertEquals(plain.enemy.getCurrentHp(), plain.enemy.getMaxHp(), 1e-6,
                "control: without the technique nothing is taken and nothing is lowered");
        Assertions.assertEquals(1099.9999500000001, plain.enemy.getAttribute(AttributeType.DEFENCE).get(), 1e-6,
                "control: the defence is untouched");
    }

    // ==================================================================
    // Eidolon 4 and the defence sources
    // ==================================================================

    @Test
    public void theSkillLowersTheTargetsResistance() {
        double atThree = enemyResistance(3);
        double atFour = enemyResistance(4);
        System.out.println("[1106] enemy RES: E3 " + atThree + " ; E4 " + atFour);
        Assertions.assertEquals(0.12, atFour - atThree, 1e-9, "「冰属性抗性降低 12%」");
    }

    /**
     * Both sources present means the two differences add.
     */
    @Test
    public void bothSourcesAreCounted() {
        double plain = read(false, false);
        double techniqueOnly = read(true, false);
        double ultOnly = read(false, true);
        double both = read(true, true);

        Assertions.assertTrue(Math.abs(techniqueOnly - plain) > 1e-9,
                "the technique's share lands (" + plain + " -> " + techniqueOnly + ")");
        Assertions.assertTrue(Math.abs(ultOnly - plain) > 1e-9,
                "and the ult's lands on its own (" + plain + " -> " + ultOnly + ")");
        Assertions.assertEquals((techniqueOnly - plain) + (ultOnly - plain), both - plain, 1e-6,
                "with both present the two differences must ADD: (" + techniqueOnly + " - " + plain + ") + ("
                        + ultOnly + " - " + plain + ") vs (" + both + " - " + plain + ")");
    }

    // ==================================================================
    // The traces
    // ==================================================================

    @Test
    public void thePartyGainsTenPercentEffectHit() {
        Character pela = CharacterFactory.create(PELA, LEVEL);
        Character ally = CharacterFactory.create(TRACE_ALLY, LEVEL);
        Battle battle = new Battle(new ArrayList<>(List.of(pela, ally)),
                new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        Assertions.assertTrue(ally.getAttribute(AttributeType.EFFECT_HIT_RATE).get() > 0,
                "「佩拉在场时，我方全体的效果命中提高 10%」");
    }

    @Test
    public void onlyADebuffedEnemyPaysTheExtraBoost() {
        double bare = boostArea(false);
        double debuffed = boostArea(true);
        System.out.println("[1106] boost zone " + bare + " -> " + debuffed);
        Assertions.assertEquals(0.2, debuffed - bare, 1e-6, "「对处于负面效果的敌方目标造成的伤害提高 20%」");
    }

    @Test
    public void theSkillArmsTheNextAttackOnly() {
        Character pela = CharacterFactory.create(PELA, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(pela, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        // Note: the sentence is 「施放战技**解除增益效果时**」 — so the fixture has to give the enemy something to dispel.
        // Before 2026-11-02 the rule fired on every Skill (the engine had no way to ask), which is why this test did not
        // need the buff; now the condition is real (`theTraceNeedsSomethingToDispel` above holds the "nothing to dispel" half).
        enemy.getBuffManager().addBuff(new com.laosun.aluminium.buff.StateBuff("敌方增益", 2));
        double before = pela.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();

        battle.castImmediate(pela.getSkills().get(SkillType.SKILL), pela, List.of(enemy));
        double armed = pela.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        System.out.println("[1106] boost " + before + " -> " + armed);
        Assertions.assertEquals(0.2, armed - before, 1e-9, "「下一次攻击造成的伤害提高 20%」");

        // Spend it: the lifetime is `next_attack`, so one attack later it is gone.
        battle.castImmediate(pela.getSkills().get(SkillType.COMMON), pela, List.of(enemy));
        battle.processRequests();
        Assertions.assertEquals(before, pela.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get(), 1e-9,
                "the boost was spent on that attack");
    }

    // ==================================================================
    // The rules themselves
    // ==================================================================

    /**
     * Note: Both "in a negative effect" clauses are gated on the target's debuff count.
     */
    @Test
    public void theDebuffConditionsAreOnTheRules() {
        TriggerTable table = TriggerTables.of(PELA);
        Assertions.assertEquals(2, table.ruleCount(TriggerEvent.ALLY_ATTACK),
                "the talent's energy trace, plus (2026-11-02) Eidolon 6's additional damage — same event, same condition, "
                        + "different Eidolon gate, so they are two rules rather than one");
        Assertions.assertEquals(1, table.ruleCount(TriggerEvent.DEALING_DAMAGE), "trace Bash (痛击)");

        // Note: Two separate fixtures: adding a DOT is not undone, so reusing one enemy would leave the "no debuff" check
        // looking at a debuffed target.
        TriggerFixture debuffed = new TriggerFixture();
        Assertions.assertFalse(table.matching(TriggerEvent.DEALING_DAMAGE, debuffed.ctx(true)).isEmpty(),
                "with a debuff on the target, Bash (痛击) matches");
        TriggerFixture clean = new TriggerFixture();
        Assertions.assertTrue(table.matching(TriggerEvent.DEALING_DAMAGE, clean.ctx(false)).isEmpty(),
                "Note: with none it does not: 「to an enemy target in a **negative effect**」");
    }

    /**
     * trace The Secret Strategy (秘策) hands the whole side the effect-hit rate, and the file says so.
     */
    @Test
    public void herTraceRaisesThePartysEffectHitRate() {
        TriggerFixture f = new TriggerFixture();
        Assertions.assertTrue(f.ally.getAttribute(AttributeType.EFFECT_HIT_RATE).get() > 0,
                "「while Pela is on the field, the effect hit rate of all of our side is increased by 10%」");
        Assertions.assertEquals(4, TriggerTables.of(PELA).ruleCount(TriggerEvent.BATTLE_START) - 1,
                "census: the Secret Strategy (秘策) rule, the level-convention rule, and (2026-09-29) the technique's "
                        + "「defence reduced by 20%」 which rolls a base chance");
    }

    /**
     * Note: The direction is the whole point: her Skill strips an enemy's <b>benefit</b> (a timed shield) and leaves the
     * <b>negative</b> effects it carries alone.
     */
    @Test
    public void herSkillStripsABenefitAndNotADebuff() {
        TriggerFixture f = new TriggerFixture();
        f.enemy.getBuffManager().addBuff(new com.laosun.aluminium.buff.StateBuff("测试增益", 2));
        f.enemy.getBuffManager().addBuff(new com.laosun.aluminium.buff.DotBuff(f.pela,
                com.laosun.aluminium.enums.DamageElement.FIRE, 10, 2));
        Assertions.assertTrue(f.enemy.getBuffManager().hasState("测试增益"), "precondition: the enemy has a shield");
        Assertions.assertTrue(f.enemy.getBuffManager().hasState("dot:burn"), "precondition: and a DOT");

        f.battle.castImmediate(f.pela.getSkills().get(com.laosun.aluminium.enums.SkillType.SKILL), f.pela,
                List.of(f.enemy));

        Assertions.assertFalse(f.enemy.getBuffManager().hasState("测试增益"),
                "「removes 1 buff effect from the designated single enemy」 -- the shield went");
        Assertions.assertTrue(f.enemy.getBuffManager().hasState("dot:burn"),
                "Note: ...and the DOT stayed: REMOVE_BUFF is the mirror of DISPEL, not a second DISPEL");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static JsonObject rule(String id) throws IOException {
        for (var element : rules()) {
            JsonObject candidate = element.getAsJsonObject();
            if (candidate.has("id") && id.equals(candidate.get("id").getAsString())) {
                return candidate;
            }
        }
        throw new AssertionError("no rule " + id);
    }

    private static JsonArray rules() throws IOException {
        Path file = Path.of("src", "main", "resources", "game", "characters", "1106.json");
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonArray("rules");
    }

    private static double enemyResistance(int eidolon) {
        Character her = CharacterFactory.create(1106, 80, true, null, null, eidolon);
        Battle battle = new Battle(new ArrayList<>(List.of(her)),
                new ArrayList<>(List.of(BattleFixture.dummy())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        var enemy = battle.enemyUnits().getFirst();
        battle.castImmediate(her.getSkills().get(SkillType.SKILL), her, List.of(enemy));
        battle.processRequests();
        return enemy.getAttribute(AttributeType.RESISTANCE_REDUCTION).get();
    }

    /**
     * The 痛击 delta, read off the boost zone (both former copies of this helper were identical and are unified here).
     */
    private static double boostArea(boolean debuffed) {
        Character pela = CharacterFactory.create(PELA, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(pela, CharacterFactory.create(TRACE_ALLY, LEVEL))),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        if (debuffed) {
            enemy.getBuffManager().addBuff(new VulnerabilityBuff(1, 0.01));
        }
        Damage damage = new Damage(pela, enemy, DamageElement.ICE, DamageType.NORMAL, 100);
        battle.applyDamage(enemy, damage);
        return damage.breakdown().getOrDefault("BoostArea", 0.0);
    }

    private static double read(boolean usedTechnique, boolean castUlt) {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(owner), List.of(enemy), BattleFixture.seeded());
        if (usedTechnique) {
            battle.markTechniqueUsed(owner);
        }
        battle.startBattle();
        battle.processRequests();
        if (castUlt) {
            battle.fire(TriggerPayload.Simple.of(TriggerEvent.ULT_CAST, owner, owner, 0, 0));
            battle.processRequests();
        }
        return enemy.getAttribute(AttributeType.DEFENCE).get();
    }

    private static Random fixed() {
        return BattleFixture.alwaysRoll(0.0);
    }

    private static final class Fixture {
        private final Character pela;
        private final Character ally = CharacterFactory.create(ALLY, 80);
        private final Enemy enemy = BattleFixture.enemy(BattleFixture.MONSTER_ID, BattleFixture.MONSTER_LEVEL);
        private final Battle battle;

        private Fixture(int eidolon) {
            this(eidolon, false);
        }

        private Fixture(int eidolon, boolean technique) {
            pela = CharacterFactory.create(PELA, 80, false, null, null, eidolon);
            battle = new Battle(List.of(pela, ally), List.of(enemy), BattleFixture.seeded());
            if (technique) {
                battle.markTechniqueUsed(pela);
            }
            battle.startBattle();
            battle.processRequests();
        }

        private void killBy(Character killer) {
            battle.applyTrueDamage(killer, enemy, DamageElement.ICE, enemy.getMaxHp() * 2);
            battle.processRequests();
        }

        private void announceAttack() {
            battle.fire(TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, pela, enemy, 0, 0));
            battle.processRequests();
        }

        private void castSkill() {
            battle.castImmediate(pela.getSkills().get(SkillType.SKILL), pela, List.of(enemy));
            battle.processRequests();
        }
    }

    /**
     * The rule-table fixture: one battle, and the rule's context with the target carrying a debuff or not.
     */
    private static final class TriggerFixture {
        private final Character pela = CharacterFactory.create(PELA, LEVEL);
        private final Character ally = CharacterFactory.create(ALLY, LEVEL);
        private final Enemy enemy = BattleFixture.monster();
        private final Battle battle = new Battle(List.of(pela, ally), List.of(enemy), fixed());

        private TriggerFixture() {
            battle.startBattle();
        }

        /**
         * The rule's context, with the target carrying a debuff or not.
         */
        private TriggerTable.TriggerContext ctx(boolean debuffed) {
            if (debuffed) {
                enemy.getBuffManager().addBuff(new com.laosun.aluminium.buff.DotBuff(pela,
                        com.laosun.aluminium.enums.DamageElement.FIRE, 10, 2));
            }
            return new TriggerTable.TriggerContext(pela, pela, enemy, 0, 0, null, battle);
        }
    }
}
