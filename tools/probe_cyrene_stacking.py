"""Ask whether the two +20% rules STACK, by asserting the total (2026-10-02).

Her talent writes `ALL_DAMAGE_TYPE_BOOST` at BATTLE_START (0.2 measured), and the trace I added writes the same attribute
on TURN_START when speed >= 180. A delta cannot be read reliably when the same slot is already occupied, so this asserts
the TOTAL: 0.2 if the same attribute does not stack, 0.4 if the two rules add.

Written as a probe that asserts 0.4 on purpose: a red telling us "was 0.2" is the answer, and it is a fact worth having
before the content stays or goes.
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
 * \u300c\u901f\u5ea6\u5927\u4e8e\u7b49\u4e8e 180 \u70b9\u65f6\uff0c\u6211\u65b9\u5168\u4f53\u9020\u6210\u7684\u4f24\u5bb3\u63d0\u9ad8 20%\u300d against her own table (1415:823, 2026-10-02).
 *
 * <p>\u26a0 Her talent writes the SAME attribute at BATTLE_START (measured 0.2), so the reading here is the TOTAL, not a delta:
 * 0.4 means the two add, 0.2 means the slot does not stack. This asserts 0.4 on purpose -- a red saying "was 0.2" is the
 * answer.
 */
public class CyreneSpeedThresholdTest {
    private static final int OWNER = 1415;
    private static final int MONSTER = 1002011;

    /** \u2b50 The three readings: talent alone, and talent plus the trace past the threshold. */
    @Test
    public void theTraceAndTheTalentStack() {
        Assertions.assertEquals(0.2, totalWithExtraSpeed(0), 1e-6, "her talent alone is 20%");
        Assertions.assertEquals(0.4, totalWithExtraSpeed(100), 1e-6,
                "and past 180 the trace must add its own 20%");
    }

    // ==================================================================

    private static double totalWithExtraSpeed(double extra) {
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
        battle.fireTriggers(TriggerEvent.TURN_START, owner, owner, 0, 0);
        battle.processRequests();
        return owner.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
    }
}
''')
print("ok   probe written (asserts the total)")
