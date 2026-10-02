package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.DamageElement;
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
 * 会过期的弱点（`65f143b`）：`ADD_ELEMENTAL_WEAKNESS` 带 `turns` 时走 {@code Enemy.addWeakness(element, turns)}，
 * 而计时表在**目标自己的回合结束**时递减（`Battle:1203` 的 `TURN_END` 旁）。
 *
 * <p>⚠ 这个判据**必须驱动真实的回合金流程**（`battle.stepForward()`）。若只调 `enemy.tickTimedWeaknesses()`，
 * `Enemy` 那一半会被覆盖，而 **`Battle:1203` 的接线写错了也不会红** —— 本段见过太多次"判据只覆盖一半"。
 *
 * <p>⚠ 两句断言缺一不可：「最终失效」证明递减会发生；「不是第一步就掉」证明**计数**对（否则把 `turns: 2`
 * 写成 `turns: 0` 也会让第一句变绿）。
 */
public class TimedWeaknessTest {
    private static final int WEARER = 1006;
    private static final int MONSTER = 1002011;
    private static final int LEVEL = 80;
    private static final int TURNS = 2;

    @Test
    public void anInsertedWeaknessExpiresInItsOwnTurns() {
        Character wearer = CharacterFactory.create(WEARER, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        // ⚠ 不写死属性：挑一个这个敌人**没有**的。写死会让判据依赖它的属性表
        // （实测：1002011 本来就有 Fire，第一次运行就是被这条前置挡住的）。
        DamageElement chosen = null;
        for (DamageElement each : DamageElement.values()) {
            if (!enemy.isWeakTo(each)) {
                chosen = each;
                break;
            }
        }
        Assertions.assertNotNull(chosen, "this enemy is weak to every element, which cannot happen");
        EffectSpec add = new EffectSpec();
        TriggerSpecs.set(add, "op", "ADD_ELEMENTAL_WEAKNESS");
        TriggerSpecs.set(add, "element", chosen.name());
        TriggerSpecs.set(add, "turns", TURNS);
        TriggerSpecs.set(add, "target", "target");
        TriggerSpec rule = TriggerSpecs.rule(TriggerEvent.SKILL_CAST.name(), List.of(), add);
        TriggerSpecs.set(rule, "id", "probe_timed_weakness");
        TriggerSpecs.set(rule, "when", List.of("actor == self"));
        wearer.setTriggerTable(new TriggerTable(WEARER, List.of(rule)));

        Battle battle = new Battle(List.of(wearer), List.of(enemy), new Random(0));
        battle.startBattle();
        Assertions.assertFalse(enemy.isWeakTo(chosen), "the enemy must not already have it");

        battle.fireTriggers(TriggerEvent.SKILL_CAST, wearer, enemy, 0, 0);
        Assertions.assertTrue(enemy.isWeakTo(chosen), "the insertion itself must land");

        int steps = 0;
        while (enemy.isWeakTo(chosen) && !battle.isOver() && steps < 60) {
            battle.stepForward();      // ⚠ 只把 currentMove 设成下一个行动者（实测：它不执行回合）
            battle.afterMove();        // ⭐ 结束那次行动 —— 计时弱点的递减就在 afterMove 里面
            steps++;
        }
        System.out.println("[timed] " + chosen + " weakness gone after " + steps + " steps (declared turns=" + TURNS
                + ", over=" + battle.isOver() + ")");
        Assertions.assertFalse(enemy.isWeakTo(chosen),
                "a timed weakness must expire -- " + steps + " steps and it is still there");
        Assertions.assertTrue(steps > 1,
                "but it must not fall on the very first step: the count would be wrong, not just the ticking");
    }

    /**
     * ⚠ **把两半切开**：直接调 `Enemy.tickTimedWeaknesses()`（它是 `public`）。
     *
     * <p>上面那条走的是真实回合金流程，⚠ 而它 60 步都没失效 ⇒ 两种可能：`Enemy` 的递减本身坏了，
     * 或者**那条流程根本没走到敌人的回合**。这一条只问前半 —— ⚠ 若它绿，问题一定在接线上。
     */
    @Test
    public void theEnemySideRunsDownOnItsOwn() {
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        DamageElement chosen = null;
        for (DamageElement each : DamageElement.values()) {
            if (!enemy.isWeakTo(each)) {
                chosen = each;
                break;
            }
        }
        Assertions.assertNotNull(chosen);
        enemy.addWeakness(chosen, TURNS);
        Assertions.assertTrue(enemy.isWeakTo(chosen), "declared with turns, so it is a weakness right now");
        System.out.println("[timed] direct: inserted " + chosen + " with turns=" + TURNS
                + " count=" + enemy.weaknessCount());

        enemy.tickTimedWeaknesses();
        Assertions.assertTrue(enemy.isWeakTo(chosen), "after one tick it still has one turn left");
        enemy.tickTimedWeaknesses();
        System.out.println("[timed] direct: after two ticks weak=" + enemy.isWeakTo(chosen)
                + " count=" + enemy.weaknessCount());
        Assertions.assertFalse(enemy.isWeakTo(chosen), "two ticks and a turns=2 weakness is gone");
    }
}
