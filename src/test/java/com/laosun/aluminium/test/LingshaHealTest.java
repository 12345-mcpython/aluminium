package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1222 灵砂's Skill: 「同时为我方全体回复等同于灵砂14.00%攻击力+420的生命值」 -- the first clause of a character who had no file at all.
 *
 * <p>Judged as the AMOUNT healed on a wounded teammate, against 14% of her ATK plus 420 (her own Lv10 row), so the assertion checks
 * the scale, the percentage and the flat part together.
 */
public class LingshaHealTest {
    private static final int LINGSHA = 1222;
    private static final int ALLY = 1001;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    @Test
    public void herSkillHealsThePartyForHerOwnNumbers() {
        Character lingsha = CharacterFactory.create(LINGSHA, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(lingsha, ally), List.of(enemy), new Random(0));
        battle.startBattle();
        // Wound the ally first: a heal on a full bar measures nothing.
        ally.takeDamage(ally.getCurrentHp() * 0.5);
        double before = ally.getCurrentHp();
        battle.castImmediate(lingsha.getSkills().get(SkillType.SKILL), lingsha, List.of(enemy));
        double healed = ally.getCurrentHp() - before;
        double expected = 0.14 * lingsha.getAttribute(AttributeType.ATTACK).get() + 420;
        Assertions.assertTrue(healed > 0, "precondition: the skill healed the ally: " + healed);
        Assertions.assertEquals(expected, healed, Math.max(2.0, 0.02 * expected),
                "14% of her ATK (" + lingsha.getAttribute(AttributeType.ATTACK).get() + ") plus 420: " + healed);
    }
}
