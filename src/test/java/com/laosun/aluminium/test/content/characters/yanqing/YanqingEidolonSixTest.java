package com.laosun.aluminium.test.content.characters.yanqing;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.buff.AbstractBuff;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1209 彦卿's Eidolon 6, second branch: 「消灭敌方目标时…终结技的增益效果…持续时间全部延长 1 回合」.
 *
 * <p>Note: this only works because a modifier can now be found BY NAME - before, the rule fired and lengthened nothing.
 */
public class YanqingEidolonSixTest {

    private static final int YANQING = 1209;
    private static final int LEVEL = 80;

    @Test
    public void aKillExtendsTheUltimatesBuffs() {
        Character yanqing = CharacterFactory.create(YANQING, LEVEL, true, null, null, 6);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(yanqing, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        battle.castImmediate(yanqing.getSkills().get(SkillType.ULTRA), yanqing, List.of(enemy));

        int before = namedTurns(yanqing);
        Assertions.assertTrue(before > 0, "precondition: the ultimate's named buff is on him");
        battle.fire(TriggerPayload.Simple.of(TriggerEvent.KILL, yanqing, enemy, 0, 0));
        int after = namedTurns(yanqing);
        System.out.println("[1209] 终结技增益 turns " + before + " -> " + after);
        Assertions.assertEquals(before + 1, after, "「持续时间全部延长 1 回合」");
    }

    private static int namedTurns(Character unit) {
        int total = 0;
        for (AbstractBuff buff : unit.getBuffManager().activeBuffs()) {
            if ("终结技增益".equals(buff.getBuffName())) {
                total += buff.remainingTurns();
            }
        }
        return total;
    }
}