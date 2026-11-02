package com.laosun.aluminium.test.content.characters.welt;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1004 瓦尔特's Eidolon 1 (「强化前」half):「施放终结技后…在随后 2 次施放普攻或战技时额外对敌方目标造成 1 次附加伤害」.
 *
 * <p>The judge counts the extra instances by damage: two empowered attacks add damage that a plain attack does not, and the
 * third one is back to normal - which is what 「随后 2 次」 means.
 */
public class WeltEidolonOneTest {

    @Test
    public void theUltimateEmpowersExactlyTwoAttacks() {
        double[] d = damages();
        System.out.println("[1004] E1 after ult: #1=" + d[0] + " #2=" + d[1] + " #3=" + d[2] + " #4=" + d[3]);
        Assertions.assertTrue(d[0] > d[2], "the first attack after the Ultimate carries the additional damage");
        Assertions.assertTrue(d[1] > d[2], "the second one does too");
        Assertions.assertEquals(d[2], d[3], 1e-9, "「随后 2 次」 - attacks three and four are the same ordinary attack");
    }

    /** Raw damage of four consecutive basic attacks in ONE battle, so the crit stream is the same for all of them. */
    private static double[] damages() {
        Character him = CharacterFactory.create(1004, 80, true, null, null, 1);
        Battle battle = new Battle(new ArrayList<>(List.of(him)),
                new ArrayList<>(List.of(BattleFixture.dummy())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        var enemy = battle.enemyUnits().getFirst();
        battle.castImmediate(him.getSkills().get(SkillType.ULTRA), him, List.of(enemy));
        battle.processRequests();
        double[] out = new double[4];
        for (int i = 0; i < 4; i++) {
            double before = enemy.getCurrentHp();
            battle.castImmediate(him.getSkills().get(SkillType.COMMON), him, List.of(enemy));
            battle.processRequests();
            out[i] = before - enemy.getCurrentHp();
        }
        return out;
    }
}
