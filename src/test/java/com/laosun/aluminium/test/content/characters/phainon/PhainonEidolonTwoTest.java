package com.laosun.aluminium.test.content.characters.phainon;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Character 1408 (Phainon 白厄): Eidolon 2's first half - 「卡厄斯兰那的物理属性<b>抗性穿透</b>提高 20%」.
 *
 * <p>⚠⚠ <b>This clause was in the file already, attributed to a TRACE and therefore ungated</b> - so a 0-Eidolon Phainon
 * carried +20% physical RES PEN. Two independent sources say it is Eidolon 2's: {@code eidolons.json} 140806's sibling
 * rank 140802 has {@code param = [4, 0.2]} with that exact text ({@code #2 = 0.2}), and his kit has only <b>three</b>
 * traces ({@code 1408101} 火种 / {@code 1408102} 受伤增伤 / {@code 1408103} 攻击力) - none of them mentions RES PEN.
 *
 * <p>⚠ The case is written at BOTH ranks: a clause that applies for free and one that is correctly gated differ only at
 * E0. E2's <b>second</b> half (「单次施放【支柱•死星天裁】消耗【毁伤】点数达到 4 点时，获得 1 个额外回合」) is NOT here -
 * the engine has no "how much 【毁伤】 did this cast spend" fact at all; it is registered in the file's
 * {@code ult_transformation} note and in {@code level_convention}'s per-Eidolon table.
 */
public class PhainonEidolonTwoTest {
    private static final int PHAINON = 1408;
    private static final int ALLY = 1204;
    private static final String TRANSFORMED = "变身";

    /**
     * E0 must not have the penetration; E2 must have exactly the 20% the data's {@code #2} states.
     */
    @Test
    public void thePhysicalResistancePenetrationIsGatedByEidolonTwo() {
        double atZero = penetrationWhileTransformed(0);
        double atTwo = penetrationWhileTransformed(2);
        System.out.println("[1408] DAMAGE_PENETRATION while transformed: E0 " + atZero + " ; E2 " + atTwo);
        Assertions.assertEquals(0.0, atZero, 1e-9,
                "星魂 2 的前半句在 0 星魂时不该生效 - 它此前被记成行迹，于是白拿 +20% 物理抗性穿透");
        Assertions.assertEquals(0.2, atTwo, 1e-9, "星魂 2：卡厄斯兰那的物理属性抗性穿透提高 20%");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /**
     * His RES PEN read <b>while transformed</b>, which is the clause's scope (卡厄斯兰那 is the transformed form).
     */
    private static double penetrationWhileTransformed(int eidolonRank) {
        Battle battle = new Battle(
                List.of(CharacterFactory.create(PHAINON, 80, false, null, null, eidolonRank),
                        CharacterFactory.create(ALLY, 80)),
                List.of(BattleFixture.monster()), BattleFixture.alwaysRoll(0.9999));
        battle.startBattle();
        battle.processRequests();
        Character phainon = battle.characters().getFirst();
        phainon.setCurrentEnergy(phainon.getMaxEnergy());
        battle.castUltra(phainon, List.of(battle.enemyUnits().getFirst()));
        battle.processRequests();
        Assertions.assertTrue(phainon.getBuffManager().hasState(TRANSFORMED),
                "precondition: the Ultimate transforms him, which is the clause's scope");
        return phainon.getAttribute(AttributeType.DAMAGE_PENETRATION).get();
    }
}
