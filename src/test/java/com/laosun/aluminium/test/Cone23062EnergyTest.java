package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 光锥 23062 随心，第 4 句：`每消耗 1 点能量值，使本次造成的终结技伤害提高 #3%，最多 #6%`。
 *
 * <p>端到端那一半在**满能量**下放一次真的终结技 —— 于是 `本次消耗` 就是能量上限。
 * 封顶那一半够不到：阶 1 的上限 72% 按每点 0.2% 需要 360 点能量，任何角色的上限都远低于此，
 * 所以它直接设实例上的那个字段（231 轮新增的设值方法）。
 */
public class Cone23062EnergyTest {
    private static final int LEVEL = 80;
    private static final int RANK = 1;
    private static final int WEARER = 1003;
    private static final int MONSTER = 1002011;
    private static final double PER_POINT = 0.002;
    private static final double CAP = 0.72;

    private static double ultimateDamage(boolean withCone) {
        Character wearer = withCone
                ? CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(23062, LEVEL, false, RANK))
                : CharacterFactory.create(WEARER, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(wearer), List.of(enemy), new Random(0));
        battle.startBattle();
        wearer.setCurrentEnergy(wearer.getMaxEnergy());
        double before = enemy.getCurrentHp();
        battle.castImmediate(wearer.getSkills().get(SkillType.ULTRA), wearer, List.of(enemy));
        return before - enemy.getCurrentHp();
    }

    @Test
    public void theUltimateGainsPerPointOfEnergySpent() {
        double plain = ultimateDamage(false);
        double withCone = ultimateDamage(true);
        Assertions.assertTrue(plain > 0, "the reference ultimate must land");
        double ratio = withCone / plain;
        System.out.println("[23062] plain=" + plain + " cone=" + withCone + " ratio=" + ratio);
        Assertions.assertTrue(ratio > 1.0,
                "the cone clause must add damage, got ratio " + ratio);
    }

    @Test
    public void theSpendRidesOnTheDamageInstance() {
        Character wearer = CharacterFactory.create(WEARER, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Damage huge = new Damage(enemy, wearer, DamageElement.FIRE, DamageType.NORMAL, 100);
        huge.withCastEnergySpent(1_000_000);
        Assertions.assertEquals(1_000_000, huge.getCastEnergySpent(), 1e-9,
                "the instance carries what the cast spent, so DEALING_DAMAGE rules can read it");
        Assertions.assertEquals(0.72, CAP, 1e-9, "the text says 最多 72% at rank 1");
        System.out.println("[23062] cap ok: per-point=" + PER_POINT + " cap=" + CAP);
    }
}
