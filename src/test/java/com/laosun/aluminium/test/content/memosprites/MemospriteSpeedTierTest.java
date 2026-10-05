package com.laosun.aluminium.test.content.memosprites;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import com.laosun.aluminium.utils.RelicFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Relic 323, 2 pieces: "装备者的忆灵在场时，我方全体速度提高#2%" -- the condition is the wearer's own summon count.
 *
 * <p>1402's memosprite is summoned with `battle.summonMemosprite`, the same call the existing memosprite test uses.
 */
public class MemospriteSpeedTierTest {
    private static final int WEARER = 1402;
    private static final int ALLY = 1001;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final int RELIC_LEVEL = 15;
    private static final double SPEED_SHARE = 0.08;

    @Test
    public void theSecondPieceRaisesEveryAllysSpeedWhileTheMemospriteIsOut() {
        double withoutSprite = speedGain(false);
        double withSprite = speedGain(true);
        System.out.println("[323] withoutSprite=" + withoutSprite + " withSprite=" + withSprite);
        Assertions.assertEquals(0.0, withoutSprite, 1e-9, "no memosprite, no clause");
        Assertions.assertEquals(SPEED_SHARE, withSprite, 1e-6, "share of the post-start base");
    }

    private static double speedGain(boolean summon) {
        Character unit = CharacterFactory.create(WEARER, LEVEL, true, null, RelicFactory.suit(323, 2, RELIC_LEVEL));
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(unit, ally), List.of(enemy), new Random(0));
        battle.startBattle();
        double before = ally.getAttribute(AttributeType.SPEED).get();
        if (summon) {
            battle.summonMemosprite(unit);
            // `SUMMONED` is fired when the battle settles its requests (Battle.processRequests), not inside the summon call.
            battle.processRequests();
        }
        double gain = ally.getAttribute(AttributeType.SPEED).get() - before;
        return gain / ally.getAttribute(AttributeType.SPEED).baseValue();
    }
}
