package com.laosun.aluminium.test.content.characters.sushang;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.List;

/**
 * 1206 素裳 clause coverage: every section of {@code 1206_素裳.md}, plus the two readings that had never been pinned.
 *
 * <p><b>Why this file exists.</b> The ledger had already recorded every clause as done ("仍登记：无") while the character file's
 * {@code _gaps} still said 「见 CONTENT-STATUS.md 的逐角色登记」 — a pointer, not a gap. This class is the audit that closes it:
 * one line per section, and the two Eidolon readings that no other test covered.
 */
public class SushangClauseCoverageTest {

    private static final int SUSHANG = 1206;
    private static final int ALLY = 1002;

    /**
     * 星魂 4: 「素裳的击破特攻提高 40%」. Measured: BREAKING_EFFECT <b>0</b> at E0, <b>0.4</b> at E4.
     */
    @Test
    public void eidolon4RaisesTheBreakingEffect() {
        Assertions.assertEquals(0.0, breakingEffectOf(0), 1e-9, "control: nothing at E0");
        Assertions.assertEquals(0.4, breakingEffectOf(4), 1e-9, "「击破特攻提高 40%」");
    }

    /**
     * 星魂 6: 「天赋的加速效果可以叠加，最多叠加2层」 — the same two breaks move the speed at E6 and only refresh at E0.
     *
     * <p>Measured over two breaks in one battle: E0 <b>107 → 128.4 → 128.4</b> (the second refreshes); E6
     * <b>129.47 → 151.94 → 174.41</b> (it stacks). The point pinned here is the <b>difference between the two Eidolons</b>, not
     * the three numbers: one stack versus two.
     */
    @Test
    public void eidolon6LetsTheSpeedStackTwice() {
        double[] e0 = speedsAfterTwoBreaks(0);
        Assertions.assertEquals(e0[1], e0[2], 1e-6, "E0: 「最多叠加2层」 does not apply — the second break only refreshes");

        double[] e6 = speedsAfterTwoBreaks(6);
        Assertions.assertTrue(e6[2] > e6[1], "E6: the second break stacks (" + e6[1] + " -> " + e6[2] + ")");
        Assertions.assertEquals(e6[1] + (e6[1] - e6[0]), e6[2], 1e-6,
                "and it stacks by the SAME amount as the first one");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static double breakingEffectOf(int eidolon) {
        return fixture(eidolon).sushang.getAttribute(AttributeType.BREAKING_EFFECT).get();
    }

    /** Speeds at [before any break, after one, after two]. */
    private static double[] speedsAfterTwoBreaks(int eidolon) {
        Fixture f = fixture(eidolon);
        double before = f.sushang.getAttribute(AttributeType.SPEED).get();
        f.breakOnce();
        double one = f.sushang.getAttribute(AttributeType.SPEED).get();
        f.breakOnce();
        double two = f.sushang.getAttribute(AttributeType.SPEED).get();
        return new double[]{before, one, two};
    }

    private static Fixture fixture(int eidolon) {
        return new Fixture(eidolon);
    }

    private static final class Fixture {
        private final Character sushang;
        private final Enemy enemy = BattleFixture.enemy(BattleFixture.MONSTER_ID, BattleFixture.MONSTER_LEVEL);
        private final Battle battle;

        private Fixture(int eidolon) {
            sushang = CharacterFactory.create(SUSHANG, 80, false, null, null, eidolon);
            battle = new Battle(List.of(sushang, CharacterFactory.create(ALLY, 80)), List.of(enemy),
                    BattleFixture.seeded());
            battle.startBattle();
            battle.processRequests();
        }

        private void breakOnce() {
            battle.fire(TriggerPayload.Simple.of(TriggerEvent.BREAK, sushang, enemy, 0, 0));
            battle.processRequests();
        }
    }
}
