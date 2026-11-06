package com.laosun.aluminium.test.content.characters.playerboy2;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 8003/8004 存护开拓者's 天赋:「【灼热意志】层数不低于 4 时，普攻将获得强化，对指定敌方单体及其相邻目标同时造成伤害」, and the
 * enhanced basic's own 「消耗 4 层」.
 *
 * <p>Both halves are one swap: `REPLACE_SKILL` puts data row 8 into the basic slot at four stacks, and the enhanced attack
 * spends four - after which the slot is the plain row again. Written 2026-11-06 for both files (they share one document).
 */
public class TrailblazerMagmaWillSwapTest {
    private static final int[] BOTH = {8003, 8004};
    private static final int LEVEL = 80;
    private static final double EPS = 1e-9;

    @Test
    public void fourStacksSwapTheBasicAndItSpendsThem() {
        for (int cid : BOTH) {
            Character trailblazer = CharacterFactory.create(cid, LEVEL);
            Enemy enemy = BattleFixture.monster();
            enemy.setMaxStance(10_000);
            enemy.setStance(10_000);
            Battle battle = new Battle(List.of(trailblazer), List.of(enemy), BattleFixture.alwaysRoll(0.0));
            battle.startBattle();
            Assertions.assertEquals(1, trailblazer.getSkills().get(SkillType.COMMON).getSkillSlot(),
                    "cid " + cid + ": precondition, the plain basic");

            // Four hits on him: each one is a stack, and the fourth crosses the threshold.
            for (int hit = 0; hit < 4; hit++) {
                battle.applyDamage(trailblazer, new Damage(enemy, trailblazer, DamageElement.FIRE,
                        DamageType.NORMAL, 1));
                battle.processRequests();
            }
            Assertions.assertEquals(4, trailblazer.getBuffManager().stacksOf("灼热意志"),
                    "cid " + cid + ": four hits, four stacks");
            Assertions.assertEquals(8, trailblazer.getSkills().get(SkillType.COMMON).getSkillSlot(),
                    "cid " + cid + ": 「层数不低于 4 时，普攻将获得强化」 — the slot holds data row 8");

            battle.castImmediate(trailblazer.getSkills().get(SkillType.COMMON), trailblazer, List.of(enemy));
            battle.processRequests();
            Assertions.assertEquals(0, trailblazer.getBuffManager().stacksOf("灼热意志"),
                    "cid " + cid + ": 「消耗 4 层【灼热意志】」");
            Assertions.assertEquals(1, trailblazer.getSkills().get(SkillType.COMMON).getSkillSlot(),
                    "cid " + cid + ": and below four stacks the plain basic is back");
        }
    }
}
