package com.laosun.aluminium.test.content.characters.jingliu;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1212 镜流's Ultimate: 「对指定敌方单体造成等同于镜流 300% 攻击力的冰属性伤害，并对其相邻目标造成等同于镜流 150% 攻击力的冰属性伤害」.
 *
 * <p><b>Why this is a criterion and not a rule.</b> The file used to register the adjacent half as 「未写（相邻是伤害形状）」 - true
 * of the RULE, and irrelevant: the Ultimate's data row is a {@code Blast}, and the engine's own settlement reads the adjacent
 * column from that row (300% main, 150% neighbours). So the shape is already right, and what was missing was a judge that says so:
 * 150 / 300 = 0.5 on the neighbours.
 */
public class JingliuUltimateSplashTest {
    private static final int JINGLIU = 1212;
    private static final int LEVEL = 80;

    @Test
    public void theUltimateSplashesHalfOntoTheNeighbours() {
        Character jingliu = CharacterFactory.create(JINGLIU, LEVEL);
        Enemy left = BattleFixture.monster();
        Enemy centre = BattleFixture.monster();
        Enemy right = BattleFixture.monster();
        for (Enemy enemy : List.of(left, centre, right)) {
            enemy.setMaxStance(100_000);
            enemy.setStance(100_000);
        }
        Battle battle = new Battle(new ArrayList<>(List.of(jingliu)), new ArrayList<>(List.of(left, centre, right)),
                BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        battle.processRequests();

        double leftBefore = left.getCurrentHp();
        double centreBefore = centre.getCurrentHp();
        double rightBefore = right.getCurrentHp();
        battle.castImmediate(jingliu.getSkills().get(SkillType.ULTRA), jingliu, List.of(centre));
        battle.processRequests();

        double main = centreBefore - centre.getCurrentHp();
        double leftLoss = leftBefore - left.getCurrentHp();
        double rightLoss = rightBefore - right.getCurrentHp();
        System.out.println("[1212] ult main=" + main + " neighbours=" + leftLoss + "/" + rightLoss);
        Assertions.assertTrue(main > 0, "precondition: the Ultimate hit the main target");
        Assertions.assertEquals(0.5, leftLoss / main, 1e-6,
                "「对其相邻目标造成等同于镜流 150% 攻击力的冰属性伤害」 - against the main target's 300%");
        Assertions.assertEquals(0.5, rightLoss / main, 1e-6, "and on the other side too");
    }
}
