"""Cyrene's two +20% rules, made to ADD (2026-10-02) -- the template for the other sixteen.

Measured chain: `MODIFY_ATTR` with a duration is a buff; `BuffManager.addBuff` REPLACES a same-kind buff by design (its
javadoc, two judges); `max_stacks` sends it down `addStackable` instead, and then `DoubleValue.compute()` sums them --
clean table, both with `maxStacks`, reads 0.4.

So BOTH rules that write `ALL_DAMAGE_TYPE_BOOST` must say it: her talent (existing, `talent_party_damage`) and the trace
(1415:823). Both sentences state +20%, so both should count; leaving either unnamed-stackable loses one of them.

The judge is file-driven and asserts the TOTAL (0.2 below the threshold, 0.4 past it), which is only possible when both
are counted.
ASCII only.
"""
import io
import json

DATA = "src/main/resources/characters/1415.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/CyreneSpeedThresholdTest.java"
TRACE = "trace_party_damage_at_speed_180"

doc = json.load(io.open(DATA, encoding="utf-8"))
rules = doc["rules"] if isinstance(doc, dict) else doc

# 1) her talent: same attribute, so it must declare itself stackable too
for r in rules:
    if isinstance(r, dict) and r.get("id") == "talent_party_damage":
        for s in r.get("do", []):
            if isinstance(s, dict) and s.get("attribute") == "ALL_DAMAGE_TYPE_BOOST":
                s["max_stacks"] = 1
        r["note"] = ((r.get("note") or "") +
                     " \u2b50 2026-10-02\uff1a`\"max_stacks\": 1` \u2713 \u2014\u2014 \u672c\u6bb5\u5b9e\u6d4b\uff1a**\u540c\u5c5e\u6027\u4e24\u6761\u52a0\u6210**"
                     "\u8981\u4e48\u90fd\u5199 `max_stacks`\uff08\u8d70 `addStackable` \u21d2 \u76f8\u52a0 \u2713\uff09\uff0c\u8981\u4e48\u540e\u5199\u7684\u628a\u5148\u5199\u7684**\u66ff\u6362\u6389** \u2717"
                     "\uff08`BuffManager.addBuff` \u7684 `isSameKind` \u5206\u652f \u2713\uff0c\u8bbe\u8ba1\u5982\u6b64 \u2713\uff09\u3002")

# 2) the trace, same shape
rules = [r for r in rules if not (isinstance(r, dict) and r.get("id") == TRACE)]
rules.append({
    "on": "TURN_START",
    "id": TRACE,
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
    "note": ("\u2b50 \u4e0e\u5929\u8d4b\u540c\u5c5e\u6027 \u2713 \u21d2 \u4e24\u6761\u90fd\u5199 `\"max_stacks\": 1` \u2713"
             "\uff08\u5b9e\u6d4b\uff1a\u53ea\u7ed9\u4e00\u6761\u5199 \u21d2 \u6574\u8868\u4e0a\u4ecd\u662f **0.2** \u2717\uff09\u3002"),
})
out = doc if isinstance(doc, dict) else {"rules": rules}
if isinstance(doc, dict):
    doc["rules"] = rules
    out = doc
else:
    out = rules
json.dump(out, io.open(DATA, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   1415.json: both writers now declare max_stacks")

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
 * \u300c\u901f\u5ea6 \u2265 180 \u65f6\u5168\u961f\u4f24\u5bb3 +20%\u300d\u4e0e\u5979\u5929\u8d4b\u90a3\u6761 +20% \u5e76\u5b58 (1415, 2026-10-02).
 *
 * <p>\u2b50 FILE-DRIVEN, and the TOTAL is the claim: 0.2 below the threshold (talent only), 0.4 past it (talent + trace). Both
 * readings are only possible when both rules are counted, which is what `max_stacks` on both buys.
 */
public class CyreneSpeedThresholdTest {
    private static final int OWNER = 1415;
    private static final int MONSTER = 1002011;

    /** \u2b50 Talent alone below 180; talent plus trace past it. */
    @Test
    public void theTwoWritersAdd() {
        Assertions.assertEquals(0.2, total(0), 1e-6, "below the threshold, her talent alone");
        Assertions.assertEquals(0.4, total(100), 1e-6, "past 180, both writers must be counted");
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
