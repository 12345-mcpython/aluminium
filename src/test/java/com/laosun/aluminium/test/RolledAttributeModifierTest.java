package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 「有100%的<b>基础概率</b>额外使该目标的全属性抗性降低10.00%」 -- a ROLLED attribute modifier.
 *
 * <p>MODIFY_ATTR used to attach directly, so a base chance had no effect on it; it now goes through the same
 * {@code attachRolled} pipeline as APPLY_DOT / APPLY_BUFF / MODIFY_DAMAGE_TAKEN. Seventeen corpus documents roll a base
 * chance before an attribute change, and 1006's Skill is the first one shipped.
 */
public class RolledAttributeModifierTest {
    private static final int SILVER_WOLF = 1006;
    private static final int ALLY = 1210;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final double EPS = 1e-9;

    /** The shipped clause states 0.1, through the roll, on the skill's target. */
    @Test
    public void herSkillStatesTenPercentOnItsTarget() {
        Character wolf = CharacterFactory.create(SILVER_WOLF, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(wolf, CharacterFactory.create(ALLY, LEVEL)), List.of(enemy), new Random(0));
        battle.startBattle();

        battle.castImmediate(wolf.getSkills().get(SkillType.SKILL), wolf, List.of(enemy));

        Assertions.assertEquals(0.1, enemy.getAttribute(AttributeType.RESISTANCE_REDUCTION).get(), EPS,
                "\u300c\u6709100%\u7684\u57fa\u7840\u6982\u7387\u4f7f\u8be5\u76ee\u6807\u7684\u5168\u5c5e\u6027\u6297\u6027\u964d\u4f4e10.00%\u300d");
    }

    /**
     * The roll is what decides: with a generator that returns 0.0 the modifier lands, and with one that returns 1.0 it
     * does not (a roll of 1.0 is not below any chance at most 1). The shipped clause cannot prove this, because at
     * base_chance 1.0 it lands whether or not the roll is consulted.
     */
    @Test
    public void theBaseChanceIsWhatDecides() {
        Assertions.assertEquals(0.25, landed(0.0), EPS, "a roll below the chance lands");
        Assertions.assertEquals(0.0, landed(1.0), EPS, "a roll at 1.0 never lands, whatever the chance is");
    }

    /** Applies a rolled 25% resistance reduction with a generator fixed at the given value. */
    private static double landed(double roll) {
        Character applier = Character.fromAttributes("applier", 10_000, 100, 100, 100);
        applier.setTriggerTable(new TriggerTable(9995, List.of(TriggerSpecs.rule(
                TriggerEvent.TURN_START.name(), List.of(), reduction()))));
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(applier), List.of(enemy), new Random() {
            @Override
            public double nextDouble() {
                return roll;
            }
        });
        battle.startBattle();

        battle.fireTriggers(TriggerEvent.TURN_START, applier, enemy, 0, 0);

        return enemy.getAttribute(AttributeType.RESISTANCE_REDUCTION).get();
    }

    private static EffectSpec reduction() {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "MODIFY_ATTR");
        TriggerSpecs.set(effect, "attribute", AttributeType.RESISTANCE_REDUCTION.attributeString);
        TriggerSpecs.set(effect, "percent", 0.25);
        TriggerSpecs.set(effect, "baseChance", 1.0);
        TriggerSpecs.set(effect, "turns", 2);
        TriggerSpecs.set(effect, "target", "target");
        return effect;
    }
    /** Two technique clauses that roll a base chance before lowering a FLAT attribute. */
    @Test
    public void twoTechniquesLowerAnEnemyAttributeThroughTheRoll() {
        Assertions.assertEquals(0.8, appliedRatio(1106, AttributeType.DEFENCE, false), 1e-6,
                "\u300c\u6709100%\u7684\u57fa\u7840\u6982\u7387\u4f7f\u654c\u65b9\u6bcf\u4e2a\u5355\u4f53\u76ee\u6807\u9632\u5fa1\u529b\u964d\u4f4e20%\u300d");
        Assertions.assertEquals(0.75, appliedRatio(1217, AttributeType.ATTACK, false), 1e-6,
                "\u300c\u6709100%\u7684\u57fa\u7840\u6982\u7387\u4f7f\u654c\u65b9\u6bcf\u4e2a\u5355\u4f53\u76ee\u6807\u653b\u51fb\u529b\u964d\u4f4e25%\u300d");
    }

    /**
     * The victim's attribute after the technique, over that same victim's base value.
     *
     * <p>⚠ The fixture checks its own premise: a base chance is rolled through the resistance pipeline, which includes the
     * victim's SPECIFIC resistance (a {@code STAT_*} key in the monster data), and the first monster in the probe range
     * resists attack-down -- so 1217's clause legitimately does nothing to it. The loop therefore walks the monster range
     * and uses the first one the clause reaches, and fails loudly if there is none (a fixture that silently finds no
     * victim would make the assertion below meaningless).
     */
    private static double appliedRatio(int cid, AttributeType attribute, boolean fireSkillCast) {
        Character owner = CharacterFactory.create(cid, LEVEL);
        Character partner = CharacterFactory.create(ALLY, LEVEL);
        for (int id = 1002010; id < 1002100; id++) {
            Enemy candidate;
            try {
                candidate = EnemyFactory.create(id, 90, 1);
            } catch (RuntimeException ignored) {
                continue;                                  // not in the data
            }
            // ⚠ A generator fixed at 0.0, not `new Random(0)`: its first draw is ~0.7244, so a 75% base chance passes
            // only barely and any victim-side resistance (specific or effect) turns the roll into a failure.
            Battle battle = new Battle(List.of(owner, partner), List.of(candidate), new Random() {
                @Override
                public double nextDouble() {
                    return 0.0;
                }
            });
            battle.markTechniqueUsed(owner);
            battle.startBattle();
            // ⚠ The cast event is opt-in: the techniques hang on BATTLE_START while 1004's slow hangs on SKILL_CAST, and
            // firing SKILL_CAST for everyone broke 1217 -- her own Skill rule dispels a negative effect from its target
            // and removed the reduction the technique had just applied. It is fired directly rather than casting the
            // skill, because a bounce cast would deal damage and could kill the monster under inspection.
            if (fireSkillCast) {
                battle.fireTriggers(TriggerEvent.SKILL_CAST, owner, candidate, 0, 0);
            }
            double ratio = candidate.getAttribute(attribute).get()
                    / candidate.getAttribute(attribute).baseValue();
            if (ratio < 1.0) {
                return ratio;
            }
        }
        throw new AssertionError("no monster in the probed range takes the " + attribute + " reduction");
    }
    /** 1004's Skill: 75% base chance, 10% slow, two turns -- the sixth stale registration refuted. */
    @Test
    public void weltsSkillSlowsTheTargetThroughItsBaseChance() {
        Assertions.assertEquals(0.9, appliedRatio(1004, AttributeType.SPEED, true), 1e-6,
                "\u300c\u653b\u51fb\u547d\u4e2d\u65f6\u670975%\u7684\u57fa\u7840\u6982\u7387\u4f7f\u53d7\u5230\u653b\u51fb\u7684\u654c\u65b9\u76ee\u6807\u901f\u5ea6\u964d\u4f4e10%\u300d");
    }
}
