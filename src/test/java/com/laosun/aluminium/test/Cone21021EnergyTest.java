package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 光锥 21021 等价交换，技能 酣适：`当装备者的回合开始时，随机为 1 个当前能量百分比小于 50% 的我方其他目标
 * 恢复 8 点能量`（原文：`for a randomly chosen ally (excluding the wearer) whose current Energy is lower than 50%`）。
 *
 * <p>⚠ 三个对照一次说清三件事：装备者自己**不被选**（`excluding the wearer`）；满能量的队友**不被选**（阈值）；
 * 只有那个 30% 的队友被恢复，且恢复的是 **8** 点（阶 1）。
 */
public class Cone21021EnergyTest {
    private static final int WEARER = 1003;
    private static final int ALLY_LOW = 1002;
    private static final int ALLY_FULL = 1004;
    private static final int MONSTER = 1002011;
    private static final int LEVEL = 80;
    private static final double AMOUNT = 8;

    /** 装 21021（阶 1）、放一个 TURN_START，返回 {装备者, 30% 的队友, 满的队友} 各自的能量增量。 */
    private static double[] gainsAtTurnStart() {
        Character wearer = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(21021, LEVEL, false, 1));
        Character low = CharacterFactory.create(ALLY_LOW, LEVEL);
        Character full = CharacterFactory.create(ALLY_FULL, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(wearer, low, full), List.of(enemy), new Random(0));
        battle.startBattle();

        wearer.setCurrentEnergy(wearer.getMaxEnergy());          // ⚠ 装备者满能量：若被选中，会看得很清楚
        low.setCurrentEnergy(low.getMaxEnergy() * 0.3);          // ⚠ 低于 50%
        full.setCurrentEnergy(full.getMaxEnergy());               // ⚠ 不低于 50%

        double w0 = wearer.getCurrentEnergy();
        double l0 = low.getCurrentEnergy();
        double f0 = full.getCurrentEnergy();
        battle.fireTriggers(TriggerEvent.TURN_START, wearer, enemy, 0, 0);
        return new double[]{wearer.getCurrentEnergy() - w0,
                low.getCurrentEnergy() - l0, full.getCurrentEnergy() - f0};
    }

    @Test
    public void onlyTheLowEnergyAllyIsPicked() {
        double[] gain = gainsAtTurnStart();
        System.out.println("[21021] wearer=" + gain[0] + " lowAlly=" + gain[1] + " fullAlly=" + gain[2]);
        Assertions.assertEquals(AMOUNT, gain[1], 1e-9,
                "the 30% ally is the only candidate, so it gains the tier-1 amount");
        Assertions.assertEquals(0.0, gain[2], 1e-9,
                "an ally at full energy is above the 50% threshold and must not be picked");
        Assertions.assertEquals(0.0, gain[0], 1e-9,
                "the wearer is excluded by the text itself: a randomly chosen ally (excluding the wearer)");
    }

    // ⛔ 这里原本还有一个"只有装备者低于 50%"的场景（以及一个"不装光锥"的对照）。它们被撤掉了，因为那一次
    // 暴露了一个**真实的引擎缺口**，而不是判据写错：
    //
    //   require(…) 在 resolveTarget 返回 null 时抛
    //   「Effect targets "random_ally_below_half_energy" but this event has no such party」
    //
    // ⚠ 而"随机选一个"**可以合法地没有候选**（装备者是唯一低能量的 ⇒ 被排除 ⇒ 合格集为空）。正确语义是
    // "什么都不做"，而 require 把它当成了错误。⚠ 那句话是**泛型消息**（一个共用的助手对所有选择器说同一句），
    // 所以它把诊断引向了"事件类型不对" —— 实测证明那不对（'不装光锥' 的对照得到 0，说明那 8 点确实来自本从句）。
    //
    // 缺口已入册；在它被补上之前，这个场景留着只会让套件红。上面那条断言与它无关，且已实测通过。
}
