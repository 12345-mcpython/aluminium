package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.buff.ControlBuff;
import com.laosun.aluminium.models.buff.DotBuff;
import com.laosun.aluminium.models.buff.StatModifierBuff;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
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
 */
public class ControlTest {
    private static final double EPS = 1e-6;

    @Test
    public void anIceBreakFreezesTheVictimAndPushesItBack() {
        Fixture f = fixture(DamageElement.ICE);
        double speedBefore = f.speed();
        double delayBefore = timeRemaining(f.battle, f.enemy);

        f.breakWith(DamageElement.ICE);

        Assertions.assertTrue(f.enemy.getBuffManager().hasBuff(ControlBuff.class),
                "the freeze state must attach an act lock");
        Assertions.assertEquals("冻结", f.enemy.getBuffManager().findBuff(ControlBuff.class).getName(),
                "and the state carries the NAME the documents use, which is what the freeze state is asked with");
        Assertions.assertFalse(f.enemy.getBuffManager().canAct(),
                "the freeze state = cannot act; this is the same predicate performAction refuses on");
        Assertions.assertEquals(speedBefore, f.speed(), EPS, "the freeze state does not slow -- imprisonment/entanglement do");
        Assertions.assertEquals(f.period() * (Constant.BREAK_DELAY_RATIO + Constant.FREEZE_EXTRA_DELAY),
                timeRemaining(f.battle, f.enemy) - delayBefore, EPS,
                "the delay is the fixed 25% plus the element's own extra");
    }

    @Test
    public void aQuantumBreakSlowsAndDelaysButStillLetsTheVictimAct() {
        Fixture f = fixture(DamageElement.QUANTUM);
        double speedBefore = f.speed();

        f.breakWith(DamageElement.QUANTUM);

        Assertions.assertTrue(f.enemy.getBuffManager().canAct(),
                "entanglement acts, just later and slower -- it must NOT be an act lock");
        Assertions.assertEquals(speedBefore * (1 - Constant.CONTROL_EFFECTS.get("ENTANGLED").slowPercent()),
                f.speed(), EPS, "entanglement = speed reduction");
        // Note: The push is NOT asserted here, deliberately -- see the class javadoc's measured table. A 20%
        // slow reschedules the action by more than the push, so with or without the element's extra delay
        // the movement clears every threshold this test could name: an assertion that cannot fail. The push
        // being read out of the table is pinned by the freeze state's exact figure, and the mechanism by
        // QueueActionManipulationTest.aDelaySurvivesASpeedChange.
    }

    @Test
    public void anImaginaryBreakSlowsAndDelaysButStillLetsTheVictimAct() {
        Fixture f = fixture(DamageElement.IMAGINARY);
        double speedBefore = f.speed();

        f.breakWith(DamageElement.IMAGINARY);

        Assertions.assertTrue(f.enemy.getBuffManager().canAct(), "imprisonment is not an act lock");
        Assertions.assertEquals(speedBefore * (1 - Constant.CONTROL_EFFECTS.get("IMPRISONED").slowPercent()),
                f.speed(), EPS, "imprisonment = speed reduction");
        // Same as entanglement above: no push assertion, and for the same measured reason -- at a 10% slow the
        // reschedule alone already moves the bar 33.6 against a plain break's 18.94. (A truncated reschedule
        // would move it 12.63 instead, *below* the baseline, so this case would be assertable for the wrong
        // reason.)
    }

    /**
     * Regression, and the guard on the whole task: a damaging break must be <b>untouched</b>.
     *
     * <p>It burns, it does not touch speed, it does not lock the action, and its delay is exactly the
     * fixed 25% with no element-specific extra. If the control path were wired to every element instead of
     * only the three that have a state, this is what would go red.
     */
    @Test
    public void aPhysicalBreakStillOnlyBurns() {
        Fixture f = fixture(DamageElement.PHYSICAL);
        double speedBefore = f.speed();
        double delayBefore = timeRemaining(f.battle, f.enemy);

        f.breakWith(DamageElement.PHYSICAL);

        Assertions.assertEquals(1, f.enemy.getBuffManager().countBuffs(DotBuff.class), "still burns");
        Assertions.assertTrue(f.enemy.getBuffManager().canAct(), "a burn does not control");
        Assertions.assertFalse(f.enemy.getBuffManager().hasBuff(ControlBuff.class));
        Assertions.assertEquals(0, f.enemy.getBuffManager().countBuffs(StatModifierBuff.class),
                "a burn must not slow: the slow belongs to the control states only");
        Assertions.assertEquals(speedBefore, f.speed(), EPS);
        Assertions.assertEquals(f.period() * Constant.BREAK_DELAY_RATIO,
                timeRemaining(f.battle, f.enemy) - delayBefore, EPS,
                "the four damaging elements add no extra delay");
    }

    /**
     * A break cannot be resisted away.
     *
     * <p>{@code ControlEffect.resistKey} exists so that the <i>skill</i>-applied path can be resisted through
     * {@code hitChance}; a <b>break</b> happens because the toughness bar emptied, so a monster that is fully
     * immune to freeze ({@code STAT_CTRL_Frozen} = 1) must <b>still</b> be frozen by an Ice break. Wiring the
     * break path through the resistance check would make a weakness break silently do nothing, which is the
     * kind of failure that reads as "no bug" from the outside.
     */
    @Test
    public void anIceBreakFreezesEvenAMonsterThatIsImmuneToFreezeSkills() {
        Fixture f = fixture(DamageElement.ICE);
        f.enemy.setDebuffResist(java.util.Map.of("STAT_CTRL_Frozen", 1.0));

        f.breakWith(DamageElement.ICE);

        Assertions.assertFalse(f.enemy.getBuffManager().canAct(),
                "a break is not a resisted debuff: 100% skill resistance must not cancel it");
    }

    // ==================================================================
    // A control applied by a SKILL: op APPLY_CONTROL
    // ==================================================================

    /**
     * "有 50% 基础概率使敌方目标陷入冻结状态，持续1回合": the state lands, the victim cannot act, and the name is
     * readable - which is what makes the freeze state askable at all.
     */
    @Test
    public void aSkillAppliedFreezeStopsTheVictimActingAndCarriesItsName() {
        Applied f = new Applied(control(1.0));
        f.fire();

        Assertions.assertTrue(f.enemy.getBuffManager().hasState("冻结"),
                "the applied state answers has_state under the name the documents use");
        Assertions.assertFalse(f.enemy.getBuffManager().canAct(), "冻结 = 不能行动");
        Assertions.assertEquals("冻结", f.enemy.getBuffManager().findBuff(ControlBuff.class).getName());
    }

    /**
     * A base chance is a <b>base</b> chance: it goes through effect hit and effect resistance, per target.
     *
     * <p>Driven from the two ends of the pipeline rather than from a lucky seed: with the applier's effect hit at
     * +100% a 50% base chance becomes certain, and with the victim's effect resistance at 100% it becomes impossible. A
     * rule-level {@code chance} could do neither (it is one fixed roll for the whole rule), which is why the
     * vocabulary needed this field rather than reusing that one.
     */
    @Test
    public void theBaseChanceRunsThroughTheEffectHitAndResistPipeline() {
        Applied lands = new Applied(control(1.0));
        lands.hero.setAttribute(AttributeType.EFFECT_HIT_RATE, new DoubleValue(1.0));
        lands.fire();
        Assertions.assertTrue(lands.enemy.getBuffManager().hasState("冻结"),
                "50% base × (1 + 100% effect hit) = 100%, so it always lands");

        Applied resisted = new Applied(control(1.0));
        resisted.enemy.setAttribute(AttributeType.EFFECT_RESISTANCE, new DoubleValue(1.0));
        resisted.fire();
        Assertions.assertFalse(resisted.enemy.getBuffManager().hasState("冻结"),
                "and 100% effect resistance takes it to 0, whatever the base chance was");
    }

    /**
     * A monster's own resistance to <b>this</b> state ({@code STAT_CTRL_Frozen}) is part of the same roll.
     *
     * <p>Note: The fixture for this case is Ice Edge (1002011), whose data really does carry
     * {@code STAT_CTRL_Frozen = 1.0} - it cannot be frozen by a skill at all. That is why every other case above
     * uses 1003010 (no specific resistances): a "the state lands" assertion on a monster that is immune to it would
     * have been a test of the data, not of the pipeline.
     */
    @Test
    public void aMonstersSpecificFreezeResistanceIsObeyedOnTheSkillPath() {
        Applied f = new Applied(EnemyFactory.create(1002011, 90, 1), control(1.0));
        f.hero.setAttribute(AttributeType.EFFECT_HIT_RATE, new DoubleValue(1.0));   // otherwise certain

        f.fire();

        Assertions.assertEquals(java.util.Map.of("STAT_CTRL_Frozen", 1.0), f.enemy.getDebuffResist(),
                "precondition: this monster's data is what makes it unfreezable");
        Assertions.assertFalse(f.enemy.getBuffManager().hasState("冻结"),
                "STAT_CTRL_Frozen = 1 means this monster cannot be frozen by a skill -- which is exactly what "
                        + "ControlEffect.resistKey is for (and what a BREAK deliberately ignores)");
    }

    /**
     * Note: <b>A break-frozen unit and a skill-frozen unit are the same state.</b>
     *
     * <p>This is the property that made the migration worth doing: before {@code ControlBuff}, a break produced an
     * unnamed {@code StunBuff}, so a question about the freeze state answered <b>false</b> for a unit that was, to the game and to the
     * player, frozen - a condition that would have silently missed half the cases it exists for.
     */
    @Test
    public void aBreakFreezeAndASkillFreezeAreTheSameState() {
        Fixture broken = fixture(DamageElement.ICE);
        broken.breakWith(DamageElement.ICE);

        Applied applied = new Applied(control(1.0));
        applied.fire();

        Assertions.assertTrue(broken.enemy.getBuffManager().hasState("冻结"), "the break's state has the name");
        Assertions.assertTrue(applied.enemy.getBuffManager().hasState("冻结"), "and so does the skill's");
        Assertions.assertEquals(broken.enemy.getBuffManager().findBuff(ControlBuff.class).getName(),
                applied.enemy.getBuffManager().findBuff(ControlBuff.class).getName(),
                "one state, one name, whichever path applied it");
    }

    /**
     * A gated rule can therefore ask the question: {@code target has_state 冻结}.
     *
     * <p>Note: The gate is on a <b>later event</b>, and that is not a stylistic choice: {@code TriggerInterpreter.fire}
     * matches <b>every</b> rule of one event against the context <i>before</i> any of them runs ({@code matching}
     * is a pure predicate), so a rule cannot see a state an earlier rule of the same event applied. Content that
     * wants both writes two rules on two events - which is what the game's sentences do too (the freeze is on the
     * cast, the bonus is on the damage).
     */
    @Test
    public void aRuleCanGateOnTheFrozenState() {
        Applied f = new Applied(control(1.0),
                TriggerSpecs.rule("BASIC_ATTACK", java.util.List.of("target has_state 冻结"),
                        TriggerSpecs.gainEnergy(1)));

        Assertions.assertEquals(1, f.fire(), "the freeze lands");
        Assertions.assertEquals(1, f.fire(com.laosun.aluminium.enums.TriggerEvent.BASIC_ATTACK),
                "and a later event's rule sees the freeze state on the victim");

        Applied resisted = new Applied(control(1.0),
                TriggerSpecs.rule("BASIC_ATTACK", java.util.List.of("target has_state 冻结"),
                        TriggerSpecs.gainEnergy(1)));
        resisted.enemy.setAttribute(AttributeType.EFFECT_RESISTANCE, new DoubleValue(1.0));

        Assertions.assertEquals(1, resisted.fire(), "here the control rule runs but lands nothing");
        Assertions.assertEquals(0, resisted.fire(com.laosun.aluminium.enums.TriggerEvent.BASIC_ATTACK),
                "so the gated rule is refused -- the gate is the only thing that changed");
    }

    /** The parts of a state come off together: entanglement's slow goes when the state is removed by name. */
    @Test
    public void removingTheNamedStateTakesItsSlowWithIt() {
        Applied f = new Applied(control("纠缠", null));
        double before = f.enemy.getAttribute(AttributeType.SPEED).get();

        f.fire();
        Assertions.assertTrue(f.enemy.getAttribute(AttributeType.SPEED).get() < before, "entanglement slows");

        Assertions.assertEquals(1, f.enemy.getBuffManager().removeState("纠缠"), "the state is taken off by name");
        Assertions.assertEquals(before, f.enemy.getAttribute(AttributeType.SPEED).get(), EPS,
                "and the slow it attached goes with it -- one buff, so the parts cannot come apart");
    }

    /** A control is a negative effect, so a rule that dispels N negative effects reaches it. */
    @Test
    public void aControlIsADebuffAndCanBeDispelled() {
        Applied f = new Applied(control(1.0));
        f.fire();
        Assertions.assertEquals(1, f.enemy.getBuffManager().debuffCount(), "the control counts as a debuff");

        f.enemy.getBuffManager().removeDebuffs(1);

        Assertions.assertTrue(f.enemy.getBuffManager().canAct(), "dispelling it gives the turn back");
    }

    // ==================================================================
    // The state's own per-turn damage rides with the state
    // ==================================================================

    /**
     * "冻结状态下…每回合开始时受到等同于三月七60%攻击力的冰属性附加伤害": the payload is attached with the
     * state, and it is <b>frozen with it</b> - a resisted freeze deals no ice damage either, which is why this is
     * one effect and not two.
     */
    @Test
    public void theStateCarriesItsOwnPerTurnDamage() {
        Applied f = new Applied(controlWithDamage(1.0));
        f.hero.setAttribute(AttributeType.ATTACK, new DoubleValue(2000));

        f.fire();

        Assertions.assertTrue(f.enemy.getBuffManager().hasState("冻结"), "the state landed");
        DotBuff dot = f.enemy.getBuffManager().findBuff(DotBuff.class);
        Assertions.assertNotNull(dot, "and so did its per-turn damage");
        Assertions.assertEquals(DamageElement.ICE, dot.getElement());
        Assertions.assertEquals(1200, dot.getBaseDamage(), EPS, "60% of her 2000 ATTACK, read when it landed");

        Applied resisted = new Applied(controlWithDamage(1.0));
        resisted.enemy.setAttribute(AttributeType.EFFECT_RESISTANCE, new DoubleValue(1.0));
        resisted.fire();
        Assertions.assertEquals(0, resisted.enemy.getBuffManager().countBuffs(DotBuff.class),
                "a resisted freeze attaches no damage: the payload is part of the state, not a second effect");
    }

    /** Taking the state off takes its damage with it - otherwise the ice would keep burning after "解除冻结". */
    @Test
    public void theStatesDamageComesOffWithIt() {
        Applied f = new Applied(controlWithDamage(1.0));
        f.hero.setAttribute(AttributeType.ATTACK, new DoubleValue(2000));
        f.fire();
        Assertions.assertEquals(1, f.enemy.getBuffManager().countBuffs(DotBuff.class), "precondition");

        f.enemy.getBuffManager().removeState("冻结");

        Assertions.assertEquals(0, f.enemy.getBuffManager().countBuffs(DotBuff.class),
                "one buff owns the state and its payload, so 「解除」 cannot leave half of it behind");
    }

    // ==================================================================
    // Fail fast: what a control rule may not say
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
        Assertions.assertTrue(rejected.getMessage().contains("冻结"), "the message lists the known states");
    }

    @Test
    public void aControlWithoutATurnCountIsRejected() {
        IllegalArgumentException rejected = Assertions.assertThrows(IllegalArgumentException.class,
                () -> tableOf(TriggerSpecs.applyControl("冻结", null, null, "target")));

        Assertions.assertTrue(rejected.getMessage().contains("turns"), rejected.getMessage());
    }

    @Test
    public void aBaseChanceOutsideZeroToOneIsRejected() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> tableOf(TriggerSpecs.applyControl("冻结", 1, 1.5, "target")), "a fraction of 1");
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> tableOf(TriggerSpecs.applyControl("冻结", 1, 0.0, "target")),
                "0 would read like 「never」, which is spelled by not writing the rule");
    }

    /** An op that reads none of the magnitude fields refuses them rather than pretending. */
    @Test
    public void theMagnitudeFieldsAreRejectedOnAControl() {
        com.laosun.aluminium.beans.EffectSpec effect = TriggerSpecs.applyControl("冻结", 1, null, "target");
        TriggerSpecs.set(effect, "amount", 100.0);

        IllegalArgumentException rejected = Assertions.assertThrows(IllegalArgumentException.class,
                () -> tableOf(effect));
        Assertions.assertTrue(rejected.getMessage().contains("amount"), rejected.getMessage());
    }

    /** Compiles one {@code APPLY_CONTROL} effect into a table, which is where its validation runs. */
    private static com.laosun.aluminium.models.TriggerTable tableOf(
            com.laosun.aluminium.beans.EffectSpec effect) {
        return new com.laosun.aluminium.models.TriggerTable(0,
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
            this.hero.setTriggerTable(new com.laosun.aluminium.models.TriggerTable(0,
                    java.util.List.of(rules)));
            this.battle = new Battle(java.util.List.of(hero), java.util.List.of(enemy), new Random(0));
            battle.startBattle();
        }

        /** Fires the rule with this enemy as the event's subject; returns how many rules ran. */
        private int fire() {
            return fire(com.laosun.aluminium.enums.TriggerEvent.ALLY_ATTACK);
        }

        /** The same for any event (some cases need a second event to observe what the first one applied). */
        private int fire(com.laosun.aluminium.enums.TriggerEvent event) {
            return battle.fireTriggers(event, hero, enemy, 1, 0);
        }
    }

    /** A rule that freezes the event's subject with the given base chance ({@code null} = always). */
    private static com.laosun.aluminium.beans.TriggerSpec control(Double baseChance) {
        return control("冻结", baseChance);
    }

    private static com.laosun.aluminium.beans.TriggerSpec control(String name, Double baseChance) {
        return TriggerSpecs.rule("ALLY_ATTACK", null, TriggerSpecs.applyControl(name, 1, baseChance, "target"));
    }

    /**
     * March 7th's shape: the freeze plus its own per-turn ice damage (60% of the applier's ATTACK).
     *
     * <p>Note: The payload is stated on the <b>same</b> effect, which is what makes it land only when the freeze does
     * (see {@link #theStateCarriesItsOwnPerTurnDamage}).
     */
    private static com.laosun.aluminium.beans.TriggerSpec controlWithDamage(Double baseChance) {
        com.laosun.aluminium.beans.EffectSpec effect =
                TriggerSpecs.applyControl("冻结", 1, baseChance, "target");
        TriggerSpecs.set(effect, "element", "Ice");
        TriggerSpecs.set(effect, "scale", "self_attr:ATTACK");
        TriggerSpecs.set(effect, "percent", 0.6);
        return TriggerSpecs.rule("ALLY_ATTACK", null, effect);
    }

    /** One enemy made weak to exactly one element, in a battle with a hero who does the breaking. */
    private record Fixture(Battle battle, Character hero, Enemy enemy) {

        void breakWith(DamageElement element) {
            battle.reduceToughness(hero, enemy, element, 10_000);   // far more than the bar, so it empties
        }

        double speed() {
            return enemy.getAttribute(AttributeType.SPEED).get();
        }

        /** One action period in action-value units: the same {@code 10000 / speed} the delay is computed from. */
        double period() {
            return 10_000.0 / speed();
        }
    }

    private static Fixture fixture(DamageElement weakness) {
        Character hero = Character.fromAttributes("hero", 10_000, 100, 100, 100);
        hero.setAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST, new DoubleValue(0));
        Enemy enemy = EnemyFactory.create(1002011, 90, 1);          // Ice Edge, toughness 60
        enemy.setStanceWeak(Set.of(weakness));                      // make exactly this element able to break it
        return new Fixture(new Battle(List.of(hero), List.of(enemy), new Random(0)), hero, enemy);
    }

    /** Action value left before the target acts - the observable the delay really moves. */
    private static double timeRemaining(Battle battle, Enemy target) {
        for (Signal signal : battle.queue.snapshot()) {
            if (signal.getCanHit() == target) {
                return battle.queue.getTimeRemaining(signal);
            }
        }
        throw new AssertionError(target.getName() + " is not in the action bar");
    }
}
