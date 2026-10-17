package com.laosun.aluminium.test.trigger;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.buff.StatModifierBuff;
import com.laosun.aluminium.models.trigger.TriggerTable;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code target is_slowed} - "the enemy is under a slow".
 *
 * <p><b>Why the DSL needed it.</b> 「处于减速状态」 is not a named state in this engine: a slow IS a {@code SPEED}
 * modifier, written by a Skill's {@code MODIFY_ATTR} debuff or by {@code ControlBuff}'s own slow, so {@code has_state}
 * cannot answer it. The sentence that needs it is 1002 Dan Heng's ultimate ("当受到攻击的敌方目标处于减速状态时，终结技造成
 * 的伤害倍率提高 120%"); his trace 罡风 asks the same of a basic attack, and light cone 20011 asks it of every hit.
 *
 * <p><b>What this file pins.</b> That the question is answered off the <b>attribute's modifiers</b> (so a slow from
 * anywhere counts), that a bigger positive speed buff does <b>not</b> hide the slow (the net comparison would),
 * that {@code !} inverts it, and that a trailing argument is refused at load.
 */
public class SlowedConditionTest {
    private static final int OWNER = 1003;
    private static final int LEVEL = 80;
    private static final double BASE = 1000;
    private static final double EPS = 1e-9;

    /**
     * With the slow applied, the boost lands; without it, the same hit is unchanged.
     */
    @Test
    public void aSlowedTargetTakesTheBoost() {
        double plain = damage(null, false);
        double notSlowed = damage("target is_slowed", false);
        double slowed = damage("target is_slowed", true);

        Assertions.assertEquals(plain, notSlowed, EPS,
                "the condition is false for an unslowed target, so the boost must not be there");
        Assertions.assertEquals(1.4, slowed / plain, 1e-6,
                "a +40% instance boost applied because the target is slowed: " + plain + " -> " + slowed);
    }

    /**
     * A positive speed buff next to the slow does not turn the condition off.
     *
     * <p>This is the difference between "the unit is under a slow" and "the unit's net speed is below its base":
     * with {@code +20%} speed and a {@code -12%} slow the net is above the base, and the game still calls that
     * unit slowed. A net comparison would silently clear the state.
     */
    @Test
    public void aSpeedBuffDoesNotHideTheSlow() {
        double slowedAlone = damage("target is_slowed", true);
        double buffedAndSlowed = buffedDamage();

        Assertions.assertEquals(slowedAlone, buffedAndSlowed, 1e-6,
                "「+20% speed」 is a buff, not a cure: the slow is still on the unit, so the boost is still paid");
    }

    /**
     * The same condition, negated: the hit that pays is the one against a target that is NOT slowed.
     */
    @Test
    public void negationInvertsIt() {
        double plain = damage(null, false);
        double againstASlowedTarget = damage("!target is_slowed", true);
        double againstAnUnslowedOne = damage("!target is_slowed", false);

        Assertions.assertEquals(plain, againstASlowedTarget, EPS,
                "「!target is_slowed」 must be false on a slowed target");
        Assertions.assertEquals(1.4, againstAnUnslowedOne / plain, 1e-6);
    }

    /**
     * The keyword takes no argument; a trailing word would otherwise be dropped silently and the condition would
     * answer the wrong thing (or nothing at all).
     */
    @Test
    public void anArgumentAfterTheKeywordIsRefusedAtLoad() {
        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> tableOf("target is_slowed now"));

        Assertions.assertTrue(refused.getMessage().contains("is_slowed"), refused.getMessage());
    }

    /**
     * Only the parties the DSL knows may be asked - the same rule as every other party predicate.
     */
    @Test
    public void anUnknownPartyIsRefusedAtLoad() {
        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> tableOf("everyone is_slowed"));

        Assertions.assertTrue(refused.getMessage().contains("everyone"), refused.getMessage());
    }

    /**
     * The shipped file: Dan Heng's two damage clauses both ask this question, gated on the slot the sentence names.
     */
    @Test
    public void theShippedFileAsksTheQuestionOnTheTwoSlotsItsTextNames() {
        List<String> asked = new ArrayList<>();
        for (TriggerTable.CompiledRule rule : TriggerTables.of(1002).rulesFor(TriggerEvent.DEALING_DAMAGE)) {
            for (TriggerTable.Condition condition : rule.conditions()) {
                if (condition.source().contains("is_slowed")) {
                    asked.add(rule.conditions().stream().map(TriggerTable.Condition::source).toList().toString());
                }
            }
        }
        Assertions.assertEquals(2, asked.size(), "his ultimate and trace 罡风: " + asked);
        Assertions.assertTrue(asked.stream().anyMatch(c -> c.contains("from_skill ULTRA")), asked.toString());
        Assertions.assertTrue(asked.stream().anyMatch(c -> c.contains("from_skill COMMON")), asked.toString());
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /**
     * Settles one hit against a target that is (or is not) slowed, with the boost gated on {@code condition}.
     */
    private static double damage(String condition, boolean slowed) {
        Fixture f = new Fixture(condition);
        if (slowed) {
            f.slow();
        }
        return f.hit();
    }

    /**
     * The same, with a bigger speed buff than the slow.
     */
    private static double buffedDamage() {
        Fixture f = new Fixture("target is_slowed");
        f.speedUp();
        f.slow();
        return f.hit();
    }

    /**
     * Compiles one condition into a table, which is where the load-time checks run.
     */
    private static TriggerTable tableOf(String condition) {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        owner.setTriggerTable(new TriggerTable(OWNER,
                List.of(TriggerSpecs.rule("DEALING_DAMAGE", List.of(condition), boost(0.4)))));
        return owner.getTriggerTable();
    }

    private static final class Fixture {
        private final Character owner = CharacterFactory.create(OWNER, LEVEL);
        private final Enemy enemy = BattleFixture.monster();
        private final Battle battle;
        private double lastSettled;

        private Fixture(String condition) {
            // The baseline is "no rule at all": a rule with no conditions would fire the boost and become its own
            // control (measured: the "plain" run came out 1.4x the "not slowed" one).
            owner.setTriggerTable(condition == null
                    ? new TriggerTable(OWNER, List.of())
                    : new TriggerTable(OWNER, List.of(TriggerSpecs.rule("DEALING_DAMAGE", List.of(condition), boost(0.4)))));
            battle = new Battle(List.of(owner), List.of(enemy), BattleFixture.alwaysRoll(0.99));
            battle.addDamageObserver(new Battle.DamageObserver() {
                @Override
                public void onDamageSettled(Damage damage, double applied, double hpLoss, double shieldAbsorbed,
                                            boolean died) {
                    lastSettled = applied;
                }
            });
            battle.startBattle();
        }

        /** 「减速状态」: the engine's only representation of it - a negative SPEED modifier. */
        private void slow() {
            enemy.getBuffManager().addBuff(StatModifierBuff.percentDebuff(AttributeType.SPEED, -0.12, 2));
        }

        /** A bigger buff than the slow, so a net comparison would read "not slowed". */
        private void speedUp() {
            enemy.getBuffManager().addBuff(StatModifierBuff.percentBuff(AttributeType.SPEED, 0.2, 2));
        }

        private double hit() {
            enemy.heal(enemy.getMaxHp());
            battle.applyDamage(enemy, new Damage(owner, enemy, DamageElement.WIND, DamageType.NORMAL, BASE,
                    SkillCategory.NORMAL));
            return lastSettled;
        }
    }

    private static EffectSpec boost(double percent) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "BOOST_DAMAGE");
        TriggerSpecs.set(effect, "percent", percent);
        return effect;
    }
}
