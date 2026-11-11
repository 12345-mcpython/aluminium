package com.laosun.aluminium.test.content.characters.asta;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.buff.StackBuff;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.List;

/**
 * 1009 艾丝妲 clause coverage. Every document section maps to a rule, except Eidolon 4, which is a REGISTERED GAP (see
 * {@code _gaps}): 「天赋的蓄能层数大于等于 2 时，艾丝妲的能量恢复效率提高 15%」 is a condition that must turn on and off as a
 * live counter crosses 2, and the axis has no "while" modifier.
 */
public class AstaClauseCoverageTest {

    /**
     * 星魂 4: 「天赋的蓄能层数 ≥2 时，能量恢复效率提高 15%」 — a THRESHOLD on a counter, not a per-layer scale.
     *
     * <p>{@code per_stack} scales continuously (each layer gives 15%); the sentence pays a flat 15% once two layers exist and
     * nothing at one. The pair {@code while_stacks} / {@code while_stacks_min} is the threshold spelling, and the gate is asked on
     * Asta's own counter at a turn boundary.
     */
    @Test
    public void eidolon4PaysOnlyAtTwoOrMoreCharges() {
        Assertions.assertEquals(0.0, regenAt(1), 1e-9, "one charge is below the threshold");
        Assertions.assertEquals(0.15, regenAt(2), 1e-9, "「大于等于 2 时…提高 15%」");
        Assertions.assertEquals(0.15, regenAt(3), 1e-9, "a third charge does not add another 15%");
    }

    private static double regenAt(int charges) {
        Character asta = CharacterFactory.create(1009, 80, false, null, null, 4);
        com.laosun.aluminium.enemy.Enemy enemy =
                BattleFixture.enemy(BattleFixture.MONSTER_ID, BattleFixture.MONSTER_LEVEL);
        Battle battle = new Battle(List.of(asta), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        for (int i = 0; i < charges; i++) {
            asta.getBuffManager().addBuff(new StackBuff("蓄能", 99, true, 99));
        }
        // The gate is swept at a TURN BOUNDARY (BuffManager.processBuffTick), not by a TURN_START event - so the fixture gives her
        // a real turn: the move signal is taken from the queue the way ArgentiStacksTest does it.
        battle.setCurrentMove(battle.queue().snapshot().stream()
                .filter(signal -> signal.getCanHit() == asta)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no signal for Asta")));
        battle.beforeMove();
        battle.afterMove();
        return asta.getAttribute(AttributeType.ENERGY_REGENERATION_RATE).get();
    }
}

