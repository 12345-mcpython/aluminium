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
 * 8003/8004 存护开拓者's ultimate:「下一次施放普攻时，自动获得强化且**不消耗**【灼热意志】」.
 *
 * <p>The "free" basic is a marker state: the ultimate grants the swap AND the marker, the spending rule is gated on the marker
 * being ABSENT, and the marker is spent by its own rule. So one enhanced basic costs nothing, and the next one costs four.
 */
public class TrailblazerFreeEnhancedBasicTest {
    private static final int[] BOTH = {8003, 8004};
    private static final int LEVEL = 80;

    @Test
    public void theUltimatesBasicIsFreeAndTheOneAfterItIsNot() {
        for (int cid : BOTH) {
            Character trailblazer = CharacterFactory.create(cid, LEVEL);
            Enemy enemy = BattleFixture.monster();
            enemy.setMaxStance(10_000);
            enemy.setStance(10_000);
            Battle battle = new Battle(List.of(trailblazer), List.of(enemy), BattleFixture.alwaysRoll(0.0));
            battle.startBattle();
            // Four stacks the ordinary way, so 「不消耗」 has something visible to preserve.
            for (int hit = 0; hit < 4; hit++) {
                battle.applyDamage(trailblazer, new Damage(enemy, trailblazer, DamageElement.FIRE,
                        DamageType.NORMAL, 1));
                battle.processRequests();
            }
            Assertions.assertEquals(4, trailblazer.getBuffManager().stacksOf("灼热意志"), "cid " + cid + ": precondition");

            trailblazer.setCurrentEnergy(trailblazer.getMaxEnergy());
            battle.castImmediate(trailblazer.getSkills().get(SkillType.ULTRA), trailblazer, List.of(enemy));
            battle.processRequests();
            Assertions.assertTrue(trailblazer.getBuffManager().hasState("免费强化"), "cid " + cid + ": the marker is on");

            battle.castImmediate(trailblazer.getSkills().get(SkillType.COMMON), trailblazer, List.of(enemy));
            battle.processRequests();
            Assertions.assertEquals(4, trailblazer.getBuffManager().stacksOf("灼热意志"),
                    "cid " + cid + ": 「…且不消耗【灼热意志】」 — four before, four after");
            Assertions.assertFalse(trailblazer.getBuffManager().hasState("免费强化"),
                    "cid " + cid + ": the marker was spent by that attack");
        }
    }
}
