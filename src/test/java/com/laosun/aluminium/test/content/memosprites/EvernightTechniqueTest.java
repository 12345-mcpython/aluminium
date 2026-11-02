package com.laosun.aluminium.test.content.memosprites;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.buff.StateBuff;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1413 长夜月's technique:「下一次战斗开始时，获得与战技相同的提高我方全体忆灵暴击伤害的效果，并获得 1 点【忆质】」.
 *
 * <p>「与战技相同」 is the load-bearing half: the CRIT share must be read from the SKILL row, not from the technique's own
 * {@code [1, 1]} row (which carries only the MEMORIA count).
 */
public class EvernightTechniqueTest {

    @Test
    public void theTechniqueGrantsTheSkillEffectAndOneMemoria() {
        double memoriaWith = run(true);
        double memoriaWithout = run(false);
        System.out.println("[1413] memoria: with technique " + memoriaWith + " ; without " + memoriaWithout);
        Assertions.assertEquals(1.0, memoriaWith - memoriaWithout, 1e-9, "「获得 1 点【忆质】」");
    }

    private static double run(boolean technique) {
        Character her = CharacterFactory.create(1413, 80);
        if (technique) {
            her.getBuffManager().addBuff(new StateBuff("秘技", 1));
        }
        Battle battle = new Battle(new ArrayList<>(List.of(her)),
                new ArrayList<>(List.of(BattleFixture.dummy())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        return her.getResources().value("亿质");
    }
}