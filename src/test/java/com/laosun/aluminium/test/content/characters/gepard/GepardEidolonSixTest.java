package com.laosun.aluminium.test.content.characters.gepard;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1104 杰帕德's Eidolon 6: 「触发天赋时杰帕德立即行动，且当前生命值的回复量额外增加自身生命上限的 50%」.
 *
 * <p>With the Talent (a 50% heal on the same moment) the two together put him back at full HP.
 */
public class GepardEidolonSixTest {

    private static final int GEPARD = 1104;
    private static final int LEVEL = 80;

    @Test
    public void theTalentTriggerHealsAnotherHalf() {
        Character gepard = CharacterFactory.create(GEPARD, LEVEL, true, null, null, 6);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(gepard, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        double max = gepard.getMaxHp();
        gepard.consumeHp(max * 0.9);
        double before = gepard.getCurrentHp();

        battle.fire(TriggerPayload.Simple.of(TriggerEvent.LETHAL_DAMAGE, enemy, gepard, 0, 0));
        double healed = gepard.getCurrentHp() - before;
        System.out.println("[1104] max=" + max + " healed=" + healed + " -> hp=" + gepard.getCurrentHp());
        Assertions.assertTrue(healed >= 0.5 * max, "「额外增加自身生命上限的 50%」");
    }
}