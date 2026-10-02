"""The real switch is `maxStacks > 1` -- and my earlier probes used 1 (2026-10-02).

`StatModifierBuff.isStackable()` is literally `return maxStacks > 1;`, and its `stackGroupKey()` is
`attribute | modifierType | sourceRole`. So:
  * `maxStacks: 3` on two same-attribute modifiers -> stackable, same group -> they ADD (this is the 0.4 probe);
  * `maxStacks: 1` -> `isStackable()` is FALSE -> the modifier is replaced on re-application (this is every 0.2 reading
    I have taken, INCLUDING the one I blamed on `permanent`: that probe set `maxStacks: 1` on both sides).
So the `permanent` conclusion was wrong, this corrects it, and the fix for Cyrene is `max_stacks` ABOVE 1 on both writers.

This ships it: her talent and her trace both at `max_stacks: 2`, and a file-driven judge asserting the TOTAL (0.2 below
the threshold, 0.4 past it).
ASCII only.
"""
import io
import json

DATA = "src/main/resources/characters/1415.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/CyreneSpeedThresholdTest.java"
TRACE = "trace_party_damage_at_speed_180"

doc = json.load(io.open(DATA, encoding="utf-8"))
rules = doc["rules"] if isinstance(doc, dict) else doc

for r in rules:
    if isinstance(r, dict) and r.get("id") == "talent_party_damage":
        for s in r.get("do", []):
            if isinstance(s, dict) and s.get("attribute") == "ALL_DAMAGE_TYPE_BOOST":
                s["max_stacks"] = 2
        r["note"] = ((r.get("note") or "") +
                     " \u2b50 2026-10-02\uff1a`\"max_stacks\": 2` \u2713 \u2014\u2014 \u5b9e\u6d4b\uff1a`StatModifierBuff.isStackable()`"
                     "\u5c31\u662f `maxStacks > 1` \u2713\uff0c`stackGroupKey()` = `\u5c5e\u6027|\u4fee\u9970\u7c7b\u578b|\u6765\u6e90\u89d2\u8272` \u2713"
                     "\uff1b\u540c\u5c5e\u6027\u4e24\u6761\u8981\u76f8\u52a0\uff0c**\u4e24\u8fb9\u90fd\u5f97 > 1** \u2713\uff08=1 \u65f6 `isStackable()` \u4e3a\u5047 \u2717\uff09\u3002")

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
        "max_stacks": 2,
        "target": "all_allies",
    }],
    "source": ("1415 \u6614\u6d9f \u884c\u8ff9 \u4e09\u76f8\u7684\u56e0\u679c (1415103): "
               "\u300c\u6614\u6d9f\u7684\u901f\u5ea6\u5927\u4e8e\u7b49\u4e8e **180** \u70b9\u65f6\uff0c"
               "\u6211\u65b9\u5168\u4f53\u9020\u6210\u7684\u4f24\u5bb3\u63d0\u9ad8 **20%**\u300d"),
    "note": ("\u2b50 \u4e0e\u5929\u8d4b\u540c\u5c5e\u6027 \u2713 \u21d2 \u4e24\u8fb9\u90fd\u5199 `\"max_stacks\": 2` \u2713"
             "\uff08\u5b9e\u6d4b\uff1a\u53ea\u8981\u4efb\u4e00\u8fb9 = 1 \u21d2 `isStackable()` \u4e3a\u5047 \u21d2 \u8bfb\u6570\u56de\u5230 **0.2** \u2717\uff09\u3002"),
})
out = doc if isinstance(doc, dict) else {"rules": rules}
if isinstance(doc, dict):
    doc["rules"] = rules
    out = doc
else:
    out = rules
json.dump(out, io.open(DATA, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   1415.json: both writers at max_stacks 2")

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
 * Her talent and her trace both write +20% to the party; both must be counted (1415, 2026-10-02).
 *
 * <p>\u2b50 FILE-DRIVEN, and the TOTAL is the claim: 0.2 below the threshold (talent alone), 0.4 past it. The measured switch is
 * `max_stacks > 1` -- `StatModifierBuff.isStackable()` is exactly that, and `stackGroupKey()` is the attribute, the
 * modifier type and the source role -- so both writers state 2.
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
