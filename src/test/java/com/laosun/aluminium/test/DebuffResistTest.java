package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DebuffClass;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.buff.ClassResistBuff;
import com.laosun.aluminium.models.buff.ControlBuff;
import com.laosun.aluminium.models.buff.DotBuff;
import com.laosun.aluminium.models.buff.TauntBuff;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 「抵抗<b>控制类</b>负面状态的概率提高35%」 / 「免疫<b>控制类</b>负面状态」 — protection against a whole <b>family</b>.
 *
 * <p><b>Why the vocabulary needed it.</b> The engine could already be resistant to <i>one named state</i>
 * ({@code STAT_CTRL_Frozen} and friends, straight from a monster's own data). The corpus asks for something else:
 * 8 of the 97 documents say a unit is <b>immune to the control class</b> (万敌's 【血仇】, 卡厄斯兰那, 长夜月's 忆灵
 * 「长夜」, 银狼LV.999's 【防火墙】, 小伊卡…) and two more say it is <b>35% / 50% more likely to resist</b> a class
 * (克拉拉 守护, 1008 坚韧). A list of keys cannot say that — a control written tomorrow would fall outside the list.
 *
 * <p><b>One mechanism, both spellings.</b> A class resistance multiplies the remaining chance by {@code 1 − r}, so
 * {@code percent: 0.35} is 「提高35%」 and {@code percent: 1.0} is 免疫 — the same mechanic at its limit, which is why
 * one op covers both.
 *
 * <p>⚠ The roll lives in {@code Battle.tryApplyDebuff} and happens <b>even when a rule states no probability</b>: an
 * unstated chance is a 100% <i>base</i> chance, and without that rule every 「免疫控制类负面状态」 clause would have been
 * silently ineffective against exactly the controls the documents write without a number.
 */
public class DebuffResistTest {
    private static final double EPS = 1e-9;

    private static final int OWNER = 1002;
    private static final int LEVEL = 80;

    /**
     * ⚠ Not 1002011. That one is 冰锋, whose own data carries {@code STAT_CTRL_Frozen = 1.0} — a 「the state lands」
     * assertion on it would pass for the wrong reason (and did, in this suite's first draft: 免疫 was indistinguishable
     * from the monster's data). 1003010 has no specific resistances, like {@code ControlTest}'s landing cases.
     */
    private static final int MONSTER = 1003010;

    /**
     * The arithmetic, driven through the real pipeline with a <b>pinned generator</b>.
     *
     * <p>With the base chance at 1.0 and the enemy's 效果抵抗 at 0, the roll is {@code rng < (1 − classResistance)}: a
     * generator that always answers 0.7 therefore lands with no resistance, and fails to land at 0.35 (0.65 left) or
     * 1.0 (nothing left). That is the whole claim — and it fails if the resistance is added anywhere but the roll.
     */
    @Test
    public void theClassResistanceRemovesThatShareOfTheChance() {
        Assertions.assertTrue(freezeLands(0.0, 0.7), "no resistance: 0.7 < 1.0, so it lands");
        Assertions.assertFalse(freezeLands(0.35, 0.7), "35% resisted leaves 0.65, and 0.7 is above it");
        Assertions.assertTrue(freezeLands(0.35, 0.6), "…while 0.6 is below 0.65 and therefore still lands");
        Assertions.assertFalse(freezeLands(1.0, 0.0), "1.0 is 免疫: nothing lands, not even on a 0 roll");
    }

    /** The two classes do not cross: being immune to control says nothing about 灼烧. */
    @Test
    public void immunityToControlDoesNotStopTheBurn() {
        Fixture f = new Fixture(0.0, resist(1.0), TriggerSpecs.dot("Fire", 500.0, null, null, 2, null));

        f.fire();

        Assertions.assertEquals(1, f.enemy.getBuffManager().countBuffs(DotBuff.class),
                "「免疫控制类负面状态」 is about 控制, and a burn is 持续伤害类");
    }

    /** And the immunity really stops a control the rule states <b>no</b> probability for. */
    @Test
    public void immunityStopsAControlTheRuleStatesNoProbabilityFor() {
        Fixture f = new Fixture(0.0, resist(1.0), TriggerSpecs.applyControl("冻结", 1, null, "target"));

        f.fire();

        Assertions.assertEquals(0, f.enemy.getBuffManager().countBuffs(ControlBuff.class));
        Assertions.assertFalse(f.enemy.getBuffManager().hasState("冻结"));
        Assertions.assertTrue(f.enemy.getBuffManager().canAct(), "and the victim keeps its turn");
    }

    /** Two appliers add: 35% + 35% is 70%, not two independent rolls (a different curve). */
    @Test
    public void twoResistancesOfTheSameClassAddAndClampAtOne() {
        Fixture f = new Fixture();

        f.resist(DebuffClass.CONTROL, 0.35, f.owner);
        f.resist(DebuffClass.CONTROL, 0.35, f.applier(2));
        Assertions.assertEquals(0.7, f.enemy.getBuffManager().debuffResistOf(DebuffClass.CONTROL), EPS);

        f.resist(DebuffClass.CONTROL, 0.35, f.applier(3));
        Assertions.assertEquals(1.0, f.enemy.getBuffManager().debuffResistOf(DebuffClass.CONTROL), EPS,
                "0.35 × 3 = 1.05, and a resistance beyond total is still total");
        Assertions.assertEquals(0.0, f.enemy.getBuffManager().debuffResistOf(DebuffClass.DOT), EPS,
                "the other class is untouched");
    }

    /**
     * ⚠ Identity is (class, <b>applier</b>): a re-application from one applier refreshes <b>its own</b> contribution
     * instead of adding a second one, while the other applier's stands.
     *
     * <p>That is what keeps 克拉拉's permanent 35% 守护 alive when a one-turn 【防火墙】-style immunity lands on her and
     * later expires: with class alone as the identity, the timed 100% would have replaced the trace and taken it with
     * it. The numbers below are chosen so a fourth buff would be visible (0.2 + 0.3 = 0.5, and 0.2 + 0.3 + 0.2 = 0.7).
     */
    @Test
    public void reapplyingFromTheSameApplierRefreshesItsOwnContribution() {
        Fixture f = new Fixture();

        f.resist(DebuffClass.CONTROL, 0.2, f.owner);
        f.resist(DebuffClass.CONTROL, 0.3, f.applier(2));
        Assertions.assertEquals(0.5, f.enemy.getBuffManager().debuffResistOf(DebuffClass.CONTROL), EPS);

        f.resist(DebuffClass.CONTROL, 0.2, f.owner);
        Assertions.assertEquals(0.5, f.enemy.getBuffManager().debuffResistOf(DebuffClass.CONTROL), EPS,
                "the same applier again is a refresh, not a second contribution");
        Assertions.assertEquals(2, f.enemy.getBuffManager().countBuffs(ClassResistBuff.class));

        f.resist(DebuffClass.CONTROL, 0.6, f.applier(2));
        Assertions.assertEquals(0.8, f.enemy.getBuffManager().debuffResistOf(DebuffClass.CONTROL), EPS,
                "…and a stronger one from that applier replaces its own previous number");
        Assertions.assertEquals(2, f.enemy.getBuffManager().countBuffs(ClassResistBuff.class));
    }

    /** A timed resistance expires on its carrier's turns, like every other timed buff. */
    @Test
    public void aTimedResistanceExpires() {
        Fixture f = new Fixture(0.0, resist(1.0, 1));

        f.fire();

        Assertions.assertEquals(1.0, f.enemy.getBuffManager().debuffResistOf(DebuffClass.CONTROL), EPS);
        TestTurns.take(f.battle, f.enemy);
        Assertions.assertEquals(0.0, f.enemy.getBuffManager().debuffResistOf(DebuffClass.CONTROL), EPS,
                "「【防火墻】状态下…免疫」 lasts a turn — a buff covers both that and a permanent 行迹");
    }

    /** A permanent one is never ticked, so it holds for the whole fight. */
    @Test
    public void aPermanentResistanceHolds() {
        Fixture f = new Fixture(0.0, resistPermanent(0.35));

        f.fire();
        TestTurns.take(f.battle, f.enemy);
        TestTurns.take(f.battle, f.enemy);

        Assertions.assertEquals(0.35, f.enemy.getBuffManager().debuffResistOf(DebuffClass.CONTROL), EPS);
    }

    /** It is a <b>positive</b> effect on its bearer, so 「解除 N 个负面效果」 must not take it off. */
    @Test
    public void theResistanceIsNotADebuff() {
        Fixture f = new Fixture(0.0, resist(0.35));

        f.fire();

        Assertions.assertEquals(0, f.enemy.getBuffManager().debuffCount(), "carrying it is good for you");
        Assertions.assertEquals(0, f.enemy.getBuffManager().removeDebuffs(1), "so a cleanse ignores it");
        Assertions.assertEquals(0.35, f.enemy.getBuffManager().debuffResistOf(DebuffClass.CONTROL), EPS);
    }

    /**
     * ⚠ The taunt goes through the same pipeline now.
     *
     * <p>「使目标陷入嘲讽状态」 states no probability, i.e. a 100% <b>base</b> chance — which the game still runs through
     * 效果命中 / 效果抵抗. The engine used to attach the marker unconditionally, which no document says; the fix is
     * pinned here by giving the victim full 效果抵抗.
     */
    @Test
    public void theTauntIsRolledLikeEveryOtherNegativeState() {
        Fixture f = new Fixture(0.0, TriggerSpecs.taunt(1));
        f.enemy.setAttribute(AttributeType.EFFECT_RESISTANCE, new DoubleValue(1.0));

        f.fire();

        Assertions.assertEquals(0, f.enemy.getBuffManager().countBuffs(TauntBuff.class),
                "an unconditional marker would still be there -- the roll is what keeps it out");
    }

    /**
     * A state that is in no class is not covered by any class resistance — the safe default.
     *
     * <p>⚠ The taunt is the live example, and it is a <b>registered gap</b> rather than an answer: 「嘲讽」 is a control
     * in the game's own vocabulary, but this engine has not decided which family it belongs to, so 免疫控制类 does not
     * stop it yet. Guessing would be worse than saying so.
     */
    @Test
    public void aClassImmunityDoesNotCoverAStateInNoClass() {
        Fixture f = new Fixture(0.0, resist(1.0), TriggerSpecs.taunt(1));

        f.fire();

        Assertions.assertEquals(1, f.enemy.getBuffManager().countBuffs(TauntBuff.class),
                "the taunt is in no class yet, so 「免疫控制类」 leaves it alone (registered in ROADMAP)");
    }

    // ==================================================================
    // Fail fast
    // ==================================================================

    @Test
    public void theClassMustBeOneTheDocumentsName() {
        IllegalArgumentException missing = Assertions.assertThrows(IllegalArgumentException.class,
                () -> tableOf(resistEffect(null, 0.35, 1, null)));
        Assertions.assertTrue(missing.getMessage().contains("kind"), missing.getMessage());

        IllegalArgumentException unknown = Assertions.assertThrows(IllegalArgumentException.class,
                () -> tableOf(resistEffect("debuff", 0.35, 1, null)));
        Assertions.assertTrue(unknown.getMessage().contains("debuff"), unknown.getMessage());
        Assertions.assertTrue(unknown.getMessage().contains("control"), "the message lists the known classes");
    }

    @Test
    public void theResistanceMustBeAFractionOfOne() {
        IllegalArgumentException zero = Assertions.assertThrows(IllegalArgumentException.class,
                () -> tableOf(resistEffect("control", 0.0, 1, null)));
        Assertions.assertTrue(zero.getMessage().contains("percent"), zero.getMessage());

        Assertions.assertThrows(IllegalArgumentException.class,
                () -> tableOf(resistEffect("control", 1.5, 1, null)), "more than total is not a probability");
    }

    // ==================================================================
    // helpers
    // ==================================================================

    /**
     * Whether a control lands on the fixture's enemy, with a class resistance standing and a pinned generator.
     *
     * <p>⚠ 效果抵抗 = 0 and 效果命中 = +100% so that "a certain base chance" really is certain: otherwise the case would
     * be measuring 冰锋's own data. The generator is the only randomness left, which is what makes the boundary
     * (0.65 vs 0.7) an assertion instead of a coin flip.
     */
    private static boolean freezeLands(double classResist, double roll) {
        Fixture f = new Fixture(roll, TriggerSpecs.applyControl("冻结", 1, null, "target"));
        if (classResist > 0) {
            f.resist(DebuffClass.CONTROL, classResist);
        }
        f.fire();
        return f.enemy.getBuffManager().hasState("冻结");
    }

    /** One battle: the rule under test fires at the enemy, whose own numbers are stated rather than inherited. */
    private static final class Fixture {
        private final Character owner;
        private final Enemy enemy;
        private final Battle battle;

        /** Stand-in appliers for the cases about how two contributions relate; see {@link #applier(int)}. */
        private final List<CanHit> standIns = new ArrayList<>();

        private Fixture(double roll, EffectSpec... effects) {
            this.owner = CharacterFactory.create(OWNER, LEVEL);
            this.owner.setAttribute(AttributeType.EFFECT_HIT_RATE, new DoubleValue(1.0));
            this.enemy = EnemyFactory.create(MONSTER, 90, 1);
            this.enemy.setAttribute(AttributeType.EFFECT_RESISTANCE, new DoubleValue(0));
            this.owner.setTriggerTable(effects.length == 0
                    ? new TriggerTable(OWNER, List.of())
                    : new TriggerTable(OWNER, List.of(TriggerSpecs.rule("ALLY_ATTACK", null, effects))));
            this.battle = new Battle(List.of(owner), List.of(enemy), fixed(roll));
            battle.startBattle();
        }

        private Fixture() {
            this(0.0, new EffectSpec[0]);
        }

        /** Attaches a class resistance directly, for the cases that are about the resistance itself. */
        private void resist(DebuffClass kind, double percent) {
            resist(kind, percent, owner);
        }

        /**
         * Attaches one <b>contribution</b> from a named applier — the pair {@code ClassResistBuff} is identified by.
         *
         * @param source who grants it; a stand-in is enough, because nothing but its identity is ever compared
         */
        private void resist(DebuffClass kind, double percent, CanHit source) {
            ClassResistBuff buff = new ClassResistBuff(kind, percent, 5);
            buff.setSource(source);
            enemy.getBuffManager().addBuff(buff);
        }

        /**
         * A stand-in applier, <b>cached by index</b>: it never enters the battle, because only its identity matters.
         *
         * <p>⚠ The cache is the point. {@code applier(2) == applier(2)} has to hold, or a rule about "the same
         * applier again refreshes" would be measuring two different ones.
         */
        private CanHit applier(int index) {
            while (standIns.size() < index) {
                standIns.add(EnemyFactory.create(MONSTER, 90, standIns.size() + 1));
            }
            return standIns.get(index - 1);
        }

        private int fire() {
            return battle.fireTriggers(TriggerEvent.ALLY_ATTACK, owner, enemy, 1, 0);
        }

        /** A generator that always answers the same value, so a roll becomes an assertion. */
        private static Random fixed(double value) {
            return new Random() {
                @Override
                public double nextDouble() {
                    return value;
                }
            };
        }
    }

    /** A {@code RESIST_DEBUFF} for the control class. */
    private static EffectSpec resist(double percent) {
        return resistEffect("control", percent, 1, null);
    }

    private static EffectSpec resist(double percent, int turns) {
        return resistEffect("control", percent, turns, null);
    }

    private static EffectSpec resistPermanent(double percent) {
        return resistEffect("control", percent, null, true);
    }

    /**
     * A {@code RESIST_DEBUFF} effect.
     *
     * @param kind      {@code "control"} / {@code "dot"}, or {@code null} to leave the field out
     * @param percent   the resistance fraction
     * @param turns     the duration, or {@code null} when {@code permanent} is used
     * @param permanent {@code true} for 「整场战斗」, or {@code null}
     */
    private static EffectSpec resistEffect(String kind, double percent, Integer turns, Boolean permanent) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "RESIST_DEBUFF");
        TriggerSpecs.set(effect, "kind", kind);
        TriggerSpecs.set(effect, "percent", percent);
        TriggerSpecs.set(effect, "turns", turns);
        TriggerSpecs.set(effect, "permanent", permanent);
        // Defaulted to the event's subject: 「抵抗…」 protects the unit the rule is about, and self (the engine's
        // default when no target is stated) is invisible in a one-unit fixture.
        TriggerSpecs.set(effect, "target", "target");
        return effect;
    }

    /** Compiles one effect into a table, which is where its validation runs. */
    private static TriggerTable tableOf(EffectSpec effect) {
        return new TriggerTable(OWNER, List.of(TriggerSpecs.rule("ALLY_ATTACK", null, effect)));
    }
}
