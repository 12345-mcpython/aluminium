package com.laosun.aluminium.test.content.characters.phainon;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Character 1408 (Phainon 白厄): the two numbers the 2026-10-19 audit corrected - Eidolon 1's CRIT DMG needed its gate, and
 * Khaslana's killing-blow heal is 25% of Max HP and not 20%.
 *
 * <p>⚠ Both cases are written so the defect they fix can go red: the CRIT DMG is asserted at BOTH Eidolon ranks (a clause that
 * applies for free and one that is correctly gated differ only at E0), and the heal is asserted against the share the
 * document states rather than against "it healed".
 */
public class PhainonEidolonOneTest {
    private static final int PHAINON = 1408;
    private static final String TRANSFORMED = "变身";

    /**
     * 「施放终结技时，暴击伤害提高 50%，持续 3 回合」 is <b>Eidolon 1</b>'s second half - so E0 must not have it.
     */
    @Test
    public void theCriticalDamageIsGatedByEidolonOne() {
        double atZero = criticalDamageGainFromTheUltimate(0);
        double atOne = criticalDamageGainFromTheUltimate(1);
        System.out.println("[1408] CRIT_ATTACK gain from the Ultimate: E0 " + atZero + " ; E1 " + atOne);
        Assertions.assertEquals(0.0, atZero, 1e-9,
                "星魂 1 的后半句在 0 星魂时不该生效 - 它此前被记成行迹，于是白拿 +50%");
        Assertions.assertEquals(0.5, atOne, 1e-9, "星魂 1：施放终结技时暴击伤害提高 50%");
    }

    /**
     * 「受到致命攻击时不会陷入无法战斗状态，而是回复等同于自身生命上限 25% 的生命值」.
     *
     * <p>⚠ The number, not the event: the sentence's own data row agrees (the enhanced talent's #2 = 0.25 while #7 = 0.2 is the
     * <em>other</em> clause - 「施放攻击后回复 20%」, which is a different rule). Paying 0.2 here is what the audit corrected,
     * because that row's two numbers had been swapped.
     */
    @Test
    public void theKillingBlowHealsAQuarterOfMaxHp() {
        Battle battle = newBattle();
        Character phainon = battle.characters().getFirst();
        phainon.setCurrentEnergy(phainon.getMaxEnergy());
        battle.castUltra(phainon, List.of(battle.enemyUnits().getFirst()));
        battle.processRequests();
        Assertions.assertTrue(phainon.getBuffManager().hasState(TRANSFORMED), "precondition: the Ultimate transforms him");

        double maxHp = phainon.getMaxHp();
        // Note: He is brought to half HP rather than to 0, and the reading is the DELTA: a heal is truncated at Max HP, so a
        // full-HP fixture would read 0 whatever the share is - and a raw "set HP to 0" write is not an API this engine has.
        phainon.consumeHp(maxHp * 0.5);
        double before = phainon.getCurrentHp();
        battle.fire(com.laosun.aluminium.models.trigger.TriggerPayload.Simple.of(TriggerEvent.LETHAL_DAMAGE, phainon, phainon, 0, 0));
        battle.processRequests();
        double healed = phainon.getCurrentHp() - before;
        System.out.println("[1408] a killing blow during 【变身】 heals " + healed + " of " + maxHp
                + " (" + (healed / maxHp) + ")");
        Assertions.assertEquals(0.25, healed / maxHp, 1e-9,
                "文档 `:189`：回复等同于自身生命上限 25% 的生命值（数据同一行的 #2 = 0.25）");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static double criticalDamageGainFromTheUltimate(int eidolonRank) {
        Battle battle = newBattle(eidolonRank);
        Character phainon = battle.characters().getFirst();
        double before = phainon.getAttribute(AttributeType.CRIT_ATTACK).get();
        phainon.setCurrentEnergy(phainon.getMaxEnergy());
        battle.castUltra(phainon, List.of(battle.enemyUnits().getFirst()));
        battle.processRequests();
        return phainon.getAttribute(AttributeType.CRIT_ATTACK).get() - before;
    }

    private static Battle newBattle() {
        return newBattle(0);
    }

    private static Battle newBattle(int eidolonRank) {
        Battle battle = new Battle(
                List.of(CharacterFactory.create(PHAINON, 80, false, null, null, eidolonRank),
                        CharacterFactory.create(1204, 80)),
                List.of(BattleFixture.monster()), BattleFixture.alwaysRoll(0.9999));
        battle.startBattle();
        battle.processRequests();
        return battle;
    }
}
