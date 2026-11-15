package com.laosun.aluminium.test.engine;


import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.DoubleValue;
import com.laosun.aluminium.Signal;
import com.laosun.aluminium.buff.ControlBuff;
import com.laosun.aluminium.buff.DotBuff;
import com.laosun.aluminium.buff.StatModifierBuff;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enemy.EnemyFactory;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

/**
 * The three non-damaging break elements leave a control state behind, and the four
 * damaging ones still do exactly what they did before.
 *
 * <p><b>What "control" means here is decided by the data, not by the plan.</b> The plan says "冻结期受伤害
 * +30%"; the encyclopedia text says something else, the same way in six independent entries -
 * {@code "冻结状态下，敌方目标不能行动同时每回合开始时受到…冰属性伤害"} - and for the other two,
 * {@code "禁锢状态下，敌方目标行动延后#2%，速度降低#4%"} (Welt) and
 * {@code ""纠缠"会使敌人行动延后，并在敌人下次行动时对其造成额外的量子属性伤害"}. So:
 *
 * <table border="1">
 *   <caption>the three states, as the text describes them</caption>
 *   <tr><th>element</th><th>cannot act?</th><th>slows?</th><th>extra delay?</th></tr>
 *   <tr><td>Ice to frozen (冻结)</td><td><b>yes</b></td><td>no</td><td>yes</td></tr>
 *   <tr><td>Quantum to entangled (纠缠)</td><td>no</td><td>yes</td><td>yes</td></tr>
 *   <tr><td>Imaginary to imprisoned (禁锢)</td><td>no</td><td>yes</td><td>yes</td></tr>
 * </table>
 *
 * <p>The damage component of frozen (冻结) (ice damage each turn) and entangled (纠缠) (quantum damage on the next action) is <b>real but not built here</b>:
 * it is a DOT, {@code BreakEffect.dotRatio} is its field, and no source states the break-applied ratio. It
 * stays a recorded TODO instead of an invented number - and {@code BreakEffectTableTest} fails if someone
 * fills one in without deciding.
 *
 * <p>These tests drive {@link Battle#reduceToughness} directly rather than casting a skill: it is the single
 * toughness-reduction entry point and it takes the break element explicitly, so the element under test is
 * the element that breaks - no character of that element needs to exist for Ice/Quantum/Imaginary to be
 * testable.
 *
 * <p><b>Why the push is asserted to the digit for frozen (冻结) and not at all for entangled (纠缠) / imprisoned (禁锢).</b> A speed change
 * <i>reschedules</i> the pending action ({@code Signal.refreshSpeed} recomputes {@code nextActionTime} from
 * the progress the unit has already made), and the reschedule is larger the bigger the slow is - so for the
 * two states that slow, the movement of the action bar mixes two effects and no threshold can separate them.
 * On this fixture, measured:
 *
 * <table border="1">
 *   <caption>action-value movement of the same break, by how much it slows</caption>
 *   <tr><th>case</th><th>movement</th><th>assertable?</th></tr>
 *   <tr><td>plain break, no slow, no extra delay</td><td>18.94 (exactly 25%)</td><td>yes - this test's regression case pins it</td></tr>
 *   <tr><td>Freeze, no slow, +50% delay</td><td>56.82</td><td><b>yes, to the digit</b> - nothing recomputes</td></tr>
 *   <tr><td>Imprison, 10% slow, without the extra delay</td><td>33.6</td><td>no - already above 18.94</td></tr>
 *   <tr><td>Imprison, 10% slow, with the +20% delay</td><td>50.51</td><td>no - indistinguishable in kind</td></tr>
 *   <tr><td>Entangle, 20% slow, without the extra delay</td><td>52.08</td><td>no - already above 18.94</td></tr>
 *   <tr><td>Entangle, 20% slow, with the +20% delay</td><td>1.02</td><td>no - indistinguishable in kind</td></tr>
 * </table>
 *
 * <p>So an action-bar delay assertion for entanglement/imprisonment could not fail, and both were removed rather than kept
 * as decoration. Nothing is left unpinned by that, because the three links are covered where they can be
 * isolated: the <b>table</b> by {@code BreakEffectTableTest} ({@code delayPercent > 0} for every control
 * element), the <b>one call site</b> that reads it by freeze below (exact), and the <b>mechanism</b> - a push
 * surviving a speed change - by {@code QueueActionManipulationTest.aDelaySurvivesASpeedChange}, which is
 * where it can be observed alone.
 *
 * <p><b>L3:</b> the single-clause cases are grouped into the five scenarios below, one {@code assertAll}
 * per scenario so a failing clause still names itself: the three control breaks, the two sides of the break
 * boundary, the skill-applied state together with its landing roll, and the payload the state owns. The four
 * load-time rejection paths stay their own tests: an {@code assertThrows} is not a positive clause and must
 * not hide behind one.
 */
public class ControlTest {
    private static final double EPS = 1e-6;

    // ==================================================================
    // 1. The break path: the element decides what state is left
    // ==================================================================

    /**
     * The three non-damaging elements leave a control state behind, and the state is what the data says:
     * freeze (冻结) locks the action and pushes the bar, entangle (纠缠) and imprison (禁锢) only slow the
     * victim and still let it act.
     *
     * <p>One scenario because the three clauses use the same fixture ({@code fixture(element)}: Ice Edge in a
     * seeded battle, made weak to exactly the element under test) and the same observables - the enemy's buff
     * manager, its speed and the action-value bar. The element is the clause, not the fixture, so each break
     * is driven on its own instance and every clause names itself in the {@code assertAll}.
     */
    @Test
    public void theThreeControlBreaksLeaveTheirOwnState() {
        // Ice -> frozen (冻结): cannot act, no slow, and the fixed 25% plus the element's own extra push.
        Fixture frozen = fixture(DamageElement.ICE);
        double frozenSpeedBefore = frozen.speed();
        double frozenDelayBefore = timeRemaining(frozen.battle, frozen.enemy);
        frozen.breakWith(DamageElement.ICE);
        boolean freezeAttachedAControlBuff = frozen.enemy.getBuffManager().hasBuff(ControlBuff.class);
        boolean frozenCanAct = frozen.enemy.getBuffManager().canAct();
        double frozenSpeed = frozen.speed();

        // Quantum -> entangled (纠缠): acts, just later and slower.
        Fixture entangled = fixture(DamageElement.QUANTUM);
        double entangledSpeedBefore = entangled.speed();
        entangled.breakWith(DamageElement.QUANTUM);
        boolean entangledCanAct = entangled.enemy.getBuffManager().canAct();
        double entangledSpeed = entangled.speed();

        // Imaginary -> imprisoned (禁锢): acts, just later and slower.
        Fixture imprisoned = fixture(DamageElement.IMAGINARY);
        double imprisonedSpeedBefore = imprisoned.speed();
        imprisoned.breakWith(DamageElement.IMAGINARY);
        boolean imprisonedCanAct = imprisoned.enemy.getBuffManager().canAct();
        double imprisonedSpeed = imprisoned.speed();
        // The push is NOT asserted for either of these, deliberately -- see the class javadoc's measured
        // table. A 20%/10% slow reschedules the action by more than the push, so with or without the
        // element's extra delay the movement clears every threshold this test could name: an assertion
        // that cannot fail. The push being read out of the table is pinned by the freeze state's exact
        // figure, and the mechanism by QueueActionManipulationTest.aDelaySurvivesASpeedChange.

        Assertions.assertAll(
                () -> Assertions.assertTrue(freezeAttachedAControlBuff,
                        "the freeze state must attach an act lock"),
                () -> Assertions.assertEquals("ctrl:frozen",
                        frozen.enemy.getBuffManager().findBuff(ControlBuff.class).getName(),
                        "and the state carries the NAME the documents use, which is what the freeze state is asked with"),
                () -> Assertions.assertFalse(frozenCanAct,
                        "the freeze state = cannot act; this is the same predicate performAction refuses on"),
                () -> Assertions.assertEquals(frozenSpeedBefore, frozenSpeed, EPS,
                        "the freeze state does not slow -- imprisonment/entanglement do"),
                () -> Assertions.assertEquals(
                        frozen.period() * (Constant.BREAK_DELAY_RATIO + Constant.FREEZE_EXTRA_DELAY),
                        timeRemaining(frozen.battle, frozen.enemy) - frozenDelayBefore, EPS,
                        "the delay is the fixed 25% plus the element's own extra"),
                () -> Assertions.assertTrue(entangledCanAct,
                        "entanglement acts, just later and slower -- it must NOT be an act lock"),
                () -> Assertions.assertEquals(
                        entangledSpeedBefore * (1 - Constant.CONTROL_EFFECTS.get("ENTANGLED").slowPercent()),
                        entangledSpeed, EPS, "entanglement = speed reduction"),
                () -> Assertions.assertTrue(imprisonedCanAct, "imprisonment is not an act lock"),
                () -> Assertions.assertEquals(
                        imprisonedSpeedBefore * (1 - Constant.CONTROL_EFFECTS.get("IMPRISONED").slowPercent()),
                        imprisonedSpeed, EPS, "imprisonment = speed reduction"));
    }

    /**
     * The break path is decided by the element alone: a damaging break is untouched, and a monster that is
     * immune to the <i>skill's</i> freeze is still frozen by an Ice break.
     *
     * <p>Both clauses drive the same fixture ({@code fixture(element)} plus one break) and read the same
     * observable, what the enemy's buff manager allows. They are the two sides of one boundary: control is
     * wired to the three elements that have a state and to nothing else - a physical break that suddenly
     * carried a control buff, or slowed, or added an extra delay, is what would go red if the control path
     * were wired to every element - and a break is not a resisted skill debuff, because it happens when the
     * toughness bar emptied, not because a hit landed.
     */
    @Test
    public void aBreakIsDecidedByTheElementAlone() {
        // Regression, and the guard on the whole task: a damaging break must be untouched.
        Fixture physical = fixture(DamageElement.PHYSICAL);
        double physicalSpeedBefore = physical.speed();
        double physicalDelayBefore = timeRemaining(physical.battle, physical.enemy);
        physical.breakWith(DamageElement.PHYSICAL);
        int burns = physical.enemy.getBuffManager().countBuffs(DotBuff.class);
        boolean physicalCanAct = physical.enemy.getBuffManager().canAct();
        boolean physicalHasAControlBuff = physical.enemy.getBuffManager().hasBuff(ControlBuff.class);
        int physicalSlows = physical.enemy.getBuffManager().countBuffs(StatModifierBuff.class);
        double physicalSpeed = physical.speed();

        // A break cannot be resisted away: STAT_CTRL_Frozen = 1 cannot cancel the break's state.
        Fixture immune = fixture(DamageElement.ICE);
        immune.enemy.setDebuffResist(java.util.Map.of("STAT_CTRL_Frozen", 1.0));
        immune.breakWith(DamageElement.ICE);
        boolean immuneCanAct = immune.enemy.getBuffManager().canAct();

        Assertions.assertAll(
                () -> Assertions.assertEquals(1, burns, "still burns"),
                () -> Assertions.assertTrue(physicalCanAct, "a burn does not control"),
                () -> Assertions.assertFalse(physicalHasAControlBuff,
                        "a damaging break attaches no control state"),
                () -> Assertions.assertEquals(0, physicalSlows,
                        "a burn must not slow: the slow belongs to the control states only"),
                () -> Assertions.assertEquals(physicalSpeedBefore, physicalSpeed, EPS,
                        "the burn leaves the speed alone"),
                () -> Assertions.assertEquals(physical.period() * Constant.BREAK_DELAY_RATIO,
                        timeRemaining(physical.battle, physical.enemy) - physicalDelayBefore, EPS,
                        "the four damaging elements add no extra delay"),
                () -> Assertions.assertFalse(immuneCanAct,
                        "a break is not a resisted debuff: 100% skill resistance must not cancel it"));
    }

    // ==================================================================
    // 2. A control applied by a SKILL: op APPLY_CONTROL
    // ==================================================================

    /**
     * A control applied by a skill is one named state: it lands, it answers by name, it is the same state a
     * break produces, a rule can gate on it, and it is a debuff that can be dispelled.
     *
     * <p>One fixture family ({@code new Applied(control(1.0))}: the placeholder caster and enemy 1003010 in
     * a seeded battle) and one observable, the named state in the buff manager. The freeze states the same
     * name whichever path applied it - the property that made the migration worth doing: before
     * {@code ControlBuff}, a break produced an unnamed {@code StunBuff}, so a question about the freeze state
     * answered <b>false</b> for a unit that was, to the game and to the player, frozen.
     *
     * <p>The gate is on a <b>later event</b>, and that is not a stylistic choice: {@code TriggerInterpreter.fire}
     * matches <b>every</b> rule of one event against the context <i>before</i> any of them runs ({@code matching}
     * is a pure predicate), so a rule cannot see a state an earlier rule of the same event applied. Content that
     * wants both writes two rules on two events - which is what the game's sentences do too (the freeze is on the
     * cast, the bonus is on the damage).
     */
    @Test
    public void aSkillAppliedControlIsOneNamedState() {
        // "有 50% 基础概率使敌方目标陷入冻结状态，持续1回合": the state lands, stops the turn, and has a name.
        Applied freeze = new Applied(control(1.0));
        freeze.fire();
        boolean freezeHasTheName = freeze.enemy.getBuffManager().hasState("ctrl:frozen");
        boolean freezeCanAct = freeze.enemy.getBuffManager().canAct();
        ControlBuff freezeBuff = freeze.enemy.getBuffManager().findBuff(ControlBuff.class);
        String freezeBuffName = freezeBuff == null ? null : freezeBuff.getName();
        int freezeDebuffs = freeze.enemy.getBuffManager().debuffCount();
        freeze.enemy.getBuffManager().removeDebuffs(1);
        boolean canActAfterDispel = freeze.enemy.getBuffManager().canAct();

        // A break-frozen unit and a skill-frozen unit are the same state.
        Fixture broken = fixture(DamageElement.ICE);
        broken.breakWith(DamageElement.ICE);
        Applied applied = new Applied(control(1.0));
        applied.fire();
        boolean breakHasTheName = broken.enemy.getBuffManager().hasState("ctrl:frozen");
        boolean skillHasTheName = applied.enemy.getBuffManager().hasState("ctrl:frozen");

        // A gated rule can ask the question: "target has_state 冻结" / ctrl:frozen.
        Applied gated = new Applied(control(1.0),
                TriggerSpecs.rule("BASIC_ATTACK", java.util.List.of("target has_state ctrl:frozen"),
                        TriggerSpecs.gainEnergy(1)));
        int controlLands = gated.fire();
        int laterEventSeesTheFreeze = gated.fire(com.laosun.aluminium.enums.TriggerEvent.BASIC_ATTACK);

        // The same gate on a victim whose effect resistance is 100%: nothing landed, so nothing is seen.
        Applied gatedResisted = new Applied(control(1.0),
                TriggerSpecs.rule("BASIC_ATTACK", java.util.List.of("target has_state ctrl:frozen"),
                        TriggerSpecs.gainEnergy(1)));
        gatedResisted.enemy.setAttribute(AttributeType.EFFECT_RESISTANCE, new DoubleValue(1.0));
        int resistedControlLands = gatedResisted.fire();
        int resistedLaterEvent = gatedResisted.fire(com.laosun.aluminium.enums.TriggerEvent.BASIC_ATTACK);

        Assertions.assertAll(
                () -> Assertions.assertTrue(freezeHasTheName,
                        "the applied state answers has_state under the name the documents use"),
                () -> Assertions.assertFalse(freezeCanAct, "Frozen (ctrl:frozen) = cannot act (不能行动)"),
                () -> Assertions.assertEquals("ctrl:frozen", freezeBuffName,
                        "and it is a ControlBuff, so the state's name is readable"),
                () -> Assertions.assertEquals(1, freezeDebuffs, "the control counts as a debuff"),
                () -> Assertions.assertTrue(canActAfterDispel, "dispelling it gives the turn back"),
                () -> Assertions.assertTrue(breakHasTheName, "the break's state has the name"),
                () -> Assertions.assertTrue(skillHasTheName, "and so does the skill's"),
                () -> Assertions.assertEquals(
                        broken.enemy.getBuffManager().findBuff(ControlBuff.class).getName(),
                        applied.enemy.getBuffManager().findBuff(ControlBuff.class).getName(),
                        "one state, one name, whichever path applied it"),
                () -> Assertions.assertEquals(1, controlLands, "the freeze lands"),
                () -> Assertions.assertEquals(1, laterEventSeesTheFreeze,
                        "and a later event's rule sees the freeze state on the victim"),
                () -> Assertions.assertEquals(1, resistedControlLands,
                        "here the control rule runs but lands nothing"),
                () -> Assertions.assertEquals(0, resistedLaterEvent,
                        "so the gated rule is refused -- the gate is the only thing that changed"));
    }

    /**
     * A base chance is a <b>base</b> chance: it goes through effect hit, effect resistance and the monster's
     * own resist data, per target.
     *
     * <p>Driven from the ends of the pipeline rather than from a lucky seed: with the applier's effect hit at
     * +100% a 50% base chance becomes certain, with the victim's effect resistance at 100% it becomes
     * impossible, and Ice Edge's {@code STAT_CTRL_Frozen = 1} makes it impossible for that monster whatever the
     * roll. A rule-level {@code chance} could do none of this (it is one fixed roll for the whole rule), which
     * is why the vocabulary needed this field rather than reusing that one.
     *
     * <p>The 1002011 enemy is a clause, not a second fixture: the roll reads the target's own resist data, and
     * every other case uses 1003010 (no specific resistances) precisely so that "the state lands" is a
     * statement about the pipeline and not about the data.
     */
    @Test
    public void theLandingRollRunsThroughHitResistAndMonsterData() {
        Applied certain = new Applied(control(1.0));
        certain.hero.setAttribute(AttributeType.EFFECT_HIT_RATE, new DoubleValue(1.0));
        certain.fire();
        boolean landedThroughEffectHit = certain.enemy.getBuffManager().hasState("ctrl:frozen");

        Applied resistedByEffectResistance = new Applied(control(1.0));
        resistedByEffectResistance.enemy.setAttribute(AttributeType.EFFECT_RESISTANCE, new DoubleValue(1.0));
        resistedByEffectResistance.fire();
        boolean landedThroughEffectResistance =
                resistedByEffectResistance.enemy.getBuffManager().hasState("ctrl:frozen");

        Applied resistedByMonsterData = new Applied(EnemyFactory.create(1002011, 90, 1), control(1.0));
        resistedByMonsterData.hero.setAttribute(AttributeType.EFFECT_HIT_RATE, new DoubleValue(1.0));
        resistedByMonsterData.fire();
        boolean landedThroughMonsterData =
                resistedByMonsterData.enemy.getBuffManager().hasState("ctrl:frozen");

        Assertions.assertAll(
                () -> Assertions.assertTrue(landedThroughEffectHit,
                        "50% base × (1 + 100% effect hit) = 100%, so it always lands"),
                () -> Assertions.assertFalse(landedThroughEffectResistance,
                        "and 100% effect resistance takes it to 0, whatever the base chance was"),
                () -> Assertions.assertEquals(java.util.Map.of("STAT_CTRL_Frozen", 1.0),
                        resistedByMonsterData.enemy.getDebuffResist(),
                        "precondition: this monster's data is what makes it unfreezable"),
                () -> Assertions.assertFalse(landedThroughMonsterData,
                        "STAT_CTRL_Frozen = 1 means this monster cannot be frozen by a skill -- which is exactly what "
                                + "ControlEffect.resistKey is for (and what a BREAK deliberately ignores)"));
    }

    // ==================================================================
    // 3. The state's own payload rides with the state
    // ==================================================================

    /**
     * The state carries its payload, and removal takes the payload with it: entanglement's slow and the
     * freeze's per-turn ice damage both belong to the one buff, so neither can be left behind - otherwise
     * the ice would keep burning after "解除冻结".
     *
     * <p>One scenario because the fixture is the same applied-control battle and the observable is the same
     * question asked of two payload kinds (a {@code StatModifierBuff} for speed, a {@code DotBuff} for the ice
     * damage). A resisted freeze attaches no payload at all, which is what "one effect, not two" means.
     */
    @Test
    public void theStateCarriesItsPayloadAndRemovalTakesIt() {
        // Entanglement's slow goes when the state is removed by name.
        Applied entangled = new Applied(control("ctrl:entangled", null));
        double speedBeforeEntangle = entangled.enemy.getAttribute(AttributeType.SPEED).get();
        entangled.fire();
        double speedWhileEntangled = entangled.enemy.getAttribute(AttributeType.SPEED).get();
        int removedStates = entangled.enemy.getBuffManager().removeState("ctrl:entangled");
        double speedAfterEntangleRemoved = entangled.enemy.getAttribute(AttributeType.SPEED).get();

        // "冻结状态下…每回合开始时受到等同于三月七60%攻击力的冰属性附加伤害": the payload lands with the state.
        Applied frozen = new Applied(controlWithDamage(1.0));
        frozen.hero.setAttribute(AttributeType.ATTACK, new DoubleValue(2000));
        frozen.fire();
        boolean freezeHasTheName = frozen.enemy.getBuffManager().hasState("ctrl:frozen");
        DotBuff dot = frozen.enemy.getBuffManager().findBuff(DotBuff.class);

        // A resisted freeze deals no ice damage either: the payload is part of the state, not a second effect.
        Applied resisted = new Applied(controlWithDamage(1.0));
        resisted.enemy.setAttribute(AttributeType.EFFECT_RESISTANCE, new DoubleValue(1.0));
        resisted.fire();
        int dotsOnResisted = resisted.enemy.getBuffManager().countBuffs(DotBuff.class);

        // Taking the state off takes its damage with it.
        Applied removed = new Applied(controlWithDamage(1.0));
        removed.hero.setAttribute(AttributeType.ATTACK, new DoubleValue(2000));
        removed.fire();
        int dotsWhileFrozen = removed.enemy.getBuffManager().countBuffs(DotBuff.class);
        removed.enemy.getBuffManager().removeState("ctrl:frozen");
        int dotsAfterRemoval = removed.enemy.getBuffManager().countBuffs(DotBuff.class);

        Assertions.assertAll(
                () -> Assertions.assertTrue(speedWhileEntangled < speedBeforeEntangle, "entanglement slows"),
                () -> Assertions.assertEquals(1, removedStates, "the state is taken off by name"),
                () -> Assertions.assertEquals(speedBeforeEntangle, speedAfterEntangleRemoved, EPS,
                        "and the slow it attached goes with it -- one buff, so the parts cannot come apart"),
                () -> Assertions.assertTrue(freezeHasTheName, "the state landed"),
                () -> Assertions.assertNotNull(dot, "and so did its per-turn damage"),
                () -> Assertions.assertEquals(DamageElement.ICE, dot.getElement(),
                        "the payload is ice damage, as the text says"),
                () -> Assertions.assertEquals(1200, dot.getBaseDamage(), EPS,
                        "60% of her 2000 ATTACK, read when it landed"),
                () -> Assertions.assertEquals(0, dotsOnResisted,
                        "a resisted freeze attaches no damage: the payload is part of the state, not a second effect"),
                () -> Assertions.assertEquals(1, dotsWhileFrozen, "precondition"),
                () -> Assertions.assertEquals(0, dotsAfterRemoval,
                        "one buff owns the state and its payload, so 「解除」 (remove) cannot leave half of it behind"));
    }

    // ==================================================================
    // 4. Fail fast: what a control rule may not say
    // ==================================================================

    /**
     * The load-time rejections, driven through {@code new TriggerTable(...)}.
     *
     * <p>Note: {@code TriggerSpecs.rule(...)} only builds the bean - validation happens when a table compiles the rule,
     * which is why these cases must go through the table. A test that asserted on the bean alone would pass no
     * matter what the loader did.
     */
    @Test
    public void anUnknownControlNameIsRejectedAtLoadTime() {
        IllegalArgumentException rejected = Assertions.assertThrows(IllegalArgumentException.class,
                () -> tableOf(TriggerSpecs.applyControl("冰冻", 1, null, "target")));

        Assertions.assertTrue(rejected.getMessage().contains("冰冻"), rejected.getMessage());
        Assertions.assertTrue(rejected.getMessage().contains("ctrl:frozen"), "the message lists the known states");
    }

    /**
     * A control without a turn count is refused: a state that never ends is not the sentence's "持续1回合".
     */
    @Test
    public void aControlWithoutATurnCountIsRejected() {
        IllegalArgumentException rejected = Assertions.assertThrows(IllegalArgumentException.class,
                () -> tableOf(TriggerSpecs.applyControl("ctrl:frozen", null, null, "target")));

        Assertions.assertTrue(rejected.getMessage().contains("turns"), rejected.getMessage());
    }

    /**
     * A base chance is a fraction greater than 0 and at most 1: 1.5 is not a probability, and 0 would read
     * like 「never」, which is spelled by not writing the rule.
     */
    @Test
    public void aBaseChanceOutsideZeroToOneIsRejected() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> tableOf(TriggerSpecs.applyControl("ctrl:frozen", 1, 1.5, "target")), "a fraction of 1");
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> tableOf(TriggerSpecs.applyControl("ctrl:frozen", 1, 0.0, "target")),
                "0 would read like 「never」, which is spelled by not writing the rule");
    }

    /**
     * An op that reads none of the magnitude fields refuses them rather than pretending.
     */
    @Test
    public void theMagnitudeFieldsAreRejectedOnAControl() {
        com.laosun.aluminium.beans.EffectSpec effect = TriggerSpecs.applyControl("ctrl:frozen", 1, null, "target");
        TriggerSpecs.set(effect, "amount", 100.0);

        IllegalArgumentException rejected = Assertions.assertThrows(IllegalArgumentException.class,
                () -> tableOf(effect));
        Assertions.assertTrue(rejected.getMessage().contains("amount"), rejected.getMessage());
    }

    /**
     * Compiles one {@code APPLY_CONTROL} effect into a table, which is where its validation runs.
     */
    private static TriggerTable tableOf(
            com.laosun.aluminium.beans.EffectSpec effect) {
        return new TriggerTable(0,
                java.util.List.of(TriggerSpecs.rule("ALLY_ATTACK", null, effect)));
    }

    /**
     * One battle for the skill-applied cases: a hero with the rules under test and one enemy.
     *
     * <p>The hero is a placeholder character (no data file), so nothing but the rules built here is in play, and
     * the enemy is Ice Edge - the same fixture the break cases use, so the two paths are compared on one enemy.
     */
    private static final class Applied {
        private final Character hero;
        private final Enemy enemy;
        private final Battle battle;

        private Applied(com.laosun.aluminium.beans.TriggerSpec... rules) {
            this(EnemyFactory.create(1003010, 90, 1), rules);
        }

        private Applied(Enemy enemy, com.laosun.aluminium.beans.TriggerSpec... rules) {
            this.hero = Character.fromAttributes("caster", 10_000, 100, 100, 100);
            this.enemy = enemy;
            // Note: The fixture STATES the two sides of the probability pipeline instead of inheriting them: every
            // monster carries some effect resistance (1003010 has 30%), so "a base chance of 1 lands" is only true once
            // this is zero. The cases that want the resistance to bite set it back (see the two resistance
            // tests); the ones that want a certain landing rely on this line.
            enemy.setAttribute(AttributeType.EFFECT_RESISTANCE, new DoubleValue(0));
            this.hero.setTriggerTable(new TriggerTable(0,
                    java.util.List.of(rules)));
            this.battle = new Battle(java.util.List.of(hero), java.util.List.of(enemy), BattleFixture.seeded());
            battle.startBattle();
        }

        /**
         * Fires the rule with this enemy as the event's subject; returns how many rules ran.
         */
        private int fire() {
            return fire(com.laosun.aluminium.enums.TriggerEvent.ALLY_ATTACK);
        }

        /**
         * The same for any event (some cases need a second event to observe what the first one applied).
         */
        private int fire(com.laosun.aluminium.enums.TriggerEvent event) {
            return battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(event, hero, enemy, 1, 0));
        }
    }

    /**
     * A rule that freezes the event's subject with the given base chance ({@code null} = always).
     */
    private static com.laosun.aluminium.beans.TriggerSpec control(Double baseChance) {
        return control("ctrl:frozen", baseChance);
    }

    private static com.laosun.aluminium.beans.TriggerSpec control(String name, Double baseChance) {
        return TriggerSpecs.rule("ALLY_ATTACK", null, TriggerSpecs.applyControl(name, 1, baseChance, "target"));
    }

    /**
     * March 7th's shape: the freeze plus its own per-turn ice damage (60% of the applier's ATTACK).
     *
     * <p>Note: The payload is stated on the <b>same</b> effect, which is what makes it land only when the freeze does
     * (see {@link #theStateCarriesItsPayloadAndRemovalTakesIt}).
     */
    private static com.laosun.aluminium.beans.TriggerSpec controlWithDamage(Double baseChance) {
        com.laosun.aluminium.beans.EffectSpec effect =
                TriggerSpecs.applyControl("ctrl:frozen", 1, baseChance, "target");
        TriggerSpecs.set(effect, "element", "Ice");
        TriggerSpecs.set(effect, "scale", "self_attr:ATTACK");
        TriggerSpecs.set(effect, "percent", 0.6);
        return TriggerSpecs.rule("ALLY_ATTACK", null, effect);
    }

    /**
     * One enemy made weak to exactly one element, in a battle with a hero who does the breaking.
     */
    private record Fixture(Battle battle, Character hero, Enemy enemy) {

        void breakWith(DamageElement element) {
            battle.reduceToughness(hero, enemy, element, 10_000);   // far more than the bar, so it empties
        }

        double speed() {
            return enemy.getAttribute(AttributeType.SPEED).get();
        }

        /**
         * One action period in action-value units: the same {@code 10000 / speed} the delay is computed from.
         */
        double period() {
            return 10_000.0 / speed();
        }
    }

    private static Fixture fixture(DamageElement weakness) {
        Character hero = Character.fromAttributes("hero", 10_000, 100, 100, 100);
        hero.setAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST, new DoubleValue(0));
        Enemy enemy = EnemyFactory.create(1002011, 90, 1);          // Ice Edge, toughness 60
        enemy.setStanceWeak(Set.of(weakness));                      // make exactly this element able to break it
        return new Fixture(new Battle(List.of(hero), List.of(enemy), BattleFixture.seeded()), hero, enemy);
    }

    /**
     * Action value left before the target acts - the observable the delay really moves.
     */
    private static double timeRemaining(Battle battle, Enemy target) {
        for (Signal signal : battle.queue().snapshot()) {
            if (signal.getCanHit() == target) {
                return battle.queue().getTimeRemaining(signal);
            }
        }
        throw new AssertionError(target.getName() + " is not in the action bar");
    }
}
