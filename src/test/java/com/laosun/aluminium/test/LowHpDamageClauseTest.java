package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1003's clause: "对当前生命值百分比小于等于50%的敌方目标造成的伤害提高15%".
 *
 * <p>Note: The attack is her BASIC ATTACK on purpose: the probe in this round showed one Skill cast settles as TWO damage instances, and an
 * instance-scoped boost gets diluted when both are summed (which is why round 106 measured 1.005and withdrew the clause). The fixture is two victims
 * of the same monster -- one wounded to ~40%, one whole -- so the ratio isolates `target_hp_percent`, which is a FRACTION.
 */
public class LowHpDamageClauseTest {
    private static final int WEARER = 1003;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    @Test
    public void theClauseOnlyFiresAgainstAWoundedTarget() {
        double wounded = damageAgainst(true);
        double healthy = damageAgainst(false);
        Assertions.assertTrue(healthy > 0, "precondition: the hit landed");
        double ratio = wounded / healthy;
        Assertions.assertTrue(ratio > 1.05 && ratio <= 1.20,
                "the +15% applies only at or below half health: " + healthy + " vs " + wounded
                        + " (ratio " + ratio + ")");
    }

    private static double damageAgainst(boolean wound) {
        Character wearer = CharacterFactory.create(WEARER, LEVEL);
        Enemy victim = EnemyFactory.create(MONSTER, 90, 1);
        victim.setAttribute(AttributeType.HEALTH, new DoubleValue(900000));
        victim.heal(900000);
        if (wound) {
            victim.takeDamage(victim.getCurrentHp() * 0.6);
        }
        Random noCrit = new Random(0) {
            @Override
            public double nextDouble() {
                return 0.99;
            }
        };
        Battle battle = new Battle(List.of(wearer), List.of(victim), noCrit);
        battle.startBattle();
        double before = victim.getCurrentHp();
        battle.castImmediate(wearer.getSkills().get(SkillType.COMMON), wearer, List.of(victim));
        double dealt = before - victim.getCurrentHp();
        Assertions.assertFalse(victim.isDeath(), "the judged hit must not kill the victim");
        Assertions.assertTrue(dealt > 0, "the hit landed");
        return dealt;
    }
}
