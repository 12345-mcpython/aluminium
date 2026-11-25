package com.laosun.aluminium.test.content.characters.robin;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.DoubleValue;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.buff.StateBuff;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.skill.Skill;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1309 知更鸟 (Robin): the whole kit in one class - the 【协奏】 aura and its immunity, the two trace rules, the wave energy,
 * and Eidolons 1 / 2 / 4.
 *
 * <p><b>Why these cases belong together.</b> Everything here hangs off the same two levers: the 【协奏】 state, which the
 * Ultimate enters and which carries the aura's condition, the control immunity and the whole Eidolon group; and her own
 * trigger table, whose rules must not fire on an ally's cast. Read in one class, the state's two ends - what it grants while
 * it lasts and what leaves with it - sit next to each other.
 *
 * <p><b>The aura.</b> Her trace (行迹) "即兴装饰" (1309102): "处于[协奏]状态时，我方全体发动追加攻击造成的暴击伤害提高 25%".
 * The aura is owned by 1309 but must react to an ALLY's follow-up - which the engine already supports, because fireTriggers
 * walks the whole party. The judge makes the ALLY deal the damage, so a self-only implementation would fail it.
 *
 * <p><b>The immunity.</b> "while in the [协奏] state, Robin is immune to control-class negative states", asserted BOTH ways:
 * the same control that lands after the countdown cannot land while the state lasts, because the immunity ends with the
 * state rather than with a turn count. It also records that ULT_CAST arrives at the tables exactly once per cast (an event
 * count - it does NOT count the rules that match).
 *
 * <p><b>The traces.</b> The first character whose <b>trace extra abilities</b> are wired, and the first test of "one
 * character, several mechanics". Both rules use events and ops that already existed ({@code BATTLE_START},
 * {@code SKILL_CAST}, {@code ADVANCE}, {@code GAIN_ENERGY}) and a condition the DSL already had ({@code actor == self}), so
 * what is pinned is not "the engine learned Robin" but "content can change a battle without engine code". Every measurement
 * is taken twice: once with her real table, once with the <b>same character</b> (same cid, level, seed, roster) whose table
 * has been emptied - a number that only holds because a rule exists then cannot pass by accident. The one absolute
 * assertion is 0 (an ally's skill must not pay her), which is the guardrail for {@code actor == self}.
 *
 * <p><b>The wave energy.</b> "after entering battle while the field is expanded, Robin restores 5 energy at the start of
 * each wave", two-way on purpose: with the technique's field up she gains five at each wave's start, and without it she
 * gains nothing. The gate is the repo's own convention for "technique" ({@code self has_state sys:technique}), and it holds
 * because {@code applyTechniqueStates()} runs before any rule.
 *
 * <p><b>The Eidolons.</b> Eidolons 1 / 2 / 4: the party-wide modifiers that ride 【协奏】, the talent energy point Eidolon 2
 * adds, and Eidolon 4's control-class dispel. All three modifiers are granted on the Ultimate - the same cast that ENTERS
 * 【协奏】 - and they are grouped by that name ({@code buff: 协奏}), which is what one {@code REMOVE_STATE 协奏} takes off.
 * So each case reads the attribute on an ALLY (not on her), and one case removes the state and expects the whole group to
 * go: a modifier that survived its state would be a silent, permanent buff.
 *
 * <p>Consolidated from RobinAuraTest, RobinConcertoTest, RobinTraceTest, RobinWaveEnergyTest and RobinEidolonsTest; every
 * assertion, expected value, tolerance and helper is unchanged.
 *
 * <p><b>L3.</b> The single-clause cases are folded into the scenario tests below, one {@code Assertions.assertAll} per
 * fixture and per observed object so a failing clause still names itself: the aura's two ends (with and without the
 * state), the Skill clause's own-vs-ally pair, the basic-attack boundary, and Eidolon 4's control-class dispel with the
 * E3 control beside it. A case whose fixture or observed object is not the same - the compiled data row, the immunity
 * and its countdown, the ADVANCE reading, the wave energy, the Eidolon 1 / 2 / 4 attribute panels - keeps its own test.
 */
public class RobinTest {
    private static final int ROBIN = 1309;
    private static final int ALLY = 1002;
    /**
     * The ally of the concerto case: 1210, a different character from the {@link #ALLY} the aura and Eidolon cases read.
     */
    private static final int CONCERTO_ALLY = 1210;
    /**
     * 1202 Tingyun (停云): the same path (harmony) and level, but no rule file of her own - so she is a real character
     * acting as the control, not a mock. Her skill and basic attack both deal no damage beyond the basic attack itself,
     * which is what makes the absolute assertions below safe.
     */
    private static final int CONTROL = 1202;
    private static final int LEVEL = 80;
    private static final double EPS = 1e-9;
    /**
     * The wave case pins 5 energy against 1e-6 rather than against {@link #EPS}: the gain there is a sum of resources, and
     * the looser tolerance is the one that case measured it with.
     */
    private static final double WAVE_EPS = 1e-6;
    /** 「冻结」: a control-class state - the thing Eidolon 4 removes. */
    private static final String CONTROL_MARK = "ctrl:frozen";

    // ==================================================================
    // The 【协奏】 aura: trace 1309102 「即兴装饰」
    // ==================================================================

    @Test
    public void theAuraNamesTheFollowUpScopeAndShare() {
        Character owner = CharacterFactory.create(ROBIN, LEVEL);
        Enemy enemy = BattleFixture.monster();
        owner.getBuffManager().addBuff(new StateBuff("char:1309/concerto", 3, true));
        int seen = 0;
        for (var rule : owner.getTriggerTable().matching(TriggerEvent.DEALING_DAMAGE,
                new TriggerTable.TriggerContext(owner, owner, enemy, 0, 0))) {
            for (var effect : rule.effects()) {
                if ("MODIFY_ATTR".equals(effect.getOp()) && "CRIT_ATTACK".equals(effect.getAttribute())) {
                    seen++;
                    System.out.println("[1309] id=" + rule.id() + " scope=" + effect.getDamageType()
                            + " percent=" + effect.getPercent() + " instance=" + effect.getInstance());
                    Assertions.assertEquals("ADDITIONAL", effect.getDamageType(), "scoped to follow-up damage");
                    Assertions.assertEquals(0.25, effect.getPercent(), 1e-9, "the data row states 0.25");
                }
            }
        }
        Assertions.assertEquals(1, seen, "exactly one such modifier, and it needs the state");
    }

    /**
     * Trace 1309102's two ends on the same board: the ALLY's follow-up carries the owner's 25% while 【协奏】 holds, and the
     * same ally's follow-up is untouched without it - a number that only holds because the state is on cannot pass by
     * accident.
     */
    @Test
    public void theAuraReachesTheAlliesFollowUpOnlyWhileConcertoHolds() {
        Character owner = CharacterFactory.create(ROBIN, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(owner, ally), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        owner.getBuffManager().addBuff(new StateBuff("char:1309/concerto", 3, true));
        ally.getAttribute(AttributeType.CRIT_CHANCE)
                .addModifier(DoubleValue.Modifier.pure(2.0, DoubleValue.Modifier.ModifierSource.BUFF, 130901));
        boolean allyAlwaysCrits = ally.getAttribute(AttributeType.CRIT_CHANCE).get() >= 1.0;
        double normal = battle.applyDamage(enemy, new Damage(ally, enemy, DamageElement.QUANTUM, DamageType.NORMAL, 1000));
        double followUp = battle.applyAdditionalDamage(ally, enemy, DamageElement.QUANTUM, 1000, null, null);
        System.out.println("[1309] ally normal=" + normal + " ally followUp=" + followUp
                + " ratio=" + (followUp / normal));

        // MEASURED first, then pinned: (1.5 + 0.25) / 1.5. A self-only implementation would read exactly 1.0 here, which is
        // what the state-less control below asserts.
        Character noStateOwner = CharacterFactory.create(ROBIN, LEVEL);
        Character noStateAlly = CharacterFactory.create(ALLY, LEVEL);
        Enemy noStateEnemy = BattleFixture.monster();
        Battle noStateBattle = new Battle(List.of(noStateOwner, noStateAlly), List.of(noStateEnemy),
                BattleFixture.seeded());
        noStateBattle.startBattle();
        noStateAlly.getAttribute(AttributeType.CRIT_CHANCE)
                .addModifier(DoubleValue.Modifier.pure(2.0, DoubleValue.Modifier.ModifierSource.BUFF, 130902));
        double noStateNormal = noStateBattle.applyDamage(noStateEnemy,
                new Damage(noStateAlly, noStateEnemy, DamageElement.QUANTUM, DamageType.NORMAL, 1000));
        double noStateFollowUp = noStateBattle.applyAdditionalDamage(noStateAlly, noStateEnemy, DamageElement.QUANTUM,
                1000, null, null);
        System.out.println("[1309] no state: normal=" + noStateNormal + " followUp=" + noStateFollowUp
                + " ratio=" + (noStateFollowUp / noStateNormal));

        Assertions.assertAll("trace 1309102: the ally's follow-up, with and without 【char:1309/concerto】",
                () -> Assertions.assertTrue(allyAlwaysCrits, "precondition: the ally's hits always crit"),
                () -> Assertions.assertEquals(1.1666666667, followUp / normal, 1e-6,
                        "the owner's aura reaches the ALLY's follow-up"),
                () -> Assertions.assertEquals(1.0, noStateFollowUp / noStateNormal, 1e-6,
                        "without the state the aura does nothing"));
    }

    // ==================================================================
    // The 【协奏】 immunity, and the cast that opens it
    // ==================================================================

    /**
     * 「处于【协奏】状态时，知更鸟免疫控制类负面状态」 - the immunity, asserted BOTH ways: the control that lands after the
     * countdown cannot land while the state lasts, and the state's clock is the countdown rather than a turn count.
     *
     * <p>Note: <b>how the clause was born.</b> A second copy of the clauses measured a party ATK gain of 45.081848 where
     * 0.228 x the pre-cast ATK + 200 is 386.844908 - which reads like an engine bug. The duplicate was the cause: the first
     * rule computes 386.844908 (her ATK then reads 1206.499) and the second recomputes 45.081848 from that boosted value,
     * replacing the first because both carry the same name. The instrument was too coarse to see it (the counter counts
     * EVENTS, not rules), and the probe that would have shown the existing rule printed into a report that was read
     * truncated.
     */
    @Test
    public void herConcertoMakesHerControlImmuneUntilTheCountdown() {
        Character robin = CharacterFactory.create(ROBIN, LEVEL);
        Character ally = CharacterFactory.create(CONCERTO_ALLY, LEVEL);
        Character injector = Character.fromAttributes("injector", 10_000, 100, 100, 100);
        injector.setTriggerTable(new TriggerTable(9999, List.of(TriggerSpecs.rule(
                TriggerEvent.TURN_START.name(), List.of(), controlOnTarget()))));
        Character counter = Character.fromAttributes("counter", 10_000, 100, 100, 100);
        counter.setTriggerTable(new TriggerTable(9998, List.of(TriggerSpecs.rule(
                TriggerEvent.ULT_CAST.name(), List.of(), stack()))));
        Battle battle = new Battle(List.of(robin, ally, injector, counter), List.of(monster()), fixed());

        battle.startBattle();
        battle.castImmediate(robin.getSkills().get(SkillType.ULTRA), robin, List.of(monster()));

        Assertions.assertEquals(1, counter.getBuffManager().stacksOf("计数"),
                "ULT_CAST arrives at the tables once per cast (an event count -- it does NOT count the rules that match)");
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.TURN_START, injector, robin, 0, 0));
        Assertions.assertFalse(robin.getBuffManager().hasState("ctrl:frozen"),
                "「处于【char:1309/concerto】状态时，知更鸟免疫控制类负面状态」 (while in the Concerto (【char:1309/concerto】) state, Robin is immune to Crowd Control debuffs)");

        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.COUNTDOWN_TURN, battle.countdownsOf(robin).getFirst(), robin, 0, 0));

        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.TURN_START, injector, robin, 0, 0));
        Assertions.assertTrue(robin.getBuffManager().hasState("ctrl:frozen"),
                "the immunity is named after the state, so the countdown takes it off -- the same control now lands");
    }

    /**
     * A 100% base chance control aimed at the event's target (the immunity under test is on the target).
     */
    private static EffectSpec controlOnTarget() {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "APPLY_CONTROL");
        TriggerSpecs.set(effect, "control", "ctrl:frozen");
        TriggerSpecs.set(effect, "turns", 1);
        TriggerSpecs.set(effect, "baseChance", 1.0);
        TriggerSpecs.set(effect, "target", "target");
        return effect;
    }

    /**
     * Counts how many times the event arrives.
     */
    private static EffectSpec stack() {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "ADD_STACK");
        TriggerSpecs.set(effect, "buff", "计数");
        TriggerSpecs.set(effect, "maxStacks", 99);
        TriggerSpecs.set(effect, "permanent", true);
        TriggerSpecs.set(effect, "target", "self");
        return effect;
    }

    private static Enemy monster() {
        return BattleFixture.monster();
    }

    /**
     * Rolls low enough that a control that is not resisted always lands.
     */
    private static Random fixed() {
        return BattleFixture.alwaysRoll(0.0);
    }

    // ==================================================================
    // The trace extra abilities
    // ==================================================================

    /**
     * "at the start of the battle, advance its own action by 25%" - 25% of the wait still ahead, not 25% of the round.
     */
    @Test
    public void battleStartAdvancesHerByAQuarterOfTheRemainingWait() {
        double withTrace = remainingAtBattleStart(true);
        double withoutTrace = remainingAtBattleStart(false);
        Assertions.assertTrue(withoutTrace > 0, "precondition: she has a wait to shorten at battle start");
        Assertions.assertTrue(withTrace < withoutTrace, "the trace must pull her forward");
        Assertions.assertEquals(0.75 * withoutTrace, withTrace, 1e-6,
                "ADVANCE skips a fraction of the REMAINING wait (Queue.advanceActionByPercent), "
                        + "and it is the owner who is advanced -- the other three are untouched");
    }

    /**
     * "when casting the Skill, additionally restore 5 points of energy" - her own skill, and only her own.
     *
     * <p>The absolute numbers are not 0/5: the skill <b>action</b> itself already credits the caster 30 energy irrespective
     * of any trigger rule (it is credited to whoever cast, which is why the ally case leaves her at 0). That 30 is
     * therefore not asserted here - it belongs to the energy model, not to this trace. What is asserted is the difference
     * the rule makes, which is exactly the word "additional" in the text.
     *
     * <p>The load-bearing condition: {@code SKILL_CAST} reaches the whole side, so without {@code actor == self} an ally's
     * skill would feed Robin too - that half is asserted beside the positive one, at 0.
     */
    @Test
    public void onlyHerOwnSkillCastGrantsTheExtraFiveEnergy() {
        double withTrace = robinEnergyAfterSkillCast(true, 0);
        double withoutTrace = robinEnergyAfterSkillCast(false, 0);
        double allySkillCast = robinEnergyAfterSkillCast(true, 1);
        Assertions.assertAll("「施放战技时，额外恢复 5 点能量」: her own Skill, and an ally's",
                () -> Assertions.assertTrue(withoutTrace > 0,
                        "precondition: the skill action credits the caster by itself"),
                () -> Assertions.assertEquals(5.0, withTrace - withoutTrace, EPS,
                        "the trace is ADDITIONAL to the action's own energy credit"),
                () -> Assertions.assertEquals(0.0, allySkillCast, EPS,
                        "「when casting the Skill」 means HER skill -- drop `actor == self` and this becomes 5"));
    }

    /**
     * "when casting the Skill" must not fire on a <b>basic attack</b>, from either side of the clause.
     *
     * <p>This is the test that found the bug rather than pinning a known behaviour: {@code SKILL_CAST} used to mean "any
     * cast that is not an ultimate" (the emitter only split ultimate from everything else), so her own basic attacks were
     * paying her the extra 5 as well. Her relic-109 sibling - "when casting the Skill, ATK is increased by 20%" - had the
     * same over-trigger, shipped, with a note claiming the emitter's split already handled it.
     *
     * <p>Composition: her talent (authored earlier, {@code ALLY_ATTACK} to +2) still works next to the new rules, and a
     * basic attack is <b>not</b> a skill cast, so the +5 must not leak into it.
     */
    @Test
    public void aBasicAttackIsNotASkillCast() {
        double withTrace = robinEnergyAfterSkillCast(true, 0, SkillType.COMMON);
        double withoutTrace = robinEnergyAfterSkillCast(false, 0, SkillType.COMMON);
        double allyBasicAttack = robinEnergyAfterSkillCast(true, 1, SkillType.COMMON);
        Assertions.assertAll("a basic attack is not the Skill slot: her own, and an ally's",
                () -> Assertions.assertEquals(0.0, withTrace - withoutTrace, EPS,
                        "「when casting the Skill」 is the Skill slot, not a basic attack: a basic attack must not feed her the extra 5"),
                () -> Assertions.assertEquals(2.0, allyBasicAttack, EPS,
                        "talent: after one of our targets attacks an enemy target -> +2 energy (flat per attack, not per target)"));
    }

    /**
     * @param keepRules {@code false} = the same character with an emptied table, the baseline that makes every assertion
     *                  above a difference rather than a coincidence
     */
    private static Battle battleWithRobin(boolean keepRules) {
        Character robin = CharacterFactory.create(ROBIN, LEVEL);
        Character control = CharacterFactory.create(CONTROL, LEVEL);
        if (!keepRules) {
            robin.setTriggerTable(new TriggerTable(ROBIN, List.of()));
        }
        Battle battle = new Battle(List.of(robin, control), List.of(dummy()), BattleFixture.seeded());
        battle.startBattle();
        return battle;
    }

    private static double remainingAtBattleStart(boolean keepRules) {
        Battle battle = battleWithRobin(keepRules);
        Character robin = battle.characters().getFirst();
        return battle.queue().snapshot().stream()
                .filter(signal -> signal.getCanHit() == robin)
                .findFirst()
                .map(battle.queue()::getTimeRemaining)
                .orElseThrow(() -> new AssertionError("Robin is not on the action bar"));
    }

    /**
     * Robin's energy gain caused by {@code actorIndex}'s action.
     *
     * @param actorIndex 0 = Robin herself, 1 = the ally
     */
    private static double robinEnergyAfterSkillCast(boolean keepRules, int actorIndex) {
        return robinEnergyAfterSkillCast(keepRules, actorIndex, SkillType.SKILL);
    }

    private static double robinEnergyAfterSkillCast(boolean keepRules, int actorIndex, SkillType slot) {
        Battle battle = battleWithRobin(keepRules);
        Character robin = battle.characters().getFirst();
        Character actor = battle.characters().get(actorIndex);
        double before = robin.getCurrentEnergy();
        Skill skill = actor.getSkills().get(slot);
        Assertions.assertNotNull(skill, "precondition: the actor carries the " + slot + " slot");
        battle.castImmediate(skill, actor, List.of(battle.enemies().getFirst()));
        return robin.getCurrentEnergy() - before;
    }

    private static Enemy dummy() {
        return Enemy.fromAttributes("dummy", 100_000, 100, 100, 100);
    }

    // ==================================================================
    // The technique's field: 5 energy at each wave's start
    // ==================================================================

    /**
     * Five with the field, nothing without it.
     */
    @Test
    public void theFieldPaysFiveAtEachWave() {
        double withField = gainAtWaveStart(true);
        double withoutField = gainAtWaveStart(false);
        System.out.println("[robin-wave] with field=" + withField + " without=" + withoutField);

        Assertions.assertEquals(5.0, withField, WAVE_EPS,
                "「每个波次开始时知更鸟恢复 5 点能量」 (at the start of every wave Robin restores 5 Energy)-- and the field is what makes it hers");
        Assertions.assertEquals(0.0, withoutField, WAVE_EPS,
                "「领域展开期间进入战斗后」 (after entering battle while the field is open)-- no field, no energy: this half is what makes the reading about the clause");
    }

    /**
     * Her energy gain when a wave starts, with or without the technique's field.
     */
    private static double gainAtWaveStart(boolean technique) {
        Character robin = CharacterFactory.create(ROBIN, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(robin),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        if (technique) {
            battle.markTechniqueUsed(robin);
        }
        battle.startBattle();
        battle.processRequests();
        double before = robin.getCurrentEnergy();
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.WAVE_START, null, null, 0, 0));
        battle.processRequests();
        return robin.getCurrentEnergy() - before;
    }

    // ==================================================================
    // Eidolons 1 / 2 / 4
    // ==================================================================

    /**
     * Eidolon 1: 「处于【协奏】状态时，我方全体全属性抗性穿透提高 24%」.
     */
    @Test
    public void theFirstEidolonPutsPenetrationOnTheWholeParty() {
        Run e0 = new Run(0);
        Run e1 = new Run(1);
        double plain = e0.penetration();
        double raised = e1.penetration();
        System.out.println("[1309-E1] ally penetration " + plain + " -> " + raised);
        Assertions.assertEquals(0.0, plain, 1e-9, "E0 states no such aura");
        Assertions.assertEquals(0.24, raised - plain, 1e-9, "「全属性抗性穿透提高 24%」");
    }

    /**
     * Eidolon 2: party speed +16%, and the talent's energy restore is raised by 1.
     */
    @Test
    public void theSecondEidolonAddsSpeedAndOneMoreEnergy() {
        Run e1 = new Run(1);
        Run e2 = new Run(2);
        double base = e2.ally.getAttribute(AttributeType.SPEED).baseValue();
        double plainSpeed = e1.ally.getAttribute(AttributeType.SPEED).get();
        double raisedSpeed = e2.ally.getAttribute(AttributeType.SPEED).get();
        Assertions.assertEquals(0.16 * base, raisedSpeed - plainSpeed, 1e-6,
                "「我方全体速度提高 16%」 of the base speed");

        Assertions.assertEquals(2.0, e1.talentEnergy(), 1e-9, "the talent pays 2 by default");
        Assertions.assertEquals(3.0, e2.talentEnergy(), 1e-9,
                "「天赋的能量恢复效果额外提高 1 点」 - `MODIFY_RULE effect_amount: 1`");
    }

    /**
     * Eidolon 4: party effect RES +50% while 【协奏】 holds - and the whole group goes with the state.
     */
    @Test
    public void theFourthEidolonAddsEffectResistanceAndItLeavesWithTheState() {
        Run e4 = new Run(4);
        Assertions.assertEquals(0.5, e4.effectResistance(), 1e-9, "「效果抵抗提高 50%」");

        // The content removes the state from EVERY ally (`REMOVE_STATE 协奏 all_allies`): the state sits on her, while the
        // auras carrying the same name sit on each carrier - so the group comes off one carrier at a time.
        e4.robin.getBuffManager().removeState(Run.STATE);
        e4.ally.getBuffManager().removeState(Run.STATE);
        e4.battle.processRequests();

        Assertions.assertEquals(0.0, e4.effectResistance(), 1e-9,
                "the modifier is grouped by the state name, so one REMOVE_STATE takes the whole group off");
        Assertions.assertEquals(0.0, e4.penetration(), 1e-9, "and the Eidolon 1 aura with it");
    }

    /**
     * Eidolon 4, first half: 「解除我方全体的**控制类**负面状态」 - by CLASS, so a non-control debuff stays; and the control
     * it leaves: at E3 nothing dispels it.
     *
     * <p>⚠ The registry used to call this unwritable (「`DISPEL` only knows all debuffs」). That was stale: the op takes a
     * {@code kind} (the engine's {@code DebuffClass}) and, with no amount, means "all of that class".
     */
    @Test
    public void theFourthEidolonDispelsControlButKeepsOtherDebuffs() {
        Run e4 = debuffedRun(4);
        boolean controlBeforeUlt = e4.ally.getBuffManager().hasState(CONTROL_MARK);
        boolean statDebuffBeforeUlt = e4.ally.getAttribute(AttributeType.DEFENCE).get() < e4.allyBareDefence;

        e4.ult();

        Run e3 = debuffedRun(3);
        e3.ult();

        Assertions.assertAll("「解除我方全体的控制类负面状态」: E4 dispels it, E3 does not",
                () -> Assertions.assertTrue(controlBeforeUlt, "the fixture starts with a control state on the ally"),
                () -> Assertions.assertTrue(statDebuffBeforeUlt, "and a non-control stat debuff, which must survive"),
                () -> Assertions.assertFalse(e4.ally.getBuffManager().hasState(CONTROL_MARK),
                        "「解除我方全体的控制类负面状态」"),
                () -> Assertions.assertTrue(e4.ally.getAttribute(AttributeType.DEFENCE).get() < e4.allyBareDefence,
                        "a stat debuff is NOT control-class, so it survives"),
                () -> Assertions.assertTrue(e3.ally.getBuffManager().hasState(CONTROL_MARK),
                        "E3 states no dispel at all"));
    }

    private static final class Run {
        private static final String STATE = "char:1309/concerto";
        private final Character robin;
        private final Character ally;
        private final Battle battle;
        private final double allyPenetrationBefore;
        private final double allyEffectResistanceBefore;
        private double allyBareDefence;

        Run(int eidolon) {
            this(eidolon, null, true);
        }

        Run(int eidolon, TriggerTable allyTable, boolean castNow) {
            robin = CharacterFactory.create(ROBIN, LEVEL);
            robin.setEidolonRank(eidolon);
            ally = CharacterFactory.create(ALLY, LEVEL);
            if (allyTable != null) {
                ally.setTriggerTable(allyTable);
            }
            Enemy enemy = BattleFixture.monster();
            battle = new Battle(List.of(robin, ally), List.of(enemy), BattleFixture.seeded());
            battle.startBattle();
            allyPenetrationBefore = ally.getAttribute(AttributeType.DAMAGE_PENETRATION).get();
            allyEffectResistanceBefore = ally.getAttribute(AttributeType.EFFECT_RESISTANCE).get();
            if (castNow) {
                // Her Ultimate is what enters 【协奏】 and, with it, what grants the three auras.
                ult();
            }
        }

        void ult() {
            battle.castImmediate(robin.getSkills().get(SkillType.ULTRA), robin,
                    List.of(battle.enemies().getFirst()));
            battle.processRequests();
        }

        double penetration() {
            return ally.getAttribute(AttributeType.DAMAGE_PENETRATION).get() - allyPenetrationBefore;
        }

        double effectResistance() {
            return ally.getAttribute(AttributeType.EFFECT_RESISTANCE).get() - allyEffectResistanceBefore;
        }

        /** What one ally attack pays her, read as the energy delta. */
        double talentEnergy() {
            double before = robin.getCurrentEnergy();
            battle.fire(TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, ally, battle.enemies().getFirst(), 1, 0));
            battle.processRequests();
            return robin.getCurrentEnergy() - before;
        }
    }

    /**
     * A run whose ally carries a control state and a plain stat debuff, both applied at battle start by a hand-built table -
     * and whose Ultimate has NOT been cast yet, so the test can look at the board before it.
     */
    private static Run debuffedRun(int eidolon) {
        TriggerTable table = new TriggerTable(ALLY, List.of(
                TriggerSpecs.rule("BATTLE_START", null, control()),
                TriggerSpecs.rule("BATTLE_START", null, statDebuff())));
        Run run = new Run(eidolon, table, false);
        run.allyBareDefence = CharacterFactory.create(ALLY, LEVEL).getAttribute(AttributeType.DEFENCE).get();
        return run;
    }

    private static EffectSpec control() {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "APPLY_CONTROL");
        TriggerSpecs.set(effect, "control", CONTROL_MARK);
        TriggerSpecs.set(effect, "turns", 3);
        TriggerSpecs.set(effect, "baseChance", 1.0);
        TriggerSpecs.set(effect, "target", "self");
        return effect;
    }

    private static EffectSpec statDebuff() {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "MODIFY_ATTR");
        TriggerSpecs.set(effect, "attribute", "DEFENCE");
        TriggerSpecs.set(effect, "percent", -0.1);
        TriggerSpecs.set(effect, "turns", 3);
        TriggerSpecs.set(effect, "target", "self");
        return effect;
    }
}
