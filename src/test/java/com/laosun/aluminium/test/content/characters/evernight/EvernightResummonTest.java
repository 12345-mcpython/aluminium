package com.laosun.aluminium.test.content.characters.evernight;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.DoubleValue;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.entity.Summon;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 1413 长夜月's 「长夜」 can leave the field and be summoned again.
 *
 * <p><b>Why this case exists.</b> Her slot 7 states 「施放后…并使「长夜」消失」 - the memosprite leaves without dying - and a
 * recorded fight (2026-10-30) shows it acting again later, so the re-summon path is not hypothetical: it is what the capture
 * does before its cast 69. Driving that cast threw
 * <pre>Effect targets "summon" but Evernight has no summon on the field</pre>
 * from a {@code SUMMONED} rule, which means the rule that listens for the arrival cannot see the arrival.
 */
public class EvernightResummonTest {

    private static final int EVERNIGHT = 1413;

    private static Enemy dummy() {
        Enemy enemy = Enemy.fromAttributes("target", 900000, 200, 100, 100);
        enemy.setAttribute(AttributeType.HEALTH, new DoubleValue(900000));
        enemy.heal(900000);
        return enemy;
    }

    private static Random noCrit() {
        return new Random(0) {
            @Override
            public double nextDouble() {
                return 0.99;
            }
        };
    }

    /**
     * Summon, dismiss, summon again: the second arrival must reach the field like the first one.
     */
    @Test
    public void aDismissedMemospriteCanBeSummonedAgain() {
        Character evernight = CharacterFactory.create(EVERNIGHT, 80);
        Battle battle = new Battle(List.of(evernight), new ArrayList<>(List.of(dummy())), noCrit());
        battle.startBattle();

        Summon first = battle.summonMemosprite(evernight);
        Assertions.assertNotNull(first, "the first summon is on the field");

        first.perish();
        Assertions.assertNull(battle.memospriteOf(evernight), "a perished memosprite is not on the field any more");

        Summon second = Assertions.assertDoesNotThrow(() -> battle.summonMemosprite(evernight),
                "the re-summon must not be refused");
        Assertions.assertNotNull(second, "and it arrives");
        Assertions.assertSame(second, battle.memospriteOf(evernight), "the field now holds the new one");
    }
}
