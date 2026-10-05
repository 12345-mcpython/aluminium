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
                     " ⭐ 2026-10-02：`\"max_stacks\": 2` ✓ —— 实测：`StatModifierBuff.isStackable()`"
                     "就是 `maxStacks > 1` ✓，`stackGroupKey()` = `属性|修饰类型|来源角色` ✓"
                     "；同属性两条要相加，**两边都得 > 1** ✓（=1 时 `isStackable()` 为假 ✗）。")

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
    "source": ("1415 昔涟 行迹 三相的因果 (1415103): "
               "「昔涟的速度大于等于 **180** 点时，"
               "我方全体造成的伤害提高 **20%**」"),
    "note": ("⭐ 与天赋同属性 ✓ ⇒ 两边都写 `\"max_stacks\": 2` ✓"
             "（实测：只要任一边 = 1 ⇒ `isStackable()` 为假 ⇒ 读数回到 **0.2** ✗）。"),
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
 * <p>⭐ FILE-DRIVEN, and the TOTAL is the claim: 0.2 below the threshold (talent alone), 0.4 past it. The measured switch is
 * `max_stacks > 1` -- `StatModifierBuff.isStackable()` is exactly that, and `stackGroupKey()` is the attribute, the
 * modifier type and the source role -- so both writers state 2.
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
