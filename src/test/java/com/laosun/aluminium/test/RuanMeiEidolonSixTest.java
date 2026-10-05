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
 * 1303 阮•梅's 星魂 6: 「终结技展开的结界持续时间延长1回合」 -- judged over WHOLE turns.
 *
 * <p>The countdown is split into an early pass (`beforeMove`) and a late one (`afterMove`), and a stat modifier is a LATE
 * buff, so a whole turn is both calls. `turns: 2` expires after two of them; with the extension the zone must still stand.
 */
public class RuanMeiEidolonSixTest {
    private static final int RUAN_MEI = 1303;
    private static final int ALLY = 1210;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final double EPS = 1e-9;

    @Test
    public void theZoneExpiresOnHerSecondWholeTurn() {
        Assertions.assertEquals(0.25, afterTurns(0, 1), EPS, "still up after one whole turn of hers");
        Assertions.assertEquals(0.0, afterTurns(0, 2), EPS,
                "自身每回合开始时结界持续回合数减1 -- so two end it");
    }

    @Test
    public void theSixthEidolonKeepsItForOneMoreTurn() {
        Assertions.assertEquals(0.25, afterTurns(6, 2), EPS,
                "结界持续时间延长1回合 -- two turns no longer end it");
        Assertions.assertEquals(0.0, afterTurns(6, 3), EPS, "but three do");
    }

    private static double afterTurns(int rank, int turns) {
        Character ruanMei = CharacterFactory.create(RUAN_MEI, LEVEL, true, null, null, rank);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(ruanMei, ally), List.of(enemy), new Random(0));
        battle.startBattle();
        battle.castImmediate(ruanMei.getSkills().get(SkillType.ULTRA), ruanMei, List.of(enemy));
        Assertions.assertEquals(0.25, penetrationOf(ally), EPS, "precondition: the zone is up after the cast");
        for (int i = 0; i < turns; i++) {
            battle.currentMove = new Signal(ruanMei);
            battle.beforeMove();
            battle.afterMove();
        }
        return penetrationOf(ally);
    }

    private static double penetrationOf(Character unit) {
        return unit.getAttribute(AttributeType.DAMAGE_PENETRATION).get()
                - unit.getAttribute(AttributeType.DAMAGE_PENETRATION).baseValue();
    }
}
