package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.buff.AbstractBuff;
import com.laosun.aluminium.models.buff.BuffManager;
import com.laosun.aluminium.models.buff.ControlBuff;
import com.laosun.aluminium.models.buff.DotBuff;
import com.laosun.aluminium.models.buff.ShieldBuff;
import com.laosun.aluminium.models.buff.StateBuff;
import com.laosun.aluminium.models.buff.StatModifierBuff;
import com.laosun.aluminium.models.buff.TauntBuff;
import com.laosun.aluminium.models.buff.VulnerabilityBuff;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * {@code EXTEND_BUFF}: 「…的持续时间增加 1 回合」.
 *
 * <p><b>Why the vocabulary needed it.</b> Ten of the 97 documents lengthen a buff that is <b>already up</b> rather
 * than creating a new one — 三月七's 加护 (「战技提供的护盾持续时间增加1回合」), 布洛妮娅's 星魂 6, 桑博's 风化, 姬子's
 * 灼烧, 白露's 生息, 藿藿's 禳命, 加拉赫's 酩酊 … Before this op the only way to write one was to <b>fold the +1 into the
 * ability it lengthens</b>, which erases the trace's own line from the data and makes the base ability state a
 * duration that is not its own.
 *
 * <p><b>The two filters, and why neither is optional.</b> Every sentence identifies the buff by its <b>origin</b>
 * (「战技提供的」) and then by <b>what it is</b>: a state's name (灼烧 / 生息 / 冻结 / 护盾), or — when the text names an
 * effect rather than a state (「伤害提高效果」) — the <b>attribute</b> the modifier sits on. "Everything I have on that
 * unit" is deliberately not a spelling, and {@link #itDoesNotLengthenTheOwnersOtherBuffs} is the case that shows why:
 * 布洛妮娅's DEFENCE trace buff from {@code BATTLE_START} can still be ticking when she casts her Skill.
 */
public class ExtendBuffTest {
    private static final double EPS = 1e-6;

    private static final int OWNER = 1002;
    private static final int ALLY = 1202;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** The 加护 shape: the same rule applies the shield and then lengthens it. */
    @Test
    public void itLengthensTheBuffTheSameRuleJustApplied() {
        Fixture f = new Fixture(
                TriggerSpecs.shield(500, 2, "target"),
                TriggerSpecs.extendBuff(BuffManager.SHIELD_STATE, null, 1));

        f.fire();

        Assertions.assertEquals(3, f.buff(ShieldBuff.class).duration(),
                "2 turns from the shield + 1 from the trace -- and the two numbers stay in two rules");
    }

    /** A state's name is the filter for a named state and for a DOT (whose name comes from its element). */
    @Test
    public void aStateNameNamesTheBuff() {
        Fixture state = new Fixture(
                TriggerSpecs.applyBuff("协奏", 1, null),
                TriggerSpecs.extendBuff("协奏", null, 2));
        state.fire();
        Assertions.assertEquals(3, state.buff(StateBuff.class).duration(), "1 + 2");

        Fixture dot = new Fixture(
                TriggerSpecs.dot("Fire", 500.0, null, null, 2, null),
                TriggerSpecs.extendBuff("灼烧", null, 1));
        dot.fire();
        Assertions.assertEquals(3, dot.buff(DotBuff.class).duration(),
                "the DOT's name is its element's (Fire → 灼烧), translated in one place");
    }

    /** When the sentence names an <i>effect</i> rather than a state, the attribute a modifier sits on names it. */
    @Test
    public void anAttributeNamesAModifier() {
        Fixture f = new Fixture(
                TriggerSpecs.modifyAttr("ATTACK", 0.2, 1, null, null, null, "target"),
                TriggerSpecs.extendBuff(null, "ATTACK", 1));

        f.fire();

        Assertions.assertEquals(2, f.buff(StatModifierBuff.class).duration(), "1 + 1");
    }

    /** ⚠ Only the owner's own buffs: 「战技提供的」/「天赋使敌方目标陷入的」 are all about who applied it. */
    @Test
    public void itDoesNotLengthenAnotherUnitsBuff() {
        Fixture f = new Fixture(TriggerSpecs.extendBuff(BuffManager.SHIELD_STATE, null, 1));
        Character someoneElse = CharacterFactory.create(ALLY, LEVEL);
        f.target.getBuffManager().addBuff(new ShieldBuff(someoneElse, 500, 2));

        f.fire();

        Assertions.assertEquals(2, f.buff(ShieldBuff.class).duration(),
                "a shield somebody else applied is not 「战技提供的护盾」");
    }

    /**
     * And only the buff the rule <b>names</b> — the case that ruled out a "everything of mine" spelling.
     *
     * <p>Both buffs below belong to the owner. A filter that lengthened every one of them would look right in every
     * single-buff test and silently add a turn to the other one in a real fight.
     */
    @Test
    public void itDoesNotLengthenTheOwnersOtherBuffs() {
        Fixture f = new Fixture(
                TriggerSpecs.dot("Fire", 500.0, null, null, 2, null),
                TriggerSpecs.shield(500, 2, "target"),
                TriggerSpecs.extendBuff(BuffManager.SHIELD_STATE, null, 1));

        f.fire();

        Assertions.assertEquals(3, f.buff(ShieldBuff.class).duration(), "the named buff got its turn");
        Assertions.assertEquals(2, f.buff(DotBuff.class).duration(), "and the owner's burn did not");
    }

    /**
     * A permanent buff has no countdown, so there is nothing to lengthen — and that is not an error.
     *
     * <p>⚠ The two effects sit on <b>two events</b> on purpose: with both in one rule the second firing would
     * re-apply the state (a same-kind buff replaces), so the duration would look unchanged whether or not the
     * {@code permanent} guard were there — a test that passes for the wrong reason.
     */
    @Test
    public void aPermanentBuffHasNoCountdownToLengthen() {
        Fixture f = new Fixture(
                TriggerSpecs.rule("ALLY_ATTACK", null, TriggerSpecs.applyBuff("协奏", null, true)),
                TriggerSpecs.rule("BASIC_ATTACK", null, TriggerSpecs.extendBuff("协奏", null, 5)));
        f.fire();
        StateBuff state = f.buff(StateBuff.class);
        Assertions.assertTrue(state.isPermanent(), "precondition: the state is 「整场战斗」");
        int duration = state.duration();

        Assertions.assertDoesNotThrow(() -> f.fire(TriggerEvent.BASIC_ATTACK));

        Assertions.assertEquals(duration, state.duration(),
                "permanent means \"never ticked\": the +5 is a number nobody would ever read");
    }

    /**
     * ⚠ Every buff a rule creates records <b>who applied it</b>.
     *
     * <p>This is what the origin filter rests on, and it used to be true only where a constructor demanded it
     * ({@code DotBuff} needs it for kill credit) — a {@code StateBuff} or a stat modifier could be anonymous, and an
     * anonymous buff would make 「战技提供的护盾」 silently extend nothing at all. The audit is pinned here so that a
     * new buff-creating op has to keep its half of the deal.
     */
    @Test
    public void everyBuffARuleCreatesRecordsItsSource() {
        record Case(EffectSpec effect, Class<? extends AbstractBuff> kind) {
        }
        List<Case> cases = List.of(
                new Case(TriggerSpecs.shield(500, 2, "target"), ShieldBuff.class),
                new Case(TriggerSpecs.applyBuff("协奏", 2, null), StateBuff.class),
                new Case(TriggerSpecs.modifyAttr("ATTACK", 0.2, 2, null, null, null, "target"), StatModifierBuff.class),
                new Case(TriggerSpecs.modifyDamageTaken(0.2, 2), VulnerabilityBuff.class),
                new Case(TriggerSpecs.taunt(1), TauntBuff.class),
                new Case(TriggerSpecs.applyControl("冻结", 1, null, "target"), ControlBuff.class),
                new Case(TriggerSpecs.dot("Ice", 500.0, null, null, 2, null), DotBuff.class));

        for (Case one : cases) {
            Fixture f = new Fixture(one.effect());
            f.fire();
            Assertions.assertSame(f.owner, f.buff(one.kind()).getSource(),
                    one.kind().getSimpleName() + " must remember who applied it -- EXTEND_BUFF filters by origin");
        }
    }

    // ==================================================================
    // Fail fast: what an EXTEND_BUFF rule may not say
    // ==================================================================

    @Test
    public void theFilterIsRequiredAndUnique() {
        IllegalArgumentException missing = Assertions.assertThrows(IllegalArgumentException.class,
                () -> tableOf(TriggerSpecs.extendBuff(null, null, 1)));
        Assertions.assertTrue(missing.getMessage().contains("buff"), missing.getMessage());

        IllegalArgumentException both = Assertions.assertThrows(IllegalArgumentException.class,
                () -> tableOf(TriggerSpecs.extendBuff("灼烧", "ATTACK", 1)));
        Assertions.assertTrue(both.getMessage().contains("both"), both.getMessage());

        Assertions.assertThrows(IllegalArgumentException.class,
                () -> tableOf(TriggerSpecs.extendBuff(null, "NOT_AN_ATTRIBUTE", 1)), "unknown attribute");
    }

    @Test
    public void turnsAreRequiredAndNothingElseIsAccepted() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> tableOf(TriggerSpecs.extendBuff("灼烧", null, null)), "no turns");
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> tableOf(TriggerSpecs.extendBuff("灼烧", null, 0)), "0 turns");

        EffectSpec withAmount = TriggerSpecs.extendBuff("灼烧", null, 1);
        TriggerSpecs.set(withAmount, "amount", 5.0);
        IllegalArgumentException rejected = Assertions.assertThrows(IllegalArgumentException.class,
                () -> tableOf(withAmount));
        Assertions.assertTrue(rejected.getMessage().contains("amount"), rejected.getMessage());
    }

    // ==================================================================
    // helpers
    // ==================================================================

    /** One owner (with the rule under test), one ally to hold the buff, one enemy. */
    private static final class Fixture {
        private final Character owner;
        private final Character target;
        private final Battle battle;

        private Fixture(EffectSpec... effects) {
            this(TriggerSpecs.rule("ALLY_ATTACK", null, effects));
        }

        private Fixture(TriggerSpec... rules) {
            this.owner = CharacterFactory.create(OWNER, LEVEL);
            this.target = CharacterFactory.create(ALLY, LEVEL);
            this.owner.setTriggerTable(new TriggerTable(OWNER, List.of(rules)));
            this.target.setTriggerTable(new TriggerTable(ALLY, List.of()));
            this.battle = new Battle(List.of(owner, target), List.of(dummy()), new Random(0));
            battle.startBattle();
        }

        /** Fires the rule with the ally as the event's subject (which is what {@code target} resolves to). */
        private int fire() {
            return fire(TriggerEvent.ALLY_ATTACK);
        }

        private int fire(TriggerEvent event) {
            return battle.fireTriggers(event, owner, target, 1, 0);
        }

        /** The buff the rule was expected to touch, failing loudly when it is not there. */
        private <T extends AbstractBuff> T buff(Class<T> kind) {
            T found = target.getBuffManager().findBuff(kind);
            Assertions.assertNotNull(found, kind.getSimpleName() + " was expected on the ally");
            return found;
        }
    }

    /** Compiles one effect into a table, which is where its validation runs. */
    private static TriggerTable tableOf(EffectSpec effect) {
        return new TriggerTable(OWNER, List.of(TriggerSpecs.rule("ALLY_ATTACK", null, effect)));
    }

    private static Enemy dummy() {
        return EnemyFactory.create(MONSTER, 90, 1);
    }
}
