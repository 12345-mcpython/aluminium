package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 开拓者(毁灭)'s 星魂 4: "击中处于弱点击破状态的敌方目标时，暴击率提高25%" -- a state-gated boost for that hit.
 *
 * <p>Two ways: an enemy whose toughness is emptied is 弱点击破 and the boost is stated; one that is not gets nothing. The
 * state is produced through the engine's own entry point, the same way SuperBreakTest's fixture does it.
 */
public class TrailblazerEidolonTest {
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final double EPS = 1e-9;

    @Test
    public void theBoostIsStatedOnlyAgainstABrokenTarget() {
        for (int cid : new int[] {8001, 8002}) {
            // Note: A difference, not an absolute: an intact unit still reads the game's inherent +0.05 crit chance
            // (measured: 0.30 where the clause owns 0.25).
            Assertions.assertEquals(0.25, critChance(cid, true) - critChance(cid, false), EPS,
                    "cid " + cid + ": the clause's own 25%");
            Assertions.assertEquals(0.05, critChance(cid, false), EPS,
                    "cid " + cid + ": against an intact target only the inherent remains");
        }
    }

    private static double critChance(int cid, boolean broken) {
        Character trailblazer = CharacterFactory.create(cid, LEVEL, true, null, null, 4);
        DamageElement element = trailblazer.getSkills().get(SkillType.COMMON).getData().getElement();
        // Note: A monster the element can actually break: breaking requires a weakness, and `reduceToughness` on a monster
        // that is not weak to the element leaves the 弱点击破 state off entirely (measured: hasState false).
        Enemy enemy = null;
        for (int id = 1002010; id < 1002100 && enemy == null; id++) {
            try {
                Enemy candidate = EnemyFactory.create(id, 90, 1);
                if (candidate.isWeakTo(element)) {
                    enemy = candidate;
                }
            } catch (RuntimeException ignored) {
                // not in the data
            }
        }
        Assertions.assertNotNull(enemy, "no enemy weak to " + element + " in the probed range");
        Battle battle = new Battle(List.of(trailblazer), List.of(enemy), new Random(0));
        battle.startBattle();
        if (broken) {
            battle.reduceToughness(trailblazer, enemy, element, 999);
            Assertions.assertTrue(enemy.getBuffManager().hasState("弱点击破"),
                    "precondition: emptying the bar of a weak enemy puts it in 弱点击破");
        }
        battle.fireTriggers(TriggerEvent.ALLY_ATTACK, trailblazer, enemy, 1, 1000);
        return trailblazer.getAttribute(AttributeType.CRIT_CHANCE).get()
                - trailblazer.getAttribute(AttributeType.CRIT_CHANCE).baseValue();
    }
}
