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
JUDGE = "src/test/java/com/laosun/aluminium/test/YanqingCritChanceStackingTest.java"
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
                     " ⭐ 2026-10-02：`\"max_stacks\": 2` ✓ —— 同一单位同一槽上有**两个来源**"
                     "（战技 +20% ✓ 与终结技 +60% ✓）⇒ ⭐ 按实测机制，"
                     "**后写的那条必须可叠加** ✓（`StatModifierBuff.isStackable()` ≡ `maxStacks > 1` ✓），"
                     "否则后者把前者**替换掉** ✗。⚠ 两条**都**写 ✓，因为"
                     "“谁在后”取决于玩家顺序 ✗；实测过**早写的那条写多少无所谓** ✓。")

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
 * <p>⭐ FILE-DRIVEN, and the numbers are the claim: 0.2 after the skill, 0.8 after the ult as well. A replaced modifier
 * gives 0.6 at the second reading, which is what the mutation has to produce.
 */
public class YanqingCritChanceStackingTest {
    private static final int OWNER = 1209;
    private static final int MONSTER = 1002011;

    /** ⭐ Two sources, two shares, and the total. */
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
