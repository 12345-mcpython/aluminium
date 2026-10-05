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
                     " ⭐ 2026-10-02：`\"max_stacks\": 1` ✓ —— 本段实测：**同属性两条加成**"
                     "要么都写 `max_stacks`（走 `addStackable` ⇒ 相加 ✓），要么后写的把先写的**替换掉** ✗"
                     "（`BuffManager.addBuff` 的 `isSameKind` 分支 ✓，设计如此 ✓）。")

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
    "source": ("1415 昔涟 行迹 三相的因果 (1415103): "
               "「昔涟的速度大于等于 **180** 点时，"
               "我方全体造成的伤害提高 **20%**」"),
    "note": ("⭐ 与天赋同属性 ✓ ⇒ 两条都写 `\"max_stacks\": 1` ✓"
             "（实测：只给一条写 ⇒ 整表上仍是 **0.2** ✗）。"),
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
 * 「速度 ≥ 180 时全队伤害 +20%」与她天赋那条 +20% 并存 (1415, 2026-10-02).
 *
 * <p>⭐ FILE-DRIVEN, and the TOTAL is the claim: 0.2 below the threshold (talent only), 0.4 past it (talent + trace). Both
 * readings are only possible when both rules are counted, which is what `max_stacks` on both buys.
 */
public class CyreneSpeedThresholdTest {
    private static final int OWNER = 1415;
    private static final int MONSTER = 1002011;

    /** ⭐ Talent alone below 180; talent plus trace past it. */
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
