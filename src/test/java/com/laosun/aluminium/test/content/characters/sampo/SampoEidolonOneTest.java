package com.laosun.aluminium.test.content.characters.sampo;

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
 * 1108 桑博's Eidolon 1: 「施放战技时，对敌方随机单体额外造成 1 次伤害」.
 *
 * <p>The skill already deals FOUR extra instances (the engine reads that count from the sentence), so the Eidolon is one more
 * instance on the same slot - measured as the number of settled instances, not as a ratio, because the main hit is part of the
 * same count.
 */
public class SampoEidolonOneTest {

    private static final int LEVEL = 80;
    private static final int SAMPO = 1108;

    @Test
    public void theEidolonAddsOneInstanceToTheSkill() {
        double e0Hits = instances(0);
        double e1Hits = instances(1);
        System.out.println("[1108] e0=" + e0Hits + " e1=" + e1Hits);
        Assertions.assertEquals(5.0, e0Hits, 1e-9,
                "the Skill itself: one hit on the target + 「额外造成 4 次伤害」");
        Assertions.assertEquals(e0Hits + 1, e1Hits, 1e-9,
                "「额外造成 1 次」 is one more instance on the same slot, not a new attack");
    }

    /**
     * @return how many separate damage instances the skill settles
     */
    private static double instances(int eidolon) {
        Character sampo = CharacterFactory.create(SAMPO, LEVEL, true, null, null, eidolon);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(sampo, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.99));
        battle.startBattle();
        double[] count = new double[1];
        battle.addDamageObserver((damage, applied, hpLoss, shield, died) -> {
            if (damage.getDefender() == enemy) {
                count[0] += 1;
            }
        });
        battle.castImmediate(sampo.getSkills().get(SkillType.SKILL), sampo, List.of(enemy));
        return count[0];
    }
}
