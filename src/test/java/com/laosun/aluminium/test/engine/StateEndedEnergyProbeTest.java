package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import com.laosun.aluminium.damage.Damage;

/**
 * "[生息]结束时若我方目标当前生命值等于其生命上限，则额外恢复目标 8 点能量" (1211, <b>star soul 1</b>).
 *
 * <p>FILE-DRIVEN and TWO-WAY: the clause has to be shown to matter, so full HP must pay and less-than-full HP must not.
 * The announcement is fired through the engine's own `Battle.fireStateEnded`, which is what sets the name the condition reads.
 *
 * <p>Note: <b>Built at Eidolon 1</b>, because the sentence IS her Eidolon 1. The pre-2026-11-02 version of this probe built
 * E0 — the rule carried no {@code min_eidolon} back then (the old files wrote Eidolon clauses without a threshold), so
 * building at E0 was harmless and this probe could never see the gate. When the clause was fixed it was also given its
 * threshold, and this fixture had to follow: the E0 reading is asserted in {@code BailuClauseCoverageTest} instead.
 */
public class StateEndedEnergyProbeTest {
    private static final int OWNER = 1211;
    private static final String STATE = "生息";

    /**
     * At full HP the clause pays.
     */
    @Test
    public void theClausePaysAtFullHealth() {
        Assertions.assertEquals(8.0, gain(0.0), 1e-6, "full HP pays the document's 8");
    }

    /**
     * Note: And below full HP it does not -- the clause is the point.
     */
    @Test
    public void theClauseRefusesBelowFullHealth() {
        Assertions.assertEquals(0.0, gain(0.5), 1e-6, "half HP pays nothing");
    }

    // ==================================================================

    /**
     * @param missing the share of max HP to take away before the announcement
     */
    private static double gain(double missing) {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 1);
        Battle battle = new Battle(List.of(owner),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();

        if (missing > 0) {
            // Note: Measured: no HP setter exists, and DamageElement has no IMAGINARY. So she is damaged the way the game
            // damages -- through applyDamage -- with the enemy as attacker and an element the enum really has.
            battle.applyDamage(owner, new com.laosun.aluminium.damage.Damage(battle.enemies().get(0), owner,
                    com.laosun.aluminium.enums.DamageElement.QUANTUM,
                    com.laosun.aluminium.enums.DamageType.NORMAL, owner.getMaxHp() * missing));
            battle.processRequests();
        }
        owner.setCurrentEnergy(0);
        double before = owner.getCurrentEnergy();
        battle.fireStateEnded(owner, STATE);
        battle.processRequests();
        return owner.getCurrentEnergy() - before;
    }
}
