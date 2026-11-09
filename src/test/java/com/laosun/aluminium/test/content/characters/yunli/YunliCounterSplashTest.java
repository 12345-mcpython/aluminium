package com.laosun.aluminium.test.content.characters.yunli;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1221 云璃's talent counter: 「立即向攻击者发起反击，对其造成等同于云璃 120% 攻击力的物理属性伤害，**对其相邻目标造成等同于云璃 60% 攻击力的物理属性伤害**」.
 *
 * <p>The neighbour share is stated as a percentage of <b>her attack</b>, so at a glance it looks like a second DAMAGE effect. It is
 * not: the second sentence names 「相邻目标」 of the unit the first one hit, and that unit is only known after the counter picks its
 * target. {@code blast} is the field that says exactly this - the neighbours take a share of the MAIN target's instance - and
 * {@code 0.5} is the document's own ratio (60% / 120%).
 */
public class YunliCounterSplashTest {
    private static final int YUNLI = 1221;
    private static final int LEVEL = 80;

    @Test
    public void theCounterSplashesHalfOfItsDamageOntoTheNeighbours() {
        Scene scene = new Scene();
        double before = scene.totalHp();
        // The enemy in the middle of the roster attacks her: that enemy is the counter's target, its roster neighbours take the splash.
        scene.battle.fire(TriggerPayload.Simple.of(TriggerEvent.TAKING_HIT, scene.middle, scene.yunli, 0, 0));
        scene.battle.processRequests();

        double main = scene.centreBefore - scene.middle.getCurrentHp();
        double left = scene.leftBefore - scene.left.getCurrentHp();
        double right = scene.rightBefore - scene.right.getCurrentHp();
        System.out.println("[1221] counter main=" + main + " neighbours=" + left + "/" + right
                + " total=" + (before - scene.totalHp()));
        Assertions.assertTrue(main > 0, "precondition: the counter hit the attacker");
        Assertions.assertEquals(0.5, left / main, 1e-6,
                "「对其相邻目标造成等同于云璃 60% 攻击力的物理属性伤害」 - 60% against the main target's 120%");
        Assertions.assertEquals(0.5, right / main, 1e-6, "and on the other side too");
    }

    // ==================================================================

    private static final class Scene {
        private final Character yunli;
        private final Enemy left = BattleFixture.monster();
        private final Enemy middle = BattleFixture.monster();
        private final Enemy right = BattleFixture.monster();
        private final Battle battle;
        private final double leftBefore;
        private final double centreBefore;
        private final double rightBefore;

        private Scene() {
            yunli = CharacterFactory.create(YUNLI, LEVEL);
            for (Enemy enemy : List.of(left, middle, right)) {
                enemy.setMaxStance(100_000);
                enemy.setStance(100_000);
            }
            battle = new Battle(new ArrayList<>(List.of(yunli)), new ArrayList<>(List.of(left, middle, right)),
                    BattleFixture.alwaysRoll(0.0));
            battle.startBattle();
            battle.processRequests();
            leftBefore = left.getCurrentHp();
            centreBefore = middle.getCurrentHp();
            rightBefore = right.getCurrentHp();
        }

        private double totalHp() {
            return left.getCurrentHp() + middle.getCurrentHp() + right.getCurrentHp();
        }
    }
}
