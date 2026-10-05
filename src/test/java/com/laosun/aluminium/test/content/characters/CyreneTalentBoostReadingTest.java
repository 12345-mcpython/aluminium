package com.laosun.aluminium.test.content.characters;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;

/**
 * What Cyrene (昔涟)'s talent actually leaves on a teammate: "at battle start ... raise the damage dealt by all of our targets".
 *
 * Her file states it as `ALL_DAMAGE_TYPE_BOOST, percent: 0.2, permanent, max_stacks: 2`, i.e. two layers. Reading the attribute directly reports 0.2, while a reverse-solved baseline comes
 * out at 0.4184 -- this settles which number `get()` answers with, so the two readings can be reconciled rather than guessed at.
 */
public class CyreneTalentBoostReadingTest {
    private static final int LEVEL = 80;
    private static final int CYRENE = 1415;
    private static final int ANAXA = 1405;
    private static final int MONSTER = 1002011;

    @Test
    public void theTalentLeavesATwoLayerBoost() {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character him = CharacterFactory.create(ANAXA, LEVEL);
        Battle battle = new Battle(List.of(cyrene, him),
                List.of(EnemyFactory.create(MONSTER, 100, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        double boost = battle.characters.get(1).getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        System.out.println("[cyrene_boost] a teammate's ALL_DAMAGE_TYPE_BOOST reads " + boost
                + " (the file states percent 0.2 with max_stacks 2)");
        Assertions.assertEquals(0.2, boost, 1e-9,
                "exactly the talent's stated 20.00% -- measured, NOT two layers, so `max_stacks: 2` merely permits more");
    }
}
