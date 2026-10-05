"""Cyrene's trace: the party-wide +20% damage once her speed reaches 180 (2026-10-02).

Sentence (1415:823, 行迹 三相的因果, id 1415103): 「昔涟的速度大于等于180点时，我方全体造成的伤害提高20%，之后每超过1点速度，
昔涟与德谬歌的冰属性抗性穿透提高2%，最多计入60点超出的速度。」

This ships the FIRST half only: 「速度 ≥ 180 ⇒ 我方全体伤害 +20%」 -- unambiguous, and expressible with shipped pieces
(`self_attr:SPEED >= 180` in `when`, `MODIFY_ATTR ALL_DAMAGE_TYPE_BOOST` on `all_allies`).

The second half is registered, for two concrete reasons found while preparing it: (a) it targets 「昔涟与德谬歌」 and
德谬歌's relationship to her is not established yet, so the right `target` spelling is unknown; (b) with 2% per point and a
60-point count the ceiling is 120% penetration, which is what the document says but is large enough that writing it without
being able to state the target would be guessing.

Fired on TURN_START so it tracks her speed (a `BATTLE_START` firing would bake the value once); measured, her own Lv80
speed is 101, so the judge raises it with a hand-built `MODIFY_ATTR{SPEED}` rule appended through `TriggerTable.plus`.
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
        "target": "all_allies",
    }],
    "source": ("1415 昔涟 行迹 三相的因果 (1415103): "
               "「昔涟的速度大于等于 **180** 点时，"
               "我方全体造成的伤害提高 **20%**」"),
    "note": ("⭐ 只做这一半 ✓（另一半——“之后每超过 1 点速度，"
             "昔涟与**德谬歌**的冰属性抗性穿透提高 2%，最多计入 60 点”✗）"
             "—— 因为它的 `target` 写法取决于**德谬歌与昔涟的关系** ✗（尚未查清 ✓）。"
             "⭐ 触发写 TURN_START ✓（不是 BATTLE_START ✗，后者会把值烘死一次 ✗）。"
             "⚠ 已测：她 Lv80 速度 = **101** ✓ ⇒ 判据必须先抬速度 ✓。"),
})
out = doc if isinstance(doc, dict) else {"rules": rules}
if isinstance(doc, dict):
    doc["rules"] = rules
    out = doc
else:
    out = rules
json.dump(out, io.open(DATA, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   1415.json: the speed-180 party damage rule")

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
 * 「昔涟的速度大于等于 180 点时，我方全体造成的伤害提高 20%」 (1415:823, 2026-10-02).
 *
 * <p>⭐ The threshold is the thing under test, so the judge raises her speed with a hand-built rule APPENDED to hers
 * (`TriggerTable.plus`) and fires her own TURN_START: below 180 the rule must not fire, at 180+ it must.
 */
public class CyreneSpeedThresholdTest {
    private static final int OWNER = 1415;
    private static final int MONSTER = 1002011;

    /** ⭐ Below the threshold nothing happens; at or above it the party gains 20%. */
    @Test
    public void theThresholdGatesThePartyBoost() {
        double speed = CharacterFactory.create(OWNER, 80, false, null, null, 0)
                .getAttribute(AttributeType.SPEED).get();
        Assertions.assertEquals(101, speed, 1e-6, "measured: her Lv80 speed is 101");

        Assertions.assertEquals(0, boostWithExtraSpeed(0), 1e-9,
                "at 101 the rule must not fire");
        Assertions.assertEquals(0.2, boostWithExtraSpeed(100), 1e-6,
                "at 201 it must give the document's 20%");
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
        // her own rule is on TURN_START, so it has to be fired for her turn
        battle.fireTriggers(TriggerEvent.TURN_START, owner, owner, 0, 0);
        battle.processRequests();
        return owner.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get() - before;
    }
}
''')
print("ok   judge written")
