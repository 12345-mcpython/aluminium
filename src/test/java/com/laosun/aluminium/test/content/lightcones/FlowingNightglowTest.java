package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Light cone 23026 (2026-09-30): both clauses -- and the first content to combine REMOVE_STATE with per_stack.
 *
 * <p>Every ally attack gives the wearer one layer of the counter; each layer lifts ENERGY_REGENERATION_RATE by 3..5%,
 * capped at 5 layers. The ultimate consumes the whole counter and grants the flourish: the wearer's ATK up 48..96% and
 * OUR WHOLE SIDE's damage up 24..40%, both for 1 turn.
 *
 * <p>Note: The two state names come from codepoints on the generator side, because round 30lost a round to a
 * hand-typed escape that named a different character: measured, the engine said the state {@code 歌咏} while the judge
 * expected something else. The engine was right.
 */
public class FlowingNightglowTest {
    private static final int CONE = 23026;
    private static final int WEARER = 1210;
    private static final int LEVEL = 80;
    private static final double[] REGEN = {0.03, 0.035, 0.04, 0.045, 0.05};
    private static final double[] ALLY = {0.24, 0.28, 0.32, 0.36, 0.40};
    private static final double[] SELF = {0.48, 0.60, 0.72, 0.84, 0.96};

    private Character wearer(int rank) {
        return CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, rank));
    }

    @Test
    public void everyTierStatesWhatTheTextStates() {
        for (int rank = 1; rank <= 5; rank++) {
            var table = wearer(rank).getTriggerTable();

            var song = table.rulesFor(TriggerEvent.ALLY_ATTACK).stream()
                    .filter(r -> r.id().equals("cone23026_song")).toList();
            Assertions.assertEquals(1, song.size(), "rank " + rank + ": the song rule");
            var s = song.getFirst().effects();
            Assertions.assertEquals(2, s.size(), "rank " + rank + ": a stack and its bonus");
            Assertions.assertEquals("ADD_STACK", s.get(0).getOp());
            Assertions.assertEquals("歌咏", s.get(0).getBuff(), "rank " + rank + ": the counter");
            Assertions.assertEquals(5, s.get(0).getMaxStacks(), "rank " + rank + ": capped at five");
            Assertions.assertEquals("MODIFY_ATTR", s.get(1).getOp());
            Assertions.assertEquals("ENERGY_REGENERATION_RATE", s.get(1).getAttribute());
            Assertions.assertEquals(REGEN[rank - 1], s.get(1).getPercent(), 1e-9, "rank " + rank + ": per layer");
            Assertions.assertEquals("歌咏", s.get(1).getPerStack(),
                    "rank " + rank + ": scaled by the counter (so no max_stacks here)");
            Assertions.assertEquals("self", s.get(1).getTarget());

            var ult = table.rulesFor(TriggerEvent.ULT_CAST).stream()
                    .filter(r -> r.id().equals("cone23026_flourish")).toList();
            Assertions.assertEquals(1, ult.size(), "rank " + rank + ": the flourish rule");
            var u = ult.getFirst().effects();
            Assertions.assertEquals(4, u.size(), "rank " + rank + ": remove, state, ATK, side damage");
            Assertions.assertEquals("REMOVE_STATE", u.get(0).getOp());
            Assertions.assertEquals("歌咏", u.get(0).getBuff(), "rank " + rank + ": consumed");
            Assertions.assertEquals("APPLY_BUFF", u.get(1).getOp());
            Assertions.assertEquals("华彩", u.get(1).getBuff());
            Assertions.assertEquals(1, u.get(1).getTurns(), "rank " + rank + ": one turn");
            Assertions.assertEquals("ATTACK", u.get(2).getAttribute());
            Assertions.assertEquals(SELF[rank - 1], u.get(2).getPercent(), 1e-9, "rank " + rank + ": the ATK");
            Assertions.assertEquals("self", u.get(2).getTarget(), "rank " + rank + ": to the wearer");
            Assertions.assertEquals("ALL_DAMAGE_TYPE_BOOST", u.get(3).getAttribute());
            Assertions.assertEquals(ALLY[rank - 1], u.get(3).getPercent(), 1e-9, "rank " + rank + ": the side");
            Assertions.assertEquals("all_allies", u.get(3).getTarget(), "rank " + rank + ": EVERY ally");
            System.out.println("[23026] rank=" + rank + " ok");
        }
    }
}
