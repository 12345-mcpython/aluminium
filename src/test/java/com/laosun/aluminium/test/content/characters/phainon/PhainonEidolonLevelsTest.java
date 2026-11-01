package com.laosun.aluminium.test.content.characters.phainon;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Character 1408 (Phainon 白厄): his Eidolons 3 and 5 raise skill levels, and until the 2026-10-19 audit <b>no rule did it</b> -
 * the silence was the symptom: 「终结技等级+2」 simply did nothing.
 *
 * <p>⚠ Both cases read a LEVEL, so they are written at all three ranks: E0 (the convention's row), E3 (ULTRA +2) and E5 (SKILL
 * and TALENT +2). The common attack is pinned too, because its raise is CLAMPED by its own 10-row table - "最多不超过 10 级"
 * is the data's doing, not a second op.
 */
public class PhainonEidolonLevelsTest {
    private static final int PHAINON = 1408;
    private static final int ALLY = 1204;

    /**
     * The rows each Eidolon actually moves.
     */
    @Test
    public void theEidolonsRaiseTheSlotsTheyName() {
        int[] atZero = levelsAt(0);
        int[] atThree = levelsAt(3);
        int[] atFive = levelsAt(5);
        System.out.println("[1408] levels COMMON/SKILL/ULTRA/TALENT: E0 " + shown(atZero) + " ; E3 " + shown(atThree)
                + " ; E5 " + shown(atFive));
        Assertions.assertArrayEquals(new int[]{6, 10, 10, 10}, atZero, "等级口径：普攻钉第 6 行，其余三槽第 10 行");
        Assertions.assertArrayEquals(new int[]{7, 10, 12, 10}, atThree,
                "星魂 3：终结技+2 ⇒ 第 12 行；普攻 6 ⇒ 7");
        Assertions.assertArrayEquals(new int[]{7, 12, 12, 12}, atFive,
                "星魂 5：战技+2、天赋+2 ⇒ 各第 12 行（终结技的 +2 来自星魂 3，仍在）");
    }

    /**
     * 「若施放者为白厄的队友，还会使白厄的暴击伤害提高 30%」 - the share is the TALENT row's #1, and E5 moves that row.
     */
    @Test
    public void theTalentCritDamageFollowsTheTalentRow() {
        double atZero = critDamageFromAnAlliesAbility(0);
        double atFive = critDamageFromAnAlliesAbility(5);
        System.out.println("[1408] CRIT_ATTACK from an ally's ability: E0 +" + atZero + " ; E5 +" + atFive
                + " (the Lv10 literal would read +0.3 at both)");
        Assertions.assertEquals(0.3, atZero, 1e-9, "天赋第 10 行的 #1 = 30%");
        Assertions.assertEquals(0.33, atFive, 1e-9, "星魂 5 给天赋 +2 级 ⇒ 第 12 行的 #1 = 33%");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static String shown(int[] levels) {
        return "C=" + levels[0] + " S=" + levels[1] + " U=" + levels[2] + " T=" + levels[3];
    }

    /**
     * COMMON, SKILL, ULTRA and TALENT's EFFECTIVE levels (base plus every raise), in that order.
     */
    private static int[] levelsAt(int eidolonRank) {
        Character phainon = newBattle(eidolonRank).characters().getFirst();
        return new int[]{
                phainon.skillLevel(phainon.getSkills().get(SkillType.COMMON)),
                phainon.skillLevel(phainon.getSkills().get(SkillType.SKILL)),
                phainon.skillLevel(phainon.getSkills().get(SkillType.ULTRA)),
                phainon.skillLevel(phainon.getSkills().get(SkillType.TALENT))};
    }

    /**
     * Her CRIT DMG gain when a TEAMMATE's ability targets him - the event the talent's second half hangs on.
     */
    private static double critDamageFromAnAlliesAbility(int eidolonRank) {
        Battle battle = newBattle(eidolonRank);
        Character phainon = battle.characters().getFirst();
        Character ally = battle.characters().get(1);
        double before = phainon.getAttribute(AttributeType.CRIT_ATTACK).get();
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.SKILL_CAST, ally, phainon, 0, 0));
        battle.processRequests();
        return phainon.getAttribute(AttributeType.CRIT_ATTACK).get() - before;
    }

    private static Battle newBattle(int eidolonRank) {
        Battle battle = new Battle(
                List.of(CharacterFactory.create(PHAINON, 80, false, null, null, eidolonRank),
                        CharacterFactory.create(ALLY, 80)),
                List.of(BattleFixture.monster()), BattleFixture.alwaysRoll(0.9999));
        battle.startBattle();
        battle.processRequests();
        return battle;
    }
}
