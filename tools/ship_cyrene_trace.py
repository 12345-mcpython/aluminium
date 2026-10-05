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
    "source": ("1415 昔涟 行迹 三相的因果 (1415103): "
               "「昔涟的速度大于等于 **180** 点时，"
               "我方全体造成的伤害提高 **20%**」"),
    "note": ("⭐ `\"max_stacks\": 1` **是故意写的** ✓ —— 本段实测链条："
             "她的天赋已经写了 `ALL_DAMAGE_TYPE_BOOST` +20% ✓；"
             "而 `BuffManager.addBuff` 对 **同 kind** 的处理是**先摘旧的再挂新的** ✓"
             "（设计如此 ✓，`BuffManagerTest` 与 `BuffRuleTest` 各有一条判据钉着 ✓）"
             "⇒ 不写它就是 **0.2** ✗；写了它走 `addStackable` ✓ ⇒ **0.4** ✓（实测 ✓）。"
             "⚠ 另：**名字（`buff`）不算数** ✗ —— 给两条各起一个名字仍是 0.2 ✗（已证伪 ✓）。"),
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
 * 「速度大于等于 180 点时，我方全体造成的伤害提高 20%」 against her own table (1415:823, 2026-10-02).
 *
 * <p>⭐ FILE-DRIVEN, and the total is the claim: her talent's 20% plus the trace's 20% must read 0.4 past the threshold -- which
 * is only possible when both rules are counted. Below the threshold only the talent is there, so 0.2.
 */
public class CyreneSpeedThresholdTest {
    private static final int OWNER = 1415;
    private static final int MONSTER = 1002011;

    /** ⭐ Below 180 only the talent; past it, talent plus trace. */
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
