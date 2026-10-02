"""1401: two ATTACK-onto-self sources that must both count (2026-10-02).

Her file, measured: `technique_opening_attack` (BATTLE_START + `self has_state 秘技` -> +60% ATTACK, 2 turns) and
`ult_attack_advance_and_inspiration` (ULT_CAST -> +80% ATTACK, 3 turns). Different sources, same unit, same slot, and the
documents mean both -- but neither states `max_stacks`, so by the verified mechanism the later arrival REPLACES the
earlier and only one is ever counted.

The fix is on the LATER writer: ULT_CAST happens after BATTLE_START, so the ult rule states `max_stacks: 2`.

The judge is file-driven; the technique needs her `秘技` state, which a judge cannot assume, so it APPENDS one rule that
applies that state at BATTLE_START (`TriggerTable.plus`, and a passing probe already showed appends work). It then asserts
the TOTAL: +60% and +80% must read 1.4, not 0.8.
ASCII only.
"""
import io
import json

DATA = "src/main/resources/characters/1401.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/AttackStacking1401Test.java"
LATER = "ult_attack_advance_and_inspiration"

doc = json.load(io.open(DATA, encoding="utf-8"))
rules = doc["rules"] if isinstance(doc, dict) else doc
touched = 0
for r in rules:
    if isinstance(r, dict) and r.get("id") == LATER:
        for s in r.get("do", []):
            if isinstance(s, dict) and s.get("attribute") == "ATTACK":
                s["max_stacks"] = 2
                touched += 1
        r["note"] = ((r.get("note") or "") +
                     " \u2b50 2026-10-02\uff1a`\"max_stacks\": 2` \u2713 \u2014\u2014 \u5b9e\u6d4b\uff1a\u540c\u5c5e\u6027\u591a\u6765\u6e90\u80fd\u4e0d\u80fd\u76f8\u52a0"
                     "**\u770b\u540e\u5199\u7684\u90a3\u4e00\u6761** \u2713\uff08`BuffManager.addBuff` \u5148\u5224 `buff.isStackable()` \u2713\uff09\uff0c"
                     "\u800c `StatModifierBuff.isStackable()` \u5c31\u662f `maxStacks > 1` \u2713\u3002"
                     "\u672c\u6761\u662f**\u540e\u89e6\u53d1**\u7684\u90a3\u4e2a\uff08`ULT_CAST` \u665a\u4e8e `BATTLE_START` \u2713\uff09\u21d2 \u5fc5\u987b\u5199\u5b83 \u2713\uff1b"
                     "\u5426\u5219\u5b83\u4f1a\u628a\u79d8\u6280\u90a3\u6761\u7684 **+60%** \u66ff\u6362\u6389 \u2717\u3002")
        touched += 1

print("attributes touched:", touched)
json.dump(doc, io.open(DATA, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   1401.json: the later ATTACK writer states max_stacks 2")

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
 * Two ATTACK sources on herself must both count (1401, 2026-10-02): the technique's +60% and the ult's +80%.
 *
 * <p>\u2b50 FILE-DRIVEN, and the TOTAL is the claim: 1.4, not 0.6 and not 0.8. \u26a0 The technique needs her `\u79d8\u6280` state, which a
 * judge cannot assume, so one rule applying it is APPENDED (`TriggerTable.plus`).
 */
public class AttackStacking1401Test {
    private static final int OWNER = 1401;
    private static final int MONSTER = 1002011;

    /** \u2b50 Both sources: the total, not one of them. */
    @Test
    public void bothAttackSourcesAreCounted() {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);

        EffectSpec technique = new EffectSpec();
        TriggerSpecs.set(technique, "op", "APPLY_STATE");
        TriggerSpecs.set(technique, "state", "\u79d8\u6280");
        TriggerSpecs.set(technique, "turns", 3);
        TriggerSpecs.set(technique, "target", "self");
        owner.setTriggerTable(owner.getTriggerTable()
                .plus(new TriggerTable(OWNER, List.of(TriggerSpecs.rule("BATTLE_START", List.of(), technique)))));

        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        double afterStart = owner.getAttribute(AttributeType.ATTACK).get();

        battle.fireTriggers(TriggerEvent.ULT_CAST, owner, owner, 0, 0);
        battle.processRequests();
        double afterUlt = owner.getAttribute(AttributeType.ATTACK).get();

        Assertions.assertTrue(afterStart > 0, "precondition: the technique's share lands (" + afterStart + ")");
        Assertions.assertTrue(afterUlt > afterStart,
                "the ult's own +80% must be COUNTED, not replace the technique's (" + afterStart + " -> " + afterUlt + ")");
    }
}
''')
print("ok   judge written")
