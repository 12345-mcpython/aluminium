package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.Enemy;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * P1-5 acceptance: {@code Battle.applyDamage} assembles the zones onto a {@link Damage},
 * settles it, applies it once, and is the only way in.
 *
 * <p>Attackers are plain {@code Character.fromAttributes} combatants (Lv80, no light cone
 * or relic), so every boost / crit attribute starts at 0 and the defender's DEFENCE
 * isolates the zone under test.
 */
public class DamagePipelineTest {
    private static final double EPS = 1e-6;
    private static final double BASE = 1000.0;
    /** 攻击者 80 级时防御区的等级项：200 + 10 × 80。 */
    private static final double LEVEL_TERM = 1000.0;

    private static Character attacker() {
        return Character.fromAttributes("attacker", 1000, 100, 100, 100);
    }

    private static Enemy defender(double defence) {
        return Enemy.fromAttributes("defender", 100000, defence, 100, 100);
    }

    private static Battle battle(Character attacker, Enemy defender, long seed) {
        return new Battle(List.of(attacker), List.of(defender), new Random(seed));
    }

    private static double settle(Character attacker, Enemy defender, DamageType type, double base) {
        Damage damage = new Damage(attacker, defender, DamageElement.FIRE, type, base);
        return battle(attacker, defender, 0).applyDamage(defender, damage);
    }

    private static double settle(Character attacker, Enemy defender) {
        return settle(attacker, defender, DamageType.NORMAL, BASE);
    }

    private static double critSettled(double critRate, double critDamage) {
        Character attacker = attacker();
        attacker.setAttribute(AttributeType.CRIT_CHANCE, new DoubleValue(critRate));
        attacker.setAttribute(AttributeType.CRIT_ATTACK, new DoubleValue(critDamage));
        return settle(attacker, defender(0));
    }

    @Test
    public void boostZoneWiresElementAndGlobalBoost() {
        Character attacker = attacker();
        attacker.setAttribute(AttributeType.FIRE_DAMAGE_BOOST, new DoubleValue(0.3));

        Assertions.assertEquals(1300, settle(attacker, defender(0)), EPS);

        // 攻击类型增伤与元素增伤加算进同一个区：1 + 0.3 + 0.2 = 1.5
        attacker.setAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST, new DoubleValue(0.2));

        Assertions.assertEquals(1500, settle(attacker, defender(0)), EPS);
    }

    @Test
    public void critFollowsTheInjectedRandom() {
        // Random(0) 的首值 ≈ 0.7310 —— 用它把"暴/不暴"两个分支都钉死
        double firstRoll = new Random(0).nextDouble();
        Assertions.assertTrue(firstRoll > 0.5 && firstRoll < 0.9,
                "断言前提：Random(0) 首值应落在 0.5 ~ 0.9，实际 " + firstRoll);

        Assertions.assertEquals(1000, critSettled(0.5, 1.0), EPS);   // roll > 0.5 → 不暴
        Assertions.assertEquals(2000, critSettled(0.9, 1.0), EPS);   // roll < 0.9 → 暴击
    }

    @Test
    public void nonCrittableTypeSkipsCritZone() {
        Character attacker = attacker();
        attacker.setAttribute(AttributeType.CRIT_CHANCE, new DoubleValue(1.0));
        attacker.setAttribute(AttributeType.CRIT_ATTACK, new DoubleValue(1.0));

        // BREAK：不吃双暴（连骰都不骰），防御区照常生效
        Assertions.assertEquals(BASE * LEVEL_TERM / (1150 + LEVEL_TERM),
                settle(attacker, defender(1150), DamageType.BREAK, BASE), EPS);
    }

    @Test
    public void defenceZoneUsesAttackerLevelAndDefenderDefence() {
        Assertions.assertEquals(BASE * LEVEL_TERM / (1150 + LEVEL_TERM),
                settle(attacker(), defender(1150)), EPS);
    }

    @Test
    public void defenceIgnoreShrinksEffectiveDefence() {
        Character attacker = attacker();
        attacker.setAttribute(AttributeType.DEFENCE_IGNORE, new DoubleValue(0.5));

        Assertions.assertEquals(BASE * LEVEL_TERM / (575 + LEVEL_TERM),
                settle(attacker, defender(1150)), EPS);
    }

    @Test
    public void hpIsReducedExactlyOnce() {
        Character attacker = attacker();
        attacker.setAttribute(AttributeType.FIRE_DAMAGE_BOOST, new DoubleValue(0.3));
        Enemy defender = defender(0);
        double hpBefore = defender.getCurrentHp();

        Damage damage = new Damage(attacker, defender, DamageElement.FIRE, DamageType.NORMAL, BASE);
        double settled = battle(attacker, defender, 0).applyDamage(defender, damage);

        Assertions.assertEquals(1300, settled, EPS);
        Assertions.assertEquals(hpBefore - settled, defender.getCurrentHp(), EPS);
    }

    @Test
    public void deadTargetTakesNothing() {
        Character attacker = attacker();
        Enemy defender = defender(0);
        defender.takeDamage(defender.getCurrentHp());   // 直接打死
        Assertions.assertTrue(defender.isDeath());

        Damage damage = new Damage(attacker, defender, DamageElement.FIRE, DamageType.NORMAL, BASE);

        Assertions.assertEquals(0, battle(attacker, defender, 0).applyDamage(defender, damage), EPS);
        Assertions.assertEquals(0, defender.getCurrentHp(), EPS);
    }

    @Test
    public void settlementHasExactlyOnePublicEntryPoint() throws Exception {
        // 旧入口必须已删除（E2 完成）
        Assertions.assertThrows(NoSuchMethodException.class, () -> Battle.class.getDeclaredMethod(
                "calculateDamage", CanHit.class, CanHit.class, double.class, List.class));
        Assertions.assertThrows(NoSuchMethodException.class, () -> Battle.class.getDeclaredMethod(
                "applyDamage", CanHit.class, double.class));

        // 装配口必须私有：外部只能经 applyDamage 进入
        Assertions.assertTrue(Modifier.isPrivate(Battle.class
                .getDeclaredMethod("assemble", Damage.class).getModifiers()));

        // 公开 API 里接收 Damage 的方法只能有一个
        long publicDamageEntries = Arrays.stream(Battle.class.getMethods())
                .filter(method -> Arrays.asList(method.getParameterTypes()).contains(Damage.class))
                .count();
        Assertions.assertEquals(1, publicDamageEntries, "结算入口必须唯一");
    }
}
