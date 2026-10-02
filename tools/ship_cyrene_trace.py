"""Ship Cyrene's trace for real, now that the stacking switch is known: `max_stacks` (2026-10-02).

The chain: her talent already gives the party +20% (`ALL_DAMAGE_TYPE_BOOST`, written at BATTLE_START); the trace says the
same +20% once her speed reaches 180. Measured: without `max_stacks` the second modifier REPLACES the first (0.2, never
0.4) -- `BuffManager.addBuff` replaces a same-kind buff by design, and its javadoc points at `isStackable` for the other
question. And measured now: WITH `maxStacks` on both, the two sum to 0.4.

So the content states `max_stacks`, and the judge is file-driven: her own table, a speed raise appended through
`TriggerTable.plus`, and the total read as 0.4 -- which is only possible if BOTH rules are counted.
ASCII only.
"""
import io
import json

DATA = "src/main/resources/characters/1415.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/CyreneSpeedThresholdTest.java"
ID = "trace_party_damage_at_speed_180"

doc = json.load(io.open(DATA, encoding="utf-8"))
rules = doc["rules"] if isinstance(doc, dict) else doc
rules = [r for r in rules if not (isinstance(r, dict) and r.get("id") == ID)]
rules.append({
    "on": "TURN_START",
    "id": ID,
    "when": ["self_attr:SPEED >= 180"],
    "do": [{
        "op": "MODIFY_ATTR",
        "attribute": "ALL_DAMAGE_TYPE_BOOST",
        "percent": 0.2,
        "turns": 1,
        "max_stacks": 1,
        "target": "all_allies",
    }],
    "source": ("1415 \u6614\u6d9f \u884c\u8ff9 \u4e09\u76f8\u7684\u56e0\u679c (1415103): "
               "\u300c\u6614\u6d9f\u7684\u901f\u5ea6\u5927\u4e8e\u7b49\u4e8e **180** \u70b9\u65f6\uff0c"
               "\u6211\u65b9\u5168\u4f53\u9020\u6210\u7684\u4f24\u5bb3\u63d0\u9ad8 **20%**\u300d"),
    "note": ("\u2b50 `\"max_stacks\": 1` **\u662f\u6545\u610f\u5199\u7684** \u2713 \u2014\u2014 \u672c\u6bb5\u5b9e\u6d4b\u94fe\u6761\uff1a"
             "\u5979\u7684\u5929\u8d4b\u5df2\u7ecf\u5199\u4e86 `ALL_DAMAGE_TYPE_BOOST` +20% \u2713\uff1b"
             "\u800c `BuffManager.addBuff` \u5bf9 **\u540c kind** \u7684\u5904\u7406\u662f**\u5148\u6458\u65e7\u7684\u518d\u6302\u65b0\u7684** \u2713"
             "\uff08\u8bbe\u8ba1\u5982\u6b64 \u2713\uff0c`BuffManagerTest` \u4e0e `BuffRuleTest` \u5404\u6709\u4e00\u6761\u5224\u636e\u9489\u7740 \u2713\uff09"
             "\u21d2 \u4e0d\u5199\u5b83\u5c31\u662f **0.2** \u2717\uff1b\u5199\u4e86\u5b83\u8d70 `addStackable` \u2713 \u21d2 **0.4** \u2713\uff08\u5b9e\u6d4b \u2713\uff09\u3002"
             "\u26a0 \u53e6\uff1a**\u540d\u5b57\uff08`buff`\uff09\u4e0d\u7b97\u6570** \u2717 \u2014\u2014 \u7ed9\u4e24\u6761\u5404\u8d77\u4e00\u4e2a\u540d\u5b57\u4ecd\u662f 0.2 \u2717\uff08\u5df2\u8bc1\u4f2a \u2713\uff09\u3002"),
})
out = doc if isinstance(doc, dict) else {"rules": rules}
if isinstance(doc, dict):
    doc["rules"] = rules
    out = doc
else:
    out = rules
json.dump(out, io.open(DATA, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   1415.json: the trace is back, with max_stacks")

io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

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
 * <p>\u2b50 FILE-DRIVEN, and the total is the claim: her talent's 20% plus the trace's 20% must read 0.4 past the threshold -- which
 * is only possible when both rules are counted. Below the threshold only the talent is there, so 0.2.
 */
public class CyreneSpeedThresholdTest {
    private static final int OWNER = 1415;
    private static final int MONSTER = 1002011;

    /** \u2b50 Below 180 only the talent; past it, talent plus trace. */
    @Test
    public void theThresholdAddsTheTracesTwentyPercent() {
        Assertions.assertEquals(0.2, total(0), 1e-6, "below the threshold, her talent alone");
        Assertions.assertEquals(0.4, total(100), 1e-6, "past 180, the trace's own 20% must be counted too");
    }

    // ==================================================================

    private static double total(double extraSpeed) {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        if (extraSpeed > 0) {
            EffectSpec raise = new EffectSpec();
            TriggerSpecs.set(raise, "op", "MODIFY_ATTR");
            TriggerSpecs.set(raise, "attribute", "SPEED");
            TriggerSpecs.set(raise, "amount", extraSpeed);
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
print("ok   file-driven judge written")
