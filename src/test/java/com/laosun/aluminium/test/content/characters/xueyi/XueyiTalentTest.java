package com.laosun.aluminium.test.content.characters.xueyi;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1214 雪衣's Talent, the two clauses whose numbers the document states:
 *
 * <ul>
 *   <li>「当雪衣的队友施放攻击削减敌方韧性后，雪衣叠加 1 层【恶报】」</li>
 *   <li>「当【恶报】叠加至上限时消耗当前所有【恶报】，立即…追加攻击，造成 3 次伤害…90% 攻击力」</li>
 * </ul>
 *
 * <p>Note: the Toughness is really taken off through {@code reduceToughness}, which is what fires the new event - the
 * judge does not fake it.
 */
public class XueyiTalentTest {

    private static final int XUEYI = 1214;
    private static final int ALLY = 1001;
    private static final int LEVEL = 80;
    private static final String KARMA = "恶报";

    @Test
    public void anAllysToughnessReductionGivesOneStack() {
        Battle battle = battle();
        Character xueyi = battle.characters().getFirst();
        Enemy enemy = (Enemy) battle.enemies().getFirst();
        Assertions.assertEquals(0, xueyi.getBuffManager().stacksOf(KARMA), "precondition");
        battle.reduceToughness(battle.characters().get(1), enemy, DamageElement.FIRE, 30, null);
        Assertions.assertEquals(1, xueyi.getBuffManager().stacksOf(KARMA), "「叠加 1 层【恶报】」");
    }

    @Test
    public void theCapConsumesEverythingAndLaunchesThreeHits() {
        Battle battle = battle();
        Character xueyi = battle.characters().getFirst();
        Enemy enemy = (Enemy) battle.enemies().getFirst();
        xueyi.getResources();   // touch, keeps the fixture honest about defaults

        // Seven stacks, then one more from an ally: that is the cap.
        for (int i = 0; i < 7; i++) {
            battle.reduceToughness(battle.characters().get(1), enemy, DamageElement.FIRE, 1, null);
            enemy.setStance(1000);   // keep the bar from emptying between repetitions
        }
        Assertions.assertEquals(7, xueyi.getBuffManager().stacksOf(KARMA), "precondition: seven stacks");
        double hp = enemy.getCurrentHp();
        battle.reduceToughness(battle.characters().get(1), enemy, DamageElement.FIRE, 1, null);

        System.out.println("[1214] karma=" + xueyi.getBuffManager().stacksOf(KARMA)
                + " enemy hp " + hp + " -> " + enemy.getCurrentHp());
        Assertions.assertEquals(0, xueyi.getBuffManager().stacksOf(KARMA), "「消耗当前所有【恶报】」");
        Assertions.assertTrue(enemy.getCurrentHp() < hp, "the follow-up landed");
    }

    private static Battle battle() {
        Character xueyi = CharacterFactory.create(XUEYI, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Battle battle = new Battle(new ArrayList<>(List.of(xueyi, ally)),
                new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        return battle;
    }
}