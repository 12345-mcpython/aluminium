package com.laosun.aluminium.test.content.characters.misha;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1312 米沙's Eidolons 2 / 4 / 6, the three its file now states.
 *
 * <p>Each is measured against the rank BELOW it, so an unrelated Eidolon cannot account for the difference.
 */
public class MishaEidolonTest {

    private static final int MISHA = 1312;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;

    /**
     * 「每段攻击前有 24% 基础概率使目标防御力降低 16%」: read off the enemy's DEFENCE, on an enemy that HAS defence.
     * ⚠ Not {@code BattleFixture.dummy()} - its DEFENCE is 0, so a 16% share of it is 0 and the clause looks dead
     * (measured: the attribute read 0.0 at both ranks and the damage did not move).
     */
    @Test
    public void eidolonTwoLowersDefencePerSegment() {
        double atOne = enemyDefence(1);
        double atTwo = enemyDefence(2);
        System.out.println("[1312] enemy DEF at E1 " + atOne + " ; E2 " + atTwo);
        Assertions.assertTrue(atTwo < atOne, "「使目标防御力降低 16%」");
    }

    private static double enemyDefence(int eidolon) {
        Character misha = CharacterFactory.create(MISHA, LEVEL, true, null, null, eidolon);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(misha)), new ArrayList<>(List.of(enemy)),
                BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        battle.castImmediate(misha.getSkills().get(SkillType.ULTRA), misha, List.of(enemy));
        battle.processRequests();
        return enemy.getAttribute(AttributeType.DEFENCE).get();
    }

    @Test
    public void eidolonFourRaisesEachSegment() {
        double atThree = ultDamage(3);
        double atFour = ultDamage(4);
        System.out.println("[1312] ultimate at E3 " + atThree + " ; E4 " + atFour);
        Assertions.assertTrue(atFour > atThree, "「终结技每段攻击的伤害倍率提高 6%」");
    }

    @Test
    public void eidolonSixRaisesHerOwnDamage() {
        Assertions.assertEquals(0.0, boost(5), 1e-9, "control: nothing before Eidolon 6");
        Assertions.assertEquals(0.3, boost(6), 1e-9, "「使自身造成的伤害提高 30%」");
    }

    private static double boost(int eidolon) {
        Character misha = CharacterFactory.create(MISHA, LEVEL, true, null, null, eidolon);
        Battle battle = new Battle(new ArrayList<>(List.of(misha)),
                new ArrayList<>(List.of(BattleFixture.dummy())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        battle.castImmediate(misha.getSkills().get(SkillType.ULTRA), misha, List.of(battle.enemyUnits().getFirst()));
        battle.processRequests();
        return misha.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
    }

    private static double ultDamage(int eidolon) {
        Character misha = CharacterFactory.create(MISHA, LEVEL, true, null, null, eidolon);
        Enemy enemy = BattleFixture.dummy();
        Battle battle = new Battle(new ArrayList<>(List.of(misha)), new ArrayList<>(List.of(enemy)),
                BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        double before = enemy.getCurrentHp();
        battle.castImmediate(misha.getSkills().get(SkillType.ULTRA), misha, List.of(enemy));
        battle.processRequests();
        return before - enemy.getCurrentHp();
    }
}