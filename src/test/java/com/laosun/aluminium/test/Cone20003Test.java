package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Light cone 20003: 「防御力提高#1%。当装备者当前生命值百分比小于#2%时，其防御力额外提高#3%」 at rank 5.
 *
 * <p>DEFENCE is flat (each share scales the post-start base). The two clauses are mutually exclusive branches, so exactly one modifier exists at any time.
 */
public class Cone20003Test {
    private static final int WEARER = 1001;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    @Test
    public void theBaseBranchAppliesAndTheThresholdBranchStatesTheSum() {
        double[] healthy = defence(0.0);
        double[] middle = defence(0.7);
        double[] hurt = defence(0.5);
        System.out.println("[20003] healthyGain=" + healthy[0] + " base=" + healthy[1]
                + " middleGain=" + middle[0] + " middleHp=" + middle[2]
                + " hurtGain=" + hurt[0] + " hp=" + hurt[2]);
        // ⭐ The band between the two thresholds: above #2, so ONLY the base branch may apply. Without this reading, a file whose
        // branches stop being exhaustive (e.g. base gated on hp >= 90%) would still pass.
        Assertions.assertTrue(middle[2] >= 0.5 && middle[2] < 0.9, "precondition: inside the untested band");
        Assertions.assertEquals(0.32 * middle[1], middle[0], 1e-6, "above the threshold only #1 applies");
        Assertions.assertEquals(0.32 * healthy[1], healthy[0], 1e-6, "the base branch is unconditional");
        Assertions.assertTrue(hurt[2] < 0.5, "precondition: below the threshold");
        Assertions.assertEquals(0.64 * hurt[1], hurt[0], 1e-6,
                "below 50% the exclusive branch states #1 + #3");
    }

    /** gain, base value, and the final HP fraction. */
    private static double[] defence(double fraction) {
        Character unit = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(20003, LEVEL, false, 5));
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(unit), List.of(enemy), new Random(0));
        battle.startBattle();
        if (fraction > 0) {
            int guard = 0;
            while (unit.getCurrentHp() / unit.getMaxHp() >= fraction && guard++ < 80) {
                battle.applyDamage(unit, new Damage(enemy, unit, DamageElement.PHYSICAL, DamageType.NORMAL,
                        unit.getMaxHp() * 0.15));
            }
        }
        double before = unit.getAttribute(AttributeType.DEFENCE).get();
        battle.currentMove = new Signal(unit);
        battle.beforeMove();
        return new double[] {unit.getAttribute(AttributeType.DEFENCE).get() - before,
                unit.getAttribute(AttributeType.DEFENCE).baseValue(), unit.getCurrentHp() / unit.getMaxHp()};
    }
}
