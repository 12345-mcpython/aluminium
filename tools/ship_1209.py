"""1209: her skill's +20% and her ult's +60% CRIT CHANCE must both count (2026-10-02).

Dumped from her file: `skill_soulsteel_sync_and_its_modifiers` (SKILL_CAST -> +20%, 1 turn) and
`ult_crit_chance_on_self` (ULT_CAST -> +60%, 1 turn). Same unit, same slot, two different sources, and NO state is needed to
reach either -- which is exactly what made `1401` unjudgeable and this one judgeable.

Both rules state `max_stacks: 2` rather than only the later one, because "later" depends on the player's order: measured,
the EARLIER writer's value does not matter, so stating it on both is order-proof and costs nothing.

File-driven, and the readings are the CLAIM: after the skill alone 0.2, and after the ult as well 0.8 -- not 0.6. That
second number is what a replaced modifier cannot produce, and it is what the mutation below must destroy.
ASCII only.
"""
import io
import json

DATA = "src/main/resources/characters/1209.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/CritChanceStacking1209Test.java"
IDS = ("skill_soulsteel_sync_and_its_modifiers", "ult_crit_chance_on_self")

doc = json.load(io.open(DATA, encoding="utf-8"))
rules = doc["rules"] if isinstance(doc, dict) else doc
touched = 0
for r in rules:
    if isinstance(r, dict) and r.get("id") in IDS:
        for s in r.get("do", []):
            if isinstance(s, dict) and s.get("attribute") == "CRIT_CHANCE":
                s["max_stacks"] = 2
                touched += 1
        r["note"] = ((r.get("note") or "") +
                     " \u2b50 2026-10-02\uff1a`\"max_stacks\": 2` \u2713 \u2014\u2014 \u540c\u4e00\u5355\u4f4d\u540c\u4e00\u69fd\u4e0a\u6709**\u4e24\u4e2a\u6765\u6e90**"
                     "\uff08\u6218\u6280 +20% \u2713 \u4e0e\u7ec8\u7ed3\u6280 +60% \u2713\uff09\u21d2 ⭐ \u6309\u5b9e\u6d4b\u673a\u5236\uff0c"
                     "**\u540e\u5199\u7684\u90a3\u6761\u5fc5\u987b\u53ef\u53e0\u52a0** \u2713\uff08`StatModifierBuff.isStackable()` \u2261 `maxStacks > 1` \u2713\uff09\uff0c"
                     "\u5426\u5219\u540e\u8005\u628a\u524d\u8005**\u66ff\u6362\u6389** \u2717\u3002\u26a0 \u4e24\u6761**\u90fd**\u5199 \u2713\uff0c\u56e0\u4e3a"
                     "\u201c\u8c01\u5728\u540e\u201d\u53d6\u51b3\u4e8e\u73a9\u5bb6\u987a\u5e8f \u2717\uff1b\u5b9e\u6d4b\u8fc7**\u65e9\u5199\u7684\u90a3\u6761\u5199\u591a\u5c11\u65e0\u6240\u8c13** \u2713\u3002")

print("attributes touched:", touched)
json.dump(doc, io.open(DATA, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   1209.json: both CRIT CHANCE writers state max_stacks 2")

io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Her skill's +20% and her ult's +60% CRIT CHANCE must both count (1209, 2026-10-02).
 *
 * <p>\u2b50 FILE-DRIVEN, and the numbers are the claim: 0.2 after the skill, 0.8 after the ult as well. A replaced modifier
 * gives 0.6 at the second reading, which is what the mutation has to produce.
 */
public class CritChanceStacking1209Test {
    private static final int OWNER = 1209;
    private static final int MONSTER = 1002011;

    /** \u2b50 Two sources, two shares, and the total. */
    @Test
    public void bothCritChanceSourcesAreCounted() {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        double base = owner.getAttribute(AttributeType.CRIT_CHANCE).get();

        battle.fireTriggers(TriggerEvent.SKILL_CAST, owner, owner, 0, 0);
        battle.processRequests();
        double afterSkill = owner.getAttribute(AttributeType.CRIT_CHANCE).get();

        battle.fireTriggers(TriggerEvent.ULT_CAST, owner, owner, 0, 0);
        battle.processRequests();
        double afterUlt = owner.getAttribute(AttributeType.CRIT_CHANCE).get();

        Assertions.assertEquals(base + 0.2, afterSkill, 1e-6, "the skill's 0.2 on top of her own value");
        Assertions.assertEquals(base + 0.8, afterUlt, 1e-6, "and the ult's own 0.6 must be counted, not replace it");
    }
}
''')
print("ok   judge written")
