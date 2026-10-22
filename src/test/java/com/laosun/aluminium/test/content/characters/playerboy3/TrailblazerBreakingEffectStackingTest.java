package com.laosun.aluminium.test.content.characters.playerboy3;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * The +30% Break Effect of the Ultimate and of the technique must both count (8005).
 *
 * <p>FILE-DRIVEN, four readings, and SUPERPOSITION is the claim: with both sources present the two differences must add.
 * Note: the technique comes from `battle.markTechniqueUsed(owner)` BEFORE `startBattle()` -- the engine's own entry point.
 */
public class TrailblazerBreakingEffectStackingTest {
    private static final int OWNER = 8005;

    /**
     * Both sources present means the two differences add.
     */
    @Test
    public void bothSourcesAreCounted() {
        double plain = read(false, false);
        double techniqueOnly = read(true, false);
        double ultOnly = read(false, true);
        double both = read(true, true);

        Assertions.assertTrue(Math.abs(techniqueOnly - plain) > 1e-9,
                "the technique's share lands (" + plain + " -> " + techniqueOnly + ")");
        Assertions.assertTrue(Math.abs(ultOnly - plain) > 1e-9,
                "and the ult's lands on its own (" + plain + " -> " + ultOnly + ")");
        Assertions.assertEquals((techniqueOnly - plain) + (ultOnly - plain), both - plain, 1e-6,
                "with both present the two differences must ADD: (" + techniqueOnly + " - " + plain + ") + ("
                        + ultOnly + " - " + plain + ") vs (" + both + " - " + plain + ")");
    }

    // ==================================================================

    private static double read(boolean usedTechnique, boolean castUlt) {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(owner), List.of(enemy), BattleFixture.seeded());
        if (usedTechnique) {
            battle.markTechniqueUsed(owner);
        }
        battle.startBattle();
        battle.processRequests();
        if (castUlt) {
            battle.fire(com.laosun.aluminium.models.trigger.TriggerPayload.Simple.of(TriggerEvent.ULT_CAST, owner, owner, 0, 0));
            battle.processRequests();
        }
        return owner.getAttribute(AttributeType.BREAKING_EFFECT).get();
    }
}
