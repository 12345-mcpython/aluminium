package com.laosun.aluminium.test.content.characters.jiaoqiu;

import com.laosun.aluminium.Battle;
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
 * 1218 椒丘's Ultimate: 「敌方目标行动时有 60% 的基础概率被施加 1 层【烬煨】，结界存在期间最多触发 6 次，且每个敌方目标
 * 每回合只能触发 1 次…每次施放终结技时重置」.
 */
public class JiaoqiuDomainTriggerTest {

    private static final int JIAOQIU = 1218;
    private static final int LEVEL = 80;
    private static final String ASHEN = "烬煨";

    @Test
    public void anEnemyTurnRollsOneLayerAtMostOncePerTurn() {
        Fixture f = new Fixture();
        int before = f.enemy.getBuffManager().stacksOf(ASHEN);

        f.enemyTurn();
        Assertions.assertEquals(before + 1, f.enemy.getBuffManager().stacksOf(ASHEN),
                "「60% 的基础概率被施加 1 层」 - the roll is forced to succeed by the fixture");

        f.enemyTurn();
        Assertions.assertEquals(before + 1, f.enemy.getBuffManager().stacksOf(ASHEN),
                "「每个敌方目标每回合只能触发 1 次」 - a second turn of the same enemy pays once");
    }

    @Test
    public void theDomainStopsAtSixTriggers() {
        Fixture f = new Fixture();
        for (int i = 0; i < 10; i++) {
            f.newEnemyTurn();
        }
        System.out.println("[1218] triggers=" + f.count());
        Assertions.assertEquals(6, f.count(), "「结界存在期间该效果最多触发 6 次」");
    }

    @Test
    public void recastingTheUltimateResetsTheCount() {
        Fixture f = new Fixture();
        for (int i = 0; i < 4; i++) {
            f.newEnemyTurn();
        }
        int afterFour = f.count();
        Assertions.assertEquals(4, afterFour, "precondition: four triggers so far");

        f.ultimate();
        Assertions.assertEquals(0, f.count(), "「每次施放终结技时重置触发次数」");
    }

    // ==================================================================

    private static final class Fixture {
        private final Character jiaoqiu;
        private final Enemy enemy = BattleFixture.monster();
        private final Battle battle;

        private Fixture() {
            jiaoqiu = CharacterFactory.create(JIAOQIU, LEVEL);
            battle = new Battle(new ArrayList<>(List.of(jiaoqiu, CharacterFactory.create(1001, LEVEL))),
                    new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
            battle.startBattle();
            ultimate();
        }

        private void ultimate() {
            battle.castImmediate(jiaoqiu.getSkills().get(SkillType.ULTRA), jiaoqiu, List.of(enemy));
        }

        private void enemyTurn() {
            battle.fire(TriggerPayload.Simple.of(TriggerEvent.TURN_START, enemy, enemy, 0, 0));
        }

        /**
         * A fresh enemy takes its turn, so 「每敌每回合 1 次」 does not block the next trigger.
         */
        private void newEnemyTurn() {
            Enemy fresh = BattleFixture.monster();
            battle.addEnemy(fresh);
            battle.fire(TriggerPayload.Simple.of(TriggerEvent.TURN_START, fresh, fresh, 0, 0));
        }

        private int count() {
            return jiaoqiu.getBuffManager().stacksOf("结界计数");
        }
    }
}
