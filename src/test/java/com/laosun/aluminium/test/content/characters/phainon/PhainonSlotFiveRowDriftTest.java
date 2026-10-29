package com.laosun.aluminium.test.content.characters.phainon;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.entity.Character;
import com.laosun.aluminium.models.skill.DefaultSkill;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1408: the transformation's ATK / Max HP block is written with the row's Lv10 numbers ({@code 0.8} / {@code 2.7}), and
 * <b>Eidolon 5 raises that very row by two levels</b>.
 *
 * <p>⚠⚠ <b>This is a GAP WITNESS, not a pass.</b> The block lives in data slot <b>5</b> ({@code 140805}, 卡厄斯兰那's
 * talent), and slot 5 has no {@code SkillType} - the mapped slots are 1 / 2 / 3 / 4 / 6 / 7 / 20 / 21 (see
 * {@code SkillSlotMappingTest}). So {@code percent_from_skill_param} / {@code percent_from_cast_param} cannot name it, and
 * the rule keeps the Lv10 literal while the effective level becomes 12: row 10 says {@code [4, 0.25, 0.125, 0.8, 2.7, ...]}
 * and row 12 says {@code [4, 0.25, 0.125, 0.88, 2.97, ...]}.
 *
 * <p>Why a witness rather than a fix: reading "the row of a slot that has no SkillType" is a capability, not a number, and
 * the three other slots in the same position ({@code 140808} 强化普攻, {@code 140809} / {@code 140811} 强化战技) need it too.
 * It is registered in {@code AGENT.md} §4.3; when it lands, THIS case is what goes red and points at the file to update.
 *
 * <p>⚠ What is measured is the DELTA the block adds, at E0 and at E5 with the same starting board - so the comparison
 * needs no assumption about the base attribute or about the trace that also raises ATK. Measured: the two deltas are equal,
 * which is exactly the defect: a rule reading the row would have gained 10% (0.88 / 0.8).
 */
public class PhainonSlotFiveRowDriftTest {
    private static final int PHAINON = 1408;
    private static final int ALLY = 1204;
    private static final int SLOT_FIVE = 5;
    private static final int COLUMN_ATK = 3;
    private static final int COLUMN_HP = 4;

    /**
     * At E5 the slot-5 row moves to 0.88 / 2.97, and the applied block is still the Lv10 literal 0.8 / 2.7.
     */
    @Test
    public void theBlockKeepsTheLvTenLiteralsWhileEidolonFiveRaisesTheirRow() {
        // Note: the row is read at the level the engine would use if slot 5 could be named: 10 (the convention) + 2 (E5).
        List<Double> rowTwelve = new DefaultSkill(PHAINON, SLOT_FIVE, 12).getData().getSkills().get(11);
        System.out.println("[1408] slot 5 row 12 = " + rowTwelve);
        Assertions.assertEquals(0.88, rowTwelve.get(COLUMN_ATK), 1e-9,
                "星魂 5 给槽 5（140805）+2 级 ⇒ 第 12 行的 #4 = 0.88");
        Assertions.assertEquals(2.97, rowTwelve.get(COLUMN_HP), 1e-9, "同一行的 #5 = 2.97");

        double[] atZero = appliedBlock(0);
        double[] atFive = appliedBlock(5);
        System.out.println("[1408] 变身块落下的增量: E0 ATK +" + atZero[0] + " / HEALTH +" + atZero[1]
                + " ; E5 ATK +" + atFive[0] + " / HEALTH +" + atFive[1]
                + " (第 12 行说 0.88 / 2.97，字面量是 0.8 / 2.7)");
        Assertions.assertEquals(atZero[0], atFive[0], 1e-9,
                "今天落下的是 Lv10 的字面量 ⇒ 两档的增量一样（缺口：第 12 行是 0.88，应多 10%）");
        Assertions.assertEquals(atZero[1], atFive[1], 1e-9,
                "生命上限同理：字面量 2.7，而第 12 行是 2.97");
    }

    /**
     * And the slot cannot be named at all, which is the reason: no {@link SkillType} maps to 5.
     */
    @Test
    public void slotFiveHasNoSkillType() {
        for (SkillType type : SkillType.values()) {
            Assertions.assertNotEquals(SLOT_FIVE, Constant.SKILL_SLOT.get(type),
                    "谁把槽 5 映射进 SkillType，这条见证就该改成真判据");
        }
    }

    // ==================================================================

    /**
     * The ATK and Max HP the transformation's block adds, in points, on an identical starting board.
     */
    private static double[] appliedBlock(int eidolonRank) {
        Battle battle = new Battle(
                List.of(CharacterFactory.create(PHAINON, 80, false, null, null, eidolonRank),
                        CharacterFactory.create(ALLY, 80)),
                List.of(BattleFixture.monster()), BattleFixture.alwaysRoll(0.9999));
        battle.startBattle();
        battle.processRequests();
        Character phainon = battle.characters().getFirst();
        double atkBefore = phainon.getAttribute(AttributeType.ATTACK).get();
        double hpBefore = phainon.getAttribute(AttributeType.HEALTH).get();
        phainon.setCurrentEnergy(phainon.getMaxEnergy());
        battle.castUltra(phainon, List.of(battle.enemyUnits().getFirst()));
        battle.processRequests();
        Assertions.assertTrue(phainon.getBuffManager().hasState("变身"), "precondition: the transformation is on");
        return new double[]{
                phainon.getAttribute(AttributeType.ATTACK).get() - atkBefore,
                phainon.getAttribute(AttributeType.HEALTH).get() - hpBefore};
    }
}
