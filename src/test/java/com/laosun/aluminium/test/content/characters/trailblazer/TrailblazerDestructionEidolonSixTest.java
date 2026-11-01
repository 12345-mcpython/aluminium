package com.laosun.aluminium.test.content.characters.trailblazer;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * The Destruction Trailblazer's Eidolon 6: 「开拓者消灭敌方目标时，也会触发天赋」 - the Talent's ATK bonus (20%, up to 2 layers)
 * also fires on a kill, not only on a weakness break.
 */
public class TrailblazerDestructionEidolonSixTest {

    private static final int LEVEL = 80;

    @Test
    public void aKillAlsoPaysTheTalentsAttackBonus() {
        for (int cid : new int[]{8001, 8002}) {
            Character unit = CharacterFactory.create(cid, LEVEL, true, null, null, 6);
            Enemy enemy = BattleFixture.monster();
            Battle battle = new Battle(new ArrayList<>(List.of(unit, CharacterFactory.create(1001, LEVEL))),
                    new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
            battle.startBattle();
            double before = unit.getAttribute(AttributeType.ATTACK).get();
            battle.fire(TriggerPayload.Simple.of(TriggerEvent.KILL, unit, enemy, 0, 0));
            double after = unit.getAttribute(AttributeType.ATTACK).get();
            double base = unit.getAttribute(AttributeType.ATTACK).baseValue();
            System.out.println("[" + cid + "] atk " + before + " -> " + after + " (base " + base + ")");
            // The +20% is a share of BASE attack, so the panel ratio is less than 1.2 whenever relics already boost ATK.
            Assertions.assertEquals(0.2 * base, after - before, 1e-9, cid + ": 「也会触发天赋」 = +20% 攻击力");
        }
    }
}