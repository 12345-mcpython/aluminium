package com.laosun.aluminium.test.content.characters.clara;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.SkillCategory;
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
 * 1107 克拉拉's Ultimate, second clause: the enhanced counter fires when an ENEMY finishes an attack, twice per Ultimate.
 */
public class ClaraEnhancedCounterTest {

    private static final int CLARA = 1107;
    private static final int LEVEL = 80;
    private static final String CHARGES = "强化反击";

    @Test
    public void theUltimateArmsTwoCounters() {
        Character clara = CharacterFactory.create(CLARA, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(clara, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        battle.castImmediate(clara.getSkills().get(SkillType.ULTRA), clara, List.of(enemy));
        Assertions.assertEquals(2, clara.getBuffManager().stacksOf(CHARGES), "「强化效果可生效 2 次」");

        double hp = enemy.getCurrentHp();
        enemyAttack(battle, clara, enemy);
        Assertions.assertTrue(enemy.getCurrentHp() < hp, "an ENEMY attack triggers Svarog's counter");
        Assertions.assertEquals(1, clara.getBuffManager().stacksOf(CHARGES), "one charge spent");

        enemyAttack(battle, clara, enemy);
        Assertions.assertEquals(0, clara.getBuffManager().stacksOf(CHARGES), "two charges spent");

        double after = enemy.getCurrentHp();
        enemyAttack(battle, clara, enemy);
        Assertions.assertEquals(after, enemy.getCurrentHp(), 1e-9, "no charges left: no counter");
    }

    private static void enemyAttack(Battle battle, Character clara, Enemy enemy) {
        battle.fire(new TriggerPayload.Cast(TriggerEvent.ATTACK_FINISHED, enemy, clara, 1, 0.0,
                SkillCategory.NORMAL, 0, 0, List.of(clara)));
    }
}