package com.laosun.aluminium.test.content.characters.trailblazer;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * The Elation Trailblazer's talent rider: 「开拓者持有【好活当赏】时，战技对敌方全体额外造成 30% 雷属性欢愉伤害，此伤害使用我方
 * 最高的【好活当赏】数值计算」.
 *
 * <p>Note: the Skill ALSO grants 20 【好活当赏】, and the grant lands before the rider reads the maximum - so the base is
 * 0.3 x (gift + 20). The judge therefore pins the RATIO: 0 / 20 / 40 extra gift must give 1 : 2 : 3.
 */
public class TrailblazerElationDamageTest {

    private static final int LEVEL = 80;

    @Test
    public void theRiderScalesWithTheHighestGiftOnTheTeam() {
        for (int cid : new int[]{8009, 8010}) {
            double none = rider(cid, 0);
            double twenty = rider(cid, 20);
            double forty = rider(cid, 40);
            System.out.println("[" + cid + "] " + none + " / " + twenty + " / " + forty);
            Assertions.assertTrue(none > 0, "the Skill's own 20 points are already banked when the rider reads the max");
            Assertions.assertEquals(2.0, twenty / none, 1e-6, "20 more gift = twice the base");
            Assertions.assertEquals(3.0, forty / none, 1e-6, "40 more gift = three times the base");
        }
    }

    private static double rider(int cid, int bonusGift) {
        Character trailblazer = CharacterFactory.create(cid, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(trailblazer, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        if (bonusGift > 0) {
            trailblazer.getResources().gain("好活当赏", bonusGift);
        }
        double[] elation = new double[1];
        battle.addDamageObserver((damage, applied, hpLoss, shield, died) -> {
            if (damage.getType() == DamageType.ELATION) {
                elation[0] += applied;
            }
        });
        battle.castImmediate(trailblazer.getSkills().get(SkillType.SKILL), trailblazer, List.of(enemy));
        return elation[0];
    }
}