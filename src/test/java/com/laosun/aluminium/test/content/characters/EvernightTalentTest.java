package com.laosun.aluminium.test.content.characters;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.Summon;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 1413 Evernight (长夜月)'s talent: "场上敌方目标数量等于4或以上/3/2/1名时，我方忆灵造成的伤害为原伤害的120%/125%/130%/150%" (when the number of enemy targets on the field is 4 or more/3/2/1, the damage our memosprites deal is 120%/125%/130%/150% of the original damage).
 *
 * <p>The whole memosprite chain is exercised here: the damage TYPE (`DamageType.MEMORY`), the scoped boost granted to the MASTER
 * (its table is consulted when its memosprite strikes -- measured), and the `enemy_count` condition.
 *
 * <p>Note: Judged with the same memosprite hit at FOUR and at ONE enemy: the content grants +0.20 and +0.50, so the ratio must be
 * 1.50/1.20 = 1.25 exactly -- round 65 measured this zone to hold no other contributions.
 */
public class EvernightTalentTest {
    private static final int SUMMONER = 1413;
    private static final int LEVEL = 80;
    private static final int VICTIM = 1002011;
    private static final int BYSTANDER = 1002012;

    @Test
    public void fewerEnemiesMeansMoreMemospriteDamage() {
        double four = memospriteDamage(4);
        double one = memospriteDamage(1);
        Assertions.assertTrue(four > 0 && one > 0, "precondition: the 忆灵 (memosprite) landed both hits");
        Assertions.assertEquals(1.50 / 1.20, one / four, 1e-9,
                "120% at four enemies and 150% at one: " + four + " vs " + one);
    }

    /** One hit by the memosprite against a victim that cannot die, on a field of the given size. */
    private static double memospriteDamage(int enemies) {
        Character master = CharacterFactory.create(SUMMONER, LEVEL);
        List<Enemy> foes = new ArrayList<>();
        Enemy victim = null;
        for (int i = 0; i < enemies; i++) {
            Enemy foe = EnemyFactory.create(i == 0 ? VICTIM : BYSTANDER, 90, 1);
            foe.setAttribute(AttributeType.HEALTH, new DoubleValue(900000));
            foe.heal(900000);
            foes.add(foe);
            if (i == 0) {
                victim = foe;
            }
        }
        Random noCrit = new Random(0) {
            @Override
            public double nextDouble() {
                return 0.99;
            }
        };
        Battle battle = new Battle(List.of(master), foes, noCrit);
        battle.startBattle();
        Summon memosprite = battle.summonMemosprite(master);
        Assertions.assertNotNull(memosprite, "precondition: the 忆灵 (memosprite) is out");
        double before = victim.getCurrentHp();
        // Note: COMMON, not SKILL: a memosprite's stated attack is installed in its COMMON slot.
        battle.castImmediate(memosprite.getSkills().get(SkillType.COMMON), memosprite, List.of(victim));
        double dealt = before - victim.getCurrentHp();
        Assertions.assertFalse(victim.isDeath(), "the judged hit must not kill the victim");
        Assertions.assertTrue(dealt > 0 && dealt < 0.4 * before,
                "a real measurement, not the whole bar: " + dealt + " of " + before);
        return dealt;
    }
}
