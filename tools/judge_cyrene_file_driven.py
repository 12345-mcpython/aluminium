"""Ask the one question the last judge could not: does HER FILE's rule fire? (2026-10-02)

The shipped judge (`CyreneSpeedThresholdTest`) proves the shape on a clean hand-built table. It does not read 1415.json.
This rewrites the judge to be file-driven -- her own table, with only a `BATTLE_START` speed raise APPENDED through
`TriggerTable.plus` (which a passing probe showed works) -- and fires her TURN_START by hand.

If this reads 0.2, the clause is bound end to end. If it reads 0, then her table interacts with the rule somehow, and that
is worth its own record rather than another rewrite.
ASCII only.
"""
import io

io.open("src/test/java/com/laosun/aluminium/test/CyreneSpeedThresholdTest.java",
        "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * \u300c\u6614\u6d9f\u7684\u901f\u5ea6\u5927\u4e8e\u7b49\u4e8e 180 \u70b9\u65f6\uff0c\u6211\u65b9\u5168\u4f53\u9020\u6210\u7684\u4f24\u5bb3\u63d0\u9ad8 20%\u300d (1415:823, 2026-10-02) -- FILE-DRIVEN.
 *
 * <p>\u2b50 Her own table, with only a speed raise APPENDED (`TriggerTable.plus`, verified by probe), and her TURN_START fired
 * by hand. This is the end-to-end binding: the rule under test is the one in `1415.json`.
 */
public class CyreneSpeedThresholdTest {
    private static final int OWNER = 1415;
    private static final int MONSTER = 1002011;

    /** \u2b50 Below the threshold her rule does nothing; past it, the document's 20%. */
    @Test
    public void theThresholdGatesThePartyBoost() {
        Assertions.assertEquals(0, boostWithExtraSpeed(0), 1e-9,
                "at her own speed the rule must not fire");
        Assertions.assertEquals(0.2, boostWithExtraSpeed(100), 1e-6,
                "past 180 it must give the document's 20%");
    }

    // ==================================================================

    private static double boostWithExtraSpeed(double extra) {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        if (extra > 0) {
            EffectSpec raise = new EffectSpec();
            TriggerSpecs.set(raise, "op", "MODIFY_ATTR");
            TriggerSpecs.set(raise, "attribute", "SPEED");
            TriggerSpecs.set(raise, "amount", extra);
            TriggerSpecs.set(raise, "permanent", true);
            TriggerSpecs.set(raise, "target", "self");
            owner.setTriggerTable(owner.getTriggerTable()
                    .plus(new TriggerTable(OWNER, List.of(TriggerSpecs.rule("BATTLE_START", List.of(), raise)))));
        }
        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        double before = owner.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        battle.fireTriggers(TriggerEvent.TURN_START, owner, owner, 0, 0);
        battle.processRequests();
        return owner.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get() - before;
    }
}
''')
print("ok   judge rewritten file-driven")
