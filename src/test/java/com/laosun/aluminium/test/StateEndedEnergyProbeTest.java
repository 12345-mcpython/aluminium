package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 「【生息】结束时若我方目标当前生命值等于其生命上限，则额外恢复目标 8 点能量」 (1211, 2026-10-02).
 *
 * <p>⭐ FILE-DRIVEN and TWO-WAY: the clause has to be shown to matter, so full HP must pay and less-than-full HP must not.
 * The announcement is fired through the engine's own `Battle.fireStateEnded`, which is what sets the name the condition reads.
 */
public class StateEndedEnergyProbeTest {
    private static final int OWNER = 1211;
    private static final int MONSTER = 1002011;
    private static final String STATE = "生息";

    /** ⭐ At full HP the clause pays. */
    @Test
    public void theClausePaysAtFullHealth() {
        Assertions.assertEquals(8.0, gain(0.0), 1e-6, "full HP pays the document's 8");
    }

    /** ⚠ And below full HP it does not -- the clause is the point. */
    @Test
    public void theClauseRefusesBelowFullHealth() {
        Assertions.assertEquals(0.0, gain(0.5), 1e-6, "half HP pays nothing");
    }

    // ==================================================================

    /** @param missing the share of max HP to take away before the announcement */
    private static double gain(double missing) {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        if (missing > 0) {
            // ⚠ Measured: no HP setter exists, and DamageElement has no IMAGINARY. So she is damaged the way the game
            // damages -- through applyDamage -- with the enemy as attacker and an element the enum really has.
            battle.applyDamage(owner, new com.laosun.aluminium.models.Damage(battle.enemies.get(0), owner,
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
