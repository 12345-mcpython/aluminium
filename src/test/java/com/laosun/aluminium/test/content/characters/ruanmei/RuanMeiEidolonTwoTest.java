package com.laosun.aluminium.test.content.characters.ruanmei;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.buff.BuffManager;
import com.laosun.aluminium.buff.StateBuff;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1303 阮•梅's Eidolon 2: 「我方全体对处于弱点击破状态的敌方目标造成伤害时，攻击力提高 40%」.
 *
 * <p>The clause is placed on CAST_SETUP, which fires BEFORE the base is computed - on DEALING_DAMAGE it is too late
 * (measured: an ATTACK modifier applied there left the settled damage at ratio 1.0).
 */
public class RuanMeiEidolonTwoTest {

    private static final int RUANMEI = 1303;
    private static final int ALLY = 1001;
    private static final int LEVEL = 80;

    @Test
    public void anAllyHittingABrokenEnemyGetsTheAttackBoost() {
        double plain = hit(false);
        double broken = hit(true);
        System.out.println("[1303] plain=" + plain + " broken=" + broken + " ratio=" + (broken / plain));
        Assertions.assertEquals(1.4, broken / plain, 0.05, "「攻击力提高 40%」");
    }

    private static double hit(boolean broken) {
        Character ruanmei = CharacterFactory.create(RUANMEI, LEVEL, true, null, null, 2);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(ruanmei, ally)),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        if (broken) {
            enemy.getBuffManager().addBuff(new StateBuff(BuffManager.BROKEN_STATE, 3, false));
        }
        double before = enemy.getCurrentHp();
        battle.castImmediate(ally.getSkills().get(SkillType.COMMON), ally, List.of(enemy));
        return before - enemy.getCurrentHp();
    }
}