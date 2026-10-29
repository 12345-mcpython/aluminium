package com.laosun.aluminium.test.content.characters.rappa;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.entity.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 131Rappa, from her own file: the Charge that Weakness Break feeds, and the document's cap of 10.
 *
 * <p>The cap is tested by EXCEEDING it (round 192's lesson): twelve breaks must still read ten.
 */
public class RappaTest {
    private static final int RAPPA = 1317;
    private static final int LEVEL = 80;

    /**
     * Note: One Charge per Weakness Break, stopped at the document's ten.
     */
    @Test
    public void everyBreakAddsOneChargeUpToTen() {
        Character rappa = CharacterFactory.create(RAPPA, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(rappa), List.of(enemy), fixed());
        battle.startBattle();

        Assertions.assertEquals(0, chargeOf(rappa), "the document states no initial value, so it starts at 0");
        battle.fire(com.laosun.aluminium.models.trigger.TriggerPayload.Simple.of(TriggerEvent.BREAK, rappa, enemy, 0, 0));
        Assertions.assertEquals(1, chargeOf(rappa),
                "「每当敌方目标的弱点被击破时，乱破获得1点充能」 (every time an enemy target's Weakness is Broken, Rappa gains 1 point of Charge)");

        for (int i = 0; i < 11; i++) {
            battle.fire(com.laosun.aluminium.models.trigger.TriggerPayload.Simple.of(TriggerEvent.BREAK, rappa, enemy, 0, 0));
        }
        Assertions.assertEquals(10, chargeOf(rappa),
                "「最多拥有10点充能」 (holds at most 10 points of Charge) -- twelve breaks must still read ten");
    }

    /**
     * The declared resource's value, read through the combatant's own manager.
     */
    private static int chargeOf(Character rappa) {
        return rappa.getResources().get("充能").getValue();
    }

    private static Random fixed() {
        return BattleFixture.alwaysRoll(0.0);
    }
}
