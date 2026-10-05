package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Light cone 21039: "效果抵抗提高#1%。装备者每有#2 点防御力，使造成的伤害提高#3%，最多使造成的伤害提高#4%".
 *
 * <p>Two regimes, both measured: the product (DEFENCE x share) and the CONSTANT ceiling (`cap_amount`). The ceiling is reached by raising DEFENCE with the call shape
 * `BoostDamageBuff` uses.
 */
public class DestinySThreadsForewovenTest {
    private static final int WEARER = 1001;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final double SHARE_PER_DEFENCE = 0.00012;
    private static final double CAP = 0.48;

    @Test
    public void theDamageClauseFollowsTheProductAndTheCeilingBinds() {
        double[] natural = boost(0);
        double[] clamped = boost(4000);
        System.out.println("[21039] defence=" + natural[1] + " boost=" + natural[0]
                + " | boostedDefence=" + clamped[1] + " clampedBoost=" + clamped[0]);
        Assertions.assertTrue(natural[1] * SHARE_PER_DEFENCE < CAP, "precondition: the product is below the cap");
        Assertions.assertEquals(natural[1] * SHARE_PER_DEFENCE, natural[0], 1e-9, "below the cap, the product wins");
        Assertions.assertTrue(clamped[1] * SHARE_PER_DEFENCE > CAP, "precondition: the product exceeds the cap");
        Assertions.assertEquals(CAP, clamped[0], 1e-9, "cap_amount must clamp the derived magnitude");
    }

    @Test
    public void theEffectResistanceClauseApplies() {
        Character unit = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(21039, LEVEL, false, 5));
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(unit), List.of(enemy), new Random(0));
        double before = unit.getAttribute(AttributeType.EFFECT_RESISTANCE).get();
        battle.startBattle();
        double gain = unit.getAttribute(AttributeType.EFFECT_RESISTANCE).get() - before;
        System.out.println("[21039] effectResistance=" + gain);
        Assertions.assertEquals(0.2, gain, 1e-6, "rank 5 states 20%");
    }

    private static double[] boost(double extraDefence) {
        Character unit = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(21039, LEVEL, false, 5));
        if (extraDefence > 0) {
            unit.getAttribute(AttributeType.DEFENCE).addModifier(
                    DoubleValue.Modifier.pure(extraDefence, DoubleValue.Modifier.ModifierSource.BUFF, 1));
        }
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(unit), List.of(enemy), new Random(0));
        double before = unit.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        battle.startBattle();
        return new double[] {unit.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get() - before,
                unit.getAttribute(AttributeType.DEFENCE).get()};
    }
}
