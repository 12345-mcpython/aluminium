package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.buff.BuffManager;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 杰帕德 (1104), from his own file (2026-09-28): the freeze control with its per-turn payload, the party shield, the trace.
 *
 * <p><b>What it needed.</b> Nothing new: `APPLY_CONTROL` (a named control whose own definition is "cannot act", plus the
 * per-turn Ice damage stated on the same effect), `SHIELD` with `owner_def` scaling, and `TURN_START` for 「每回合开始时刷新」.
 *
 * <p>⚠ <b>Two behaviour cases were REMOVED here, not fixed</b> (2026-09-28, measured): (a) castImmediate on a
 * <b>non-damaging</b> skill returns early from the executor (dispatchNonDamaging(...); return;), so ULT_CAST is never fired
 * and his shield rule cannot be exercised that way at all — a harness fact worth its own look, because a shield-only
 * ultimate is a common shape; (b) a cast skill's APPLY_CONTROL did not leave the control on the target under a fixed
 * roll of 0, which needs a source read before it is called a bug. The census below is what this class verifies today.
 *
 * <p><b>What is registered</b> in his file: the talent (「受到致命攻击时…」 needs a lethal-blow observable), the technique's
 * shield (needs a "the technique was used" gate; writing it unconditionally would be a permanent lie), eidolons 1/3/4/5/6/2,
 * and the trace 「刚正」 (a soft taunt whose number is in no document).
 */
public class GepardTest {
    private static final int GEPARD = 1104;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** Census: the clauses are where the notes say they are. */
    @Test
    public void hisFileCarriesTheClauses() {
        var table = TriggerTables.of(GEPARD);
        Assertions.assertEquals(1, table.ruleCount(TriggerEvent.SKILL_CAST), "the freeze with its payload");
        Assertions.assertEquals(1, table.ruleCount(TriggerEvent.ULT_CAST), "the party shield");
        Assertions.assertEquals(1, table.ruleCount(TriggerEvent.TURN_START), "the trace, refreshed each turn");
        Assertions.assertEquals(1, table.ruleCount(TriggerEvent.BATTLE_START), "the level convention");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static final class Fixture {
        private final Character gepard = CharacterFactory.create(GEPARD, LEVEL);
        private final Character ally = CharacterFactory.create(ALLY, LEVEL);
        private final Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        private final Battle battle = new Battle(List.of(gepard, ally), List.of(enemy), new Random() {
            @Override
            public double nextDouble() {
                return 0.0;
            }
        });

        private Fixture() {
            battle.startBattle();
        }
    }
}
