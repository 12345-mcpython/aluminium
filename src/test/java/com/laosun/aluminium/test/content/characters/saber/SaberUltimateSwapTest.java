package com.laosun.aluminium.test.content.characters.saber;

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
 * 1014 Saber's Ultimate swap: 「施放终结技后下次普攻会被替换为【解放的金色王权】且仅能施放【解放的金色王权】」, and the pairing
 * clause that restores the plain basic after it is used.
 */
public class SaberUltimateSwapTest {

    private static final int SABER = 1014;
    private static final int LEVEL = 80;

    @Test
    public void theUltimateSwapsTheBasicAndTheEnhancedOneRestoresIt() {
        Character saber = CharacterFactory.create(SABER, LEVEL);
        Battle battle = new Battle(new ArrayList<>(List.of(saber)),
                new ArrayList<>(List.of(BattleFixture.dummy())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        var enemy = battle.enemyUnits().getFirst();
        // The slot's own row is the identity: plain basic Lv10 #1 = 1.4, the enhanced one = 2.1 (data slot 8).
        double plainRow = rowOf(saber);

        battle.castImmediate(saber.getSkills().get(SkillType.ULTRA), saber, List.of(enemy));
        battle.processRequests();
        double afterUlt = rowOf(saber);
        System.out.println("[1014] basic #1: plain " + plainRow + " -> after ultimate " + afterUlt);
        Assertions.assertNotEquals(plainRow, afterUlt, "「下次普攻会被替换为【解放的金色王权】」");

        battle.castImmediate(saber.getSkills().get(SkillType.COMMON), saber, List.of(enemy));
        battle.processRequests();
        Assertions.assertEquals(plainRow, rowOf(saber), "after it is used the plain basic comes back");
    }

    /** The current basic-attack slot's Lv10 first parameter - the row that distinguishes the plain skill from the swap. */
    private static double rowOf(Character saber) {
        return saber.getSkills().get(SkillType.COMMON).getData().getSkills().get(9).get(0);
    }
}
