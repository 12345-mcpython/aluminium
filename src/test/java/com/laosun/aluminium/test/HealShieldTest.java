package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.EnemyFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * P6-2 / P6-3 验收：治疗乘区与护盾。
 *
 * <pre>
 * 治疗量 = 基础量 × (1 + 治疗加成) × (1 + 受疗加成)        ← 两个因子来自不同的人
 * 护盾：伤害先扣盾，盾破前不死；护盾不叠加
 * </pre>
 */
public class HealShieldTest {
    private static final double EPS = 1e-9;

    // ==================================================================
    // P6-2 治疗乘区
    // ==================================================================

    @Test
    public void healIsBaseTimesOutgoingTimesTaken() {
        Character healer = character("healer");
        Character target = character("target");
        healer.setAttribute(AttributeType.OUTGOING_HEALING_BOOST, new DoubleValue(0.3));
        target.setAttribute(AttributeType.HEAL_TAKEN_RATIO, new DoubleValue(0.2));
        Battle battle = newBattle(healer, target);

        Assertions.assertEquals(1560, battle.calculateHeal(healer, target, 1000), EPS,
                "1000 × 1.3 × 1.2 = 1560");
    }

    @Test
    public void negativeHealTakenRatioActsAsHealingReduction() {
        Character healer = character("healer");
        Character target = character("target");
        target.setAttribute(AttributeType.HEAL_TAKEN_RATIO, new DoubleValue(-0.5));
        Battle battle = newBattle(healer, target);

        Assertions.assertEquals(500, battle.calculateHeal(healer, target, 1000), EPS,
                "受疗 -50% → 1000 × 1 × 0.5 = 500（游戏里没有单独的『治疗降低』属性，用负数表达）");
    }

    @Test
    public void healActuallyRestoresHpAndIsCappedAtMaxHp() {
        Character healer = character("healer");
        Character target = character("target");
        healer.setAttribute(AttributeType.OUTGOING_HEALING_BOOST, new DoubleValue(0.3));
        target.setAttribute(AttributeType.HEAL_TAKEN_RATIO, new DoubleValue(0.2));
        Battle battle = newBattle(healer, target);

        target.takeDamage(2000);
        double before = target.getCurrentHp();
        double healed = battle.heal(healer, target, 1000);

        Assertions.assertEquals(1560, healed, EPS, "实际回复量 = 乘区后的治疗量");
        Assertions.assertEquals(before + 1560, target.getCurrentHp(), EPS);

        // 再奶一次：这次会撞上限，返回的是**实际**回复量
        double healed2 = battle.heal(healer, target, 100_000);
        Assertions.assertEquals(target.getMaxHp(), target.getCurrentHp(), EPS, "不超上限");
        Assertions.assertEquals(target.getMaxHp() - (before + 1560), healed2, EPS,
                "返回值是实际回复量，不是理论治疗量");
    }

    @Test
    public void deadTargetIsNotHealed() {
        Character healer = character("healer");
        Character target = character("target");
        Battle battle = newBattle(healer, target);

        target.takeDamage(999_999);
        Assertions.assertTrue(target.isDeath());

        Assertions.assertEquals(0, battle.heal(healer, target, 1000), EPS, "死人不能被治疗");
        Assertions.assertEquals(0, target.getCurrentHp(), EPS);
    }

    // ==================================================================
    // P6-3 护盾
    // ==================================================================

    @Test
    public void shieldAbsorbsDamageBeforeHp() {
        Character target = character("target");
        Battle battle = newBattle(character("ally"), target);

        Assertions.assertEquals(500, battle.grantShield(target, 500), EPS);
        target.takeDamage(300);

        Assertions.assertEquals(200, target.getShield(), EPS, "盾 500 - 300 = 200");
        Assertions.assertEquals(target.getMaxHp(), target.getCurrentHp(), EPS, "HP 一点没掉");
    }

    @Test
    public void overflowAfterTheShieldBreaksHitsHp() {
        Character target = character("target");
        Battle battle = newBattle(character("ally"), target);
        battle.grantShield(target, 500);

        target.takeDamage(300);                                  // 盾 → 200
        boolean died = target.takeDamage(300);                   // 盾吃 200，剩下 100 进 HP

        Assertions.assertFalse(died, "盾破不死");
        Assertions.assertEquals(0, target.getShield(), EPS, "盾空了");
        Assertions.assertEquals(target.getMaxHp() - 100, target.getCurrentHp(), EPS, "HP -100");
        Assertions.assertFalse(target.isDeath());
    }

    /**
     * 护盾**不叠加**：新盾覆盖旧值，不相加。
     */
    @Test
    public void shieldDoesNotStack() {
        Character target = character("target");
        Battle battle = newBattle(character("ally"), target);

        battle.grantShield(target, 500);
        battle.grantShield(target, 300);

        Assertions.assertEquals(300, target.getShield(), EPS, "覆盖而不是 800");
        Assertions.assertEquals(0, battle.grantShield(target, -50), EPS, "≤ 0 视为清盾");
        Assertions.assertEquals(0, target.getShield(), EPS);
    }

    /**
     * 被打进盾里的伤害**要算进这一击的伤害**（否则"打在有盾的目标上"会显示成 0，
     * {@code AttackEvent.totalDamage} 与击杀回能都会失真）。
     */
    @Test
    public void damageAbsorbedByTheShieldStillCountsAsDamageDealt() {
        Enemy attacker = EnemyFactory.create(1002011, 90, 1);
        Character target = character("target");
        target.setMaxEnergy(120);
        Battle battle = newBattle(attacker, target);
        battle.grantShield(target, 10_000);                      // 盾远大于这一击

        double hpBefore = target.getCurrentHp();
        double dealt = battle.applyDamage(target, new Damage(attacker, target,
                DamageElement.ICE, DamageType.NORMAL, 1000));

        // 这一击的**结算值**要先过防御区（角色防御 100）：1000 × 1600/(100+1600) ≈ 941.18
        double levelTerm = com.laosun.aluminium.Constant.DEFENCE_CONST
                + com.laosun.aluminium.Constant.DEFENCE_PER_LEVEL * attacker.getLevel();
        double settled = 1000 * levelTerm / (100 + levelTerm);

        Assertions.assertEquals(hpBefore, target.getCurrentHp(), EPS, "HP 没动（全被盾吃了）");
        Assertions.assertEquals(settled, target.getLastShieldAbsorbed(), EPS);
        Assertions.assertEquals(settled, dealt, EPS, "返回的是「结算值 + 盾吸收量」");
    }

    @Test
    public void invulnerableAndDeadTargetsStillIgnoreShields() {
        Character target = character("target");
        Battle battle = newBattle(character("ally"), target);
        battle.grantShield(target, 500);

        target.setInvulnerable(true);
        Assertions.assertEquals(0, battle.applyDamage(target, new Damage(
                character("x"), target, DamageElement.ICE, DamageType.NORMAL, 1000)), EPS);
        Assertions.assertEquals(500, target.getShield(), EPS, "无敌期间连盾都不掉");
    }

    // ==================================================================

    private static Character character(String name) {
        return Character.fromAttributes(name, 3000, 100, 100, 100);
    }

    private static Battle newBattle(com.laosun.aluminium.models.CanHit a,
                                   com.laosun.aluminium.models.CanHit b) {
        List<Character> characters = new java.util.ArrayList<>();
        List<Enemy> enemies = new java.util.ArrayList<>();
        for (com.laosun.aluminium.models.CanHit c : List.of(a, b)) {
            if (c instanceof Character character) {
                characters.add(character);
            } else if (c instanceof Enemy enemy) {
                enemies.add(enemy);
            }
        }
        if (characters.isEmpty()) {
            characters.add(character("filler"));
        }
        if (enemies.isEmpty()) {
            enemies.add(EnemyFactory.create(1002011, 90, 1));
        }
        return new Battle(characters, enemies, new Random(20260919));
    }
}
