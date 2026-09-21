package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 终结技的**开大阈值**（P3-4 跟进）：攒够 {@code sp_need} 就能放，不必攒满 {@code maxEnergy}。
 *
 * <p>93 个角色里有 5 个阈值**低于**上限（数据与角色文档一致）：
 *
 * <pre>
 *   云璃 1221  需要 120 / 上限 240     飞霄 1220  需要 6  / 上限 12
 *   银枝 1302  需要  90 / 上限 180     昔涟 1415  需要 12 / 上限 24（层数，见 §9.5）
 *   绯英 1505  需要 240 / 上限 480
 * </pre>
 *
 * <p>角色文档写的是「**释放所需能量** 120（上限 240）」——"所需"是门槛。
 * 修之前引擎判 {@code currentEnergy >= maxEnergy}，所以云璃会攒到 240 才肯放。
 */
public class UltraThresholdTest {
    private static final double EPS = 1e-9;

    // ==================================================================
    // 一、门槛值来自数据
    // ==================================================================

    /**
     * {@code ultraEnergyCost} 读 {@code sp_need}；多数角色它等于上限。
     */
    @Test
    public void ultraCostComesFromTheSkillData() {
        Battle battle = newBattle(CharacterFactory.create(1221, 80));   // 云璃
        Character yunli = battle.characters.getFirst();

        Assertions.assertEquals(240, yunli.getMaxEnergy(), EPS, "面板上限是 240");
        Assertions.assertEquals(120, battle.ultraEnergyCost(yunli), EPS,
                "开大只需要 120（数据 sp_need）");
    }

    /**
     * 阈值 == 上限的常规角色不受影响（景元 130/130）。
     */
    @Test
    public void regularCharactersThresholdEqualsTheirCap() {
        Battle battle = newBattle(CharacterFactory.create(1204, 80));
        Character jingYuan = battle.characters.getFirst();

        Assertions.assertEquals(130, battle.ultraEnergyCost(jingYuan), EPS);
        Assertions.assertEquals(jingYuan.getMaxEnergy(), battle.ultraEnergyCost(jingYuan), EPS);
    }

    /** 全部 93 个角色：阈值必须 ≤ 上限，且 > 0。 */
    @Test
    public void thresholdNeverExceedsTheCap() {
        for (var entry : Constant.CHARACTERS.entrySet()) {
            Character c = CharacterFactory.create(entry.getKey(), 80);
            Battle battle = newBattle(c);
            double cost = battle.ultraEnergyCost(c);

            if (!c.hasEnergyBar()) {
                Assertions.assertEquals(0, cost, EPS, "cid=" + entry.getKey() + " 没有能量条");
                continue;
            }
            Assertions.assertTrue(cost > 0, "cid=" + entry.getKey() + " 阈值应当为正");
            Assertions.assertTrue(cost <= c.getMaxEnergy() + EPS,
                    "cid=" + entry.getKey() + " 阈值 " + cost + " 不该超过上限 " + c.getMaxEnergy());
        }
    }

    /** 恰好 5 个角色的阈值低于上限 —— 穷举登记，多一个少一个都要显式改。 */
    @Test
    public void exactlyFiveCharactersHaveALowerThreshold() {
        int lower = 0;
        for (var entry : Constant.CHARACTERS.entrySet()) {
            Character c = CharacterFactory.create(entry.getKey(), 80);
            if (!c.hasEnergyBar()) {
                continue;
            }
            Battle battle = newBattle(c);
            if (battle.ultraEnergyCost(c) < c.getMaxEnergy() - EPS) {
                lower++;
            }
        }
        Assertions.assertEquals(5, lower,
                "阈值低于上限的角色应为 5（云璃/银枝/绯英/飞霄/昔涟）");
    }

    // ==================================================================
    // 二、判定与消耗
    // ==================================================================

    /**
     * 核心：云璃攒到 **120** 就能放，不必等到 240。
     */
    @Test
    public void yunliCanCastAtHalfOfHerCap() {
        Character yunli = CharacterFactory.create(1221, 80);
        Battle battle = newBattle(yunli);

        yunli.setCurrentEnergy(119);
        Assertions.assertFalse(battle.isUltraReady(yunli), "差 1 点还不能放");
        Assertions.assertFalse(battle.castUltra(yunli, List.of(firstEnemy(battle))));

        yunli.setCurrentEnergy(120);
        Assertions.assertTrue(battle.isUltraReady(yunli), "到 120 就能放");
        Assertions.assertTrue(battle.castUltra(yunli, List.of(firstEnemy(battle))));
    }

    /**
     * 放完之后**清零**：对阈值 < 上限的角色，等价于"消耗掉阈值那部分"。
     */
    @Test
    public void castingConsumesTheStoredEnergy() {
        Character yunli = CharacterFactory.create(1221, 80);
        Battle battle = newBattle(yunli);

        yunli.setCurrentEnergy(120);
        Assertions.assertTrue(battle.castUltra(yunli, List.of(firstEnemy(battle))));

        // 引擎会在本体结算后再给 5 点（onUltCast），所以是 5 而不是 0
        Assertions.assertEquals(5, yunli.getCurrentEnergy(), EPS,
                "放开后清零，再回自身的 5 点");
        Assertions.assertFalse(battle.isUltraReady(yunli), "刚放完不能再放");
    }

    /**
     * 常规角色行为不变：攒满才放。
     */
    @Test
    public void regularCharacterStillNeedsFullEnergy() {
        Character jingYuan = CharacterFactory.create(1204, 80);
        Battle battle = newBattle(jingYuan);

        jingYuan.setCurrentEnergy(129);
        Assertions.assertFalse(battle.isUltraReady(jingYuan));
        Assertions.assertFalse(battle.castUltra(jingYuan, List.of(firstEnemy(battle))));

        jingYuan.setCurrentEnergy(130);
        Assertions.assertTrue(battle.isUltraReady(jingYuan));
        Assertions.assertTrue(battle.castUltra(jingYuan, List.of(firstEnemy(battle))));
    }

    /**
     * 没有能量条的角色（遐蝶 1407）永远放不了 —— 她连 {@code hasEnergyBar()} 都是 false。
     */
    @Test
    public void noEnergyBarStillCannotCast() {
        Character castorice = CharacterFactory.create(1407, 80);
        Battle battle = newBattle(castorice);

        Assertions.assertFalse(castorice.hasEnergyBar());
        castorice.setCurrentEnergy(9999);        // 就算硬灌也不行
        Assertions.assertFalse(battle.isUltraReady(castorice));
        Assertions.assertFalse(battle.castUltra(castorice, List.of(firstEnemy(battle))));
    }

    /**
     * 特殊资源角色即便被硬灌到上限也放不出 —— provider 不给她能量，但这里验证判定本身也不放行
     * （她的 {@code sp_need} 是 12、上限 24，所以灌到 20 反而"够门槛"了 ——
     *  这正是为什么真正的防线是 {@code NoConventionalEnergyProvider} 让她攒不起来）。
     */
    @Test
    public void specialResourceCharacterCannotAccumulateInRealBattle() {
        Character cyrene = CharacterFactory.create(1415, 80);
        Battle battle = newBattle(cyrene);
        Enemy enemy = firstEnemy(battle);

        // 真实战斗里跑几轮：她的能量必须恒为 0，因此永远不满足门槛
        for (int i = 0; i < 8 && !battle.isOver(); i++) {
            battle.stepForward();
            if (battle.currentMove == null) {
                break;
            }
            var actor = battle.currentMove.getCanHit();
            battle.beforeMove();
            if (actor == cyrene) {
                Assertions.assertFalse(battle.isUltraReady(cyrene),
                        "能量恒为 0 → 永远不该 ready，实际 " + cyrene.getCurrentEnergy());
            }
            battle.afterMove();
        }
        Assertions.assertEquals(0, cyrene.getCurrentEnergy(), EPS);
    }

    // ==================================================================

    private static Battle newBattle(Character hero) {
        Enemy enemy = EnemyFactory.create(1002011, 90, 1);
        Battle battle = new Battle(List.of(hero), List.of(enemy), new Random(0));
        battle.startBattle();
        return battle;
    }

    private static Enemy firstEnemy(Battle battle) {
        return battle.enemies.getFirst();
    }
}
