package com.laosun.aluminium.test.content.characters.aglaea;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.buff.StateBuff;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1402 阿格莱雅's technique:「召唤忆灵衣匠…进入战斗后，恢复 30 点能量，并对敌方全体造成 100% 攻击力的雷属性伤害，随后使敌方随机目标陷入
 * 【间隙织线】状态」.
 *
 * <p>Each of the four halves is measured against the same fight <b>without</b> the technique declared, so the difference is
 * the technique and not something else the entry point does.
 */
public class AglaeaTechniqueTest {
    private static final int AGLAEA = 1402;
    private static final int LEVEL = 80;
    private static final String THREAD = "间隙织线";

    @Test
    public void theTechniqueSummonsHitsEveryonePaysThirtyAndThreadsExactlyOne() {
        Scene with = run(true);
        Scene without = run(false);

        Assertions.assertNotNull(with.battle.summonOf(with.aglaea), "「召唤忆灵衣匠」 — the memosprite is on the field");
        Assertions.assertNull(without.battle.summonOf(without.aglaea), "control: nothing summons it without the technique");

        Assertions.assertTrue(with.lost > 0, "「对敌方全体造成…100% 攻击力的雷属性伤害」");
        Assertions.assertTrue(with.lost > without.lost, "and it lands on top of the routine opening");
        Assertions.assertEquals(30.0, with.energy - without.energy, 1e-9, "「恢复 30 点能量」");

        Assertions.assertEquals(1, with.threaded, "「使敌方随机目标陷入」 — exactly one enemy is threaded");
        Assertions.assertEquals(0, without.threaded, "control: no technique, no 【间隙织线】");
    }

    /** What one battle looks like after BATTLE_START, with or without the technique declared. */
    private static Scene run(boolean technique) {
        Character aglaea = CharacterFactory.create(AGLAEA, LEVEL);
        // Note: above half of her bar, because her trace 飞驰之阳 raises energy TO 50% at battle start - measured
        // 2026-11-04: starting from zero, the trace's floor swallows the technique's 30 points and the delta reads 0.
        // That is the trace's own clause working, not the technique's failing, so the fixture starts above the floor.
        aglaea.setCurrentEnergy(aglaea.getMaxEnergy() * 0.6);
        if (technique) {
            // The engine stamps this state before any BATTLE_START rule runs, which is the gate the rule reads.
            aglaea.getBuffManager().addBuff(new StateBuff("秘技", 1));
        }
        Enemy first = BattleFixture.dummy();
        Enemy second = BattleFixture.dummy();
        Battle battle = new Battle(new ArrayList<>(List.of(aglaea)),
                new ArrayList<>(List.of(first, second)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        double lost = 0;
        int threaded = 0;
        for (var unit : battle.enemyUnits()) {
            lost += unit.getMaxHp() - unit.getCurrentHp();
            if (unit.getBuffManager().hasState(THREAD)) {
                threaded++;
            }
        }
        return new Scene(battle, aglaea, lost, aglaea.getCurrentEnergy(), threaded);
    }

    private record Scene(Battle battle, Character aglaea, double lost, double energy, int threaded) {
    }
}
