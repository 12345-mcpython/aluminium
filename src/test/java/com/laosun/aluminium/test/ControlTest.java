package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.EnemyFactory;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.buffs.DotBuff;
import com.laosun.aluminium.models.buffs.StatModifierBuff;
import com.laosun.aluminium.models.buffs.StunBuff;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * P10-2 acceptance: the three non-damaging break elements leave a control state behind, and the four
 * damaging ones still do exactly what they did before.
 *
 * <p><b>What "control" means here was decided by the data, not by the plan.</b> The plan said "冻结期受伤害
 * +30%"; the encyclopedia text says something else, the same way in six independent entries —
 * {@code "冻结状态下，敌方目标不能行动同时每回合开始时受到…冰属性伤害"} — and for the other two,
 * {@code "禁锢状态下，敌方目标行动延后#2%，速度降低#4%"} (瓦尔特) and
 * {@code "「纠缠」会使敌人行动延后，并在敌人下次行动时对其造成额外的量子属性伤害"}. So:
 *
 * <table border="1">
 *   <caption>the three states, as the text describes them</caption>
 *   <tr><th>element</th><th>cannot act?</th><th>slows?</th><th>extra delay?</th></tr>
 *   <tr><td>Ice → 冻结</td><td><b>yes</b></td><td>no</td><td>yes</td></tr>
 *   <tr><td>Quantum → 纠缠</td><td>no</td><td>yes</td><td>yes</td></tr>
 *   <tr><td>Imaginary → 禁锢</td><td>no</td><td>yes</td><td>yes</td></tr>
 * </table>
 *
 * <p>The damage component of 冻结 (每回合冰伤) and 纠缠 (下次行动时量子伤) is <b>real but not built here</b>:
 * it is a DOT, {@code BreakEffect.dotRatio} is its field, and no source states the break-applied ratio. It
 * stays a recorded TODO instead of an invented number — and {@code BreakEffectTableTest} fails if someone
 * fills one in without deciding.
 *
 * <p>These tests drive {@link Battle#reduceToughness} directly rather than casting a skill: it is the single
 * toughness-reduction entry point and it takes the break element explicitly, so the element under test is
 * the element that breaks — no character of that element needs to exist for Ice/Quantum/Imaginary to be
 * testable.
 *
 * <p><b>Why the push is asserted to the digit for 冻结, against a measured baseline for 禁锢, and not at
 * all for 纠缠.</b> A speed change <i>reschedules</i> the pending action ({@code Signal.refreshSpeed}
 * recomputes {@code nextActionTime} from the progress the unit has already made), and the reschedule is
 * larger the bigger the slow is. On this fixture, measured:
 *
 * <table border="1">
 *   <caption>action-value movement of the same break, by how much it slows</caption>
 *   <tr><th>case</th><th>movement</th><th>assertable?</th></tr>
 *   <tr><td>plain break, no slow, no extra delay</td><td>18.94 (exactly 25%)</td><td>yes — the regression test pins it</td></tr>
 *   <tr><td>冻结, no slow, +50% delay</td><td>56.82</td><td>yes — nothing recomputes</td></tr>
 *   <tr><td>禁锢, 10% slow, without the extra delay</td><td>12.63</td><td>yes — below the 18.94 baseline</td></tr>
 *   <tr><td>禁锢, 10% slow, with the +20% delay</td><td>29.46</td><td>yes — above it</td></tr>
 *   <tr><td>纠缠, 20% slow, without the extra delay</td><td>28.41</td><td><b>no — already above the baseline</b></td></tr>
 *   <tr><td>纠缠, 20% slow, with the +20% delay</td><td>47.35</td><td>no — indistinguishable in kind</td></tr>
 * </table>
 *
 * <p>So for 纠缠 a delay assertion could not fail, and one was removed rather than kept as decoration.
 * That is a real limitation of the engine's delay/slow composition (the same family as CODE_REVIEW L-26),
 * not of the table: what proves the element's extra delay is read at all is 冻结's exact figure plus
 * {@code BreakEffectTableTest} requiring {@code delayPercent > 0} of every control element.
 */
public class ControlTest {
    private static final double EPS = 1e-6;

    @Test
    public void anIceBreakFreezesTheVictimAndPushesItBack() {
        Fixture f = fixture(DamageElement.ICE);
        double speedBefore = f.speed();
        double delayBefore = timeRemaining(f.battle, f.enemy);

        f.breakWith(DamageElement.ICE);

        Assertions.assertTrue(f.enemy.getBuffManager().hasBuff(StunBuff.class),
                "冻结 must attach an act lock");
        Assertions.assertFalse(f.enemy.getBuffManager().canAct(),
                "冻结 = 不能行动; this is the same predicate performAction refuses on");
        Assertions.assertEquals(speedBefore, f.speed(), EPS, "冻结 does not slow -- 禁锢/纠缠 do");
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
                "纠缠 acts, just later and slower -- it must NOT be an act lock");
        Assertions.assertEquals(speedBefore * (1 - Constant.CONTROL_EFFECTS.get("ENTANGLED").slowPercent()),
                f.speed(), EPS, "纠缠 = 速度降低");
        // ⚠ The push is NOT asserted here, deliberately -- see the class javadoc. A 20% slow reschedules the
        // action by more than a plain break pushes, so with or without the element's extra delay the movement
        // clears every threshold this test could name: it would be an assertion that cannot fail. 禁锢 below
        // is assertable only because its 10% slow is small enough that the reschedule alone stays below the
        // plain-break baseline. What pins the extra delay being read out of the table is 冻结's exact figure.
    }

    @Test
    public void anImaginaryBreakSlowsAndDelaysButStillLetsTheVictimAct() {
        Fixture f = fixture(DamageElement.IMAGINARY);
        double speedBefore = f.speed();
        double delayBefore = timeRemaining(f.battle, f.enemy);

        f.breakWith(DamageElement.IMAGINARY);

        Assertions.assertTrue(f.enemy.getBuffManager().canAct(), "禁锢 is not an act lock");
        Assertions.assertEquals(speedBefore * (1 - Constant.CONTROL_EFFECTS.get("IMPRISONED").slowPercent()),
                f.speed(), EPS, "禁锢 = 速度降低");
        Assertions.assertTrue(timeRemaining(f.battle, f.enemy) - delayBefore
                        > f.plainBreakPush(speedBefore),
                "禁锢 = 行动延后: must move the bar strictly further than a plain break would; got "
                        + (timeRemaining(f.battle, f.enemy) - delayBefore) + " against a plain-break push of "
                        + f.plainBreakPush(speedBefore));
    }

    /**
     * Regression, and the guard on the whole task: a damaging break must be <b>untouched</b>.
     *
     * <p>It burns (P4-5), it does not touch speed, it does not lock the action, and its delay is exactly the
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
        Assertions.assertFalse(f.enemy.getBuffManager().hasBuff(StunBuff.class));
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

        /**
         * How far a <b>plain</b> break pushes this same enemy back: {@code BREAK_DELAY_RATIO} of one action
         * period at its pre-break speed. {@code aPhysicalBreakStillOnlyBurns} measures exactly this value
         * against this same fixture, so it is a measured baseline rather than a formula invented here.
         */
        double plainBreakPush(double speedBefore) {
            return 10_000.0 / speedBefore * Constant.BREAK_DELAY_RATIO;
        }
    }

    private static Fixture fixture(DamageElement weakness) {
        Character hero = Character.fromAttributes("hero", 10_000, 100, 100, 100);
        hero.setAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST, new DoubleValue(0));
        Enemy enemy = EnemyFactory.create(1002011, 90, 1);          // 冰锋, toughness 60
        enemy.setStanceWeak(Set.of(weakness));                      // make exactly this element able to break it
        return new Fixture(new Battle(List.of(hero), List.of(enemy), new Random(0)), hero, enemy);
    }

    /** Action value left before the target acts — the observable the delay really moves. */
    private static double timeRemaining(Battle battle, Enemy target) {
        for (Signal signal : battle.queue.snapshot()) {
            if (signal.getCanHit() == target) {
                return battle.queue.getTimeRemaining(signal);
            }
        }
        throw new AssertionError(target.getName() + " is not in the action bar");
    }
}
