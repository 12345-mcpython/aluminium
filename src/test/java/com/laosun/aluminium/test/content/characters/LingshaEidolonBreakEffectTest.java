package com.laosun.aluminium.test.content.characters;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1222 Lingsha (灵砂)'s Eidolon (星魂) 2: "施放终结技后,使我方全体击破特攻提高40%,持续3回合" (after casting the ultimate, all of our side's break effect is raised by 40% for 3 turns).
 * <p>Judged on a TEAMMATE ("我方全体", all of our side) as an ABSOLUTE +0.40, because BREAKING_EFFECT is a ratio attribute and a share lands as points on those (the
 * flat/ratio distinction).
 */
public class LingshaEidolonBreakEffectTest {
    private static final int LINGSHA = 1222;
    private static final int ALLY = 1001;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    @Test
    public void theUltimateRaisesThePartysBreakEffect() {
        Character lingsha = CharacterFactory.create(LINGSHA, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(lingsha, ally), List.of(enemy), new Random(0));
        battle.startBattle();
        double before = ally.getAttribute(AttributeType.BREAKING_EFFECT).get();
        battle.castImmediate(lingsha.getSkills().get(SkillType.ULTRA), lingsha, List.of(enemy));
        double after = ally.getAttribute(AttributeType.BREAKING_EFFECT).get();
        Assertions.assertEquals(0.40, after - before, 1e-6,
                "forty points of break effect on the teammate: " + before + " -> " + after);
    }
}
