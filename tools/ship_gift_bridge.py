"""Ship the bridge, with the trap that held it up removed (2026-10-02, closing ①-a).

Measured over three probes: a content effect carrying `"maxStacks"` (a Java name) is accepted by the key guard and then DROPPED
by Gson, which only knows `@SerializedName("max_stacks")` -- so the stackable state's cap became 1 and exactly one instance was
attached. With `"max_stacks"` the same effect repeats (probe: 4 of 4, green).

So this script:
  * removes the temporary probe from 1513's file;
  * writes the reward the way the sentence states it -- a stackable 【好活当赏】 whose layer count is the 【笑点】 spent, capped by
    the cap 【笑点】 itself declares;
  * writes the end-to-end judge (her skill grants the laughs, an Aha moment ends, the gift carries them, and 1505 takes half).
"""
import io
import json
import sys

CHAR = "src/main/resources/characters/1513.json"
RULE_ID = "elation_moment_reward"
PROBE_ID = "probe_turn_start_stacks"

with io.open(CHAR, encoding="utf-8") as handle:
    doc = json.load(handle)
before = len(doc["rules"])
doc["rules"] = [rule for rule in doc["rules"]
                if not (isinstance(rule, dict) and rule.get("id") == PROBE_ID)]
if len(doc["rules"]) != before - 1:
    sys.exit("REFUSING: the probe was not found")
print("ok   the temporary probe is gone")

rule = next(entry for entry in doc["rules"] if isinstance(entry, dict) and entry.get("id") == RULE_ID)
LAUGHS_MAX = next(entry.get("max") for entry in doc["resources"] if entry.get("id") == "\u7b11\u70b9")
rule["do"] = [{
    "op": "APPLY_BUFF",
    "buff": "\u597d\u6d3b\u5f53\u8d4f",
    "turns": 2,
    "target": "self",
    "stackable": True,
    "max_stacks": LAUGHS_MAX,
    "scale": "party_resource:\u7b11\u70b9",
    "percent": 1.0,
}]
rule["note"] = (
    "\u300c\u963f\u54c8\u65f6\u523b\u7ed3\u675f\u65f6\uff0c\u4f7f\u53c2\u6f14\u7684\u89d2\u8272\u83b7\u5f97\u672c\u6b21\u8ba1\u5165\u7b11\u70b9\u7684\u3010\u597d\u6d3b\u5f53\u8d4f\u3011\u72b6\u6001\uff0c\u6301\u7eed 2 \u56de\u5408\u300d\u2713 \u21d2 "
    "`STATE_ENDED[\"\u81ea\u5df1\u7684\u963f\u54c8\u65f6\u523b\"]` \u21d2 ⭐ **\u53ef\u53e0\u52a0\u72b6\u6001** \u2713\uff0c**\u5c42\u6570\uff1d\u7b11\u70b9**\uff1a"
    "`scale: party_resource:\u7b11\u70b9` \u00d7 1 \u2713\uff08\u7b2c 69\uff0b70 \u4ef6\u7684\u4e24\u4e2a\u96f6\u4ef6 \u2713\uff09\u3002"
    "\u26a0 **\u4e0a\u9650\u7528\u3010\u7b11\u70b9\u3011\u81ea\u5df1\u58f0\u660e\u7684\u4e0a\u9650** \u2713\uff08\u4e0d\u81c6\u9020\u6570\u5b57 \u2713\uff09\u3002"
    "\u26a0\u26a0 **\u5751\uff08\u4e09\u6b21\u63a2\u9488\u624d\u91cf\u51fa \u2713\uff09**\uff1a\u5199 `maxStacks`\uff08Java \u540d \u2717\uff09\u4f1a\u88ab\u952e\u5b88\u536b\u63a5\u53d7\u3001"
    "\u7136\u540e\u88ab Gson **\u9759\u9ed8\u4e22\u5f03** \u2717\uff08\u5b83\u53ea\u8ba4 `@SerializedName(\"max_stacks\")` \u2713\uff09\u21d2 cap \u53d8\u6210 **1** \u21d2 \u53ea\u52a0 **1** \u5c42 \u2717\u3002"
    "\u26a0 \u540c\u53e5\u300c\u963f\u54c8\u884c\u52a8\u540e\u4f1a\u6d88\u8017\u5168\u90e8\u7b11\u70b9\u300d\uff08\u6d88\u8017 \u2717\uff09\u4ecd\u672a\u5199 \u2713\u3002"
)
with io.open(CHAR, "w", encoding="utf-8", newline="\n") as handle:
    json.dump(doc, handle, ensure_ascii=False, indent=2)
    handle.write("\n")
print("ok   the reward carries the laughs, spelled the way the files spell it")

JUDGE = "src/test/java/com/laosun/aluminium/test/GiftCarriesTheLaughsTest.java"
io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.buff.StateBuff;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * The gift carries the 【笑点】 count, and 1505 takes half of it (2026-10-02) -- objective ①-a's whole chain.
 *
 * <p>Content only: 1513's skill grants 4 【笑点】, an 【阿哈时刻】 ends, her reward applies the gift with FOUR instances, and ending
 * it pays 1505 half of four. The cap is spelled `max_stacks`, which is the trap this reading was written around: the Java name
 * is accepted by the key guard and then dropped by Gson, leaving one instance.
 */
public class GiftCarriesTheLaughsTest {
    private static final int SPARKLE = 1513;
    private static final int EVANESCIA = 1505;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final String LAUGHS = "\\u7b11\\u70b9";
    private static final String GIFT = "\\u597d\\u6d3b\\u5f53\\u8d4f";
    private static final String MOMENT = "\\u963f\\u54c8\\u65f6\\u523b";

    /** Four laughs become four instances, and half of them become hers. */
    @Test
    public void theGiftCarriesTheLaughsAndSheTakesHalf() {
        Character sparkle = CharacterFactory.create(SPARKLE, LEVEL, false, null, null, 0);
        Character evanescia = CharacterFactory.create(EVANESCIA, LEVEL, false, null, null, 0);
        Enemy victim = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(sparkle, evanescia), List.of(victim), new Random(0));
        battle.startBattle();
        battle.processRequests();

        battle.castImmediate(sparkle.getSkills().get(SkillType.SKILL), sparkle, List.of(victim));
        battle.processRequests();
        int laughs = battle.partyResourceValue(LAUGHS);
        System.out.println("[gift-laughs] laughs=" + laughs);
        Assertions.assertEquals(4, laughs, "precondition: the skill grants four");

        sparkle.getBuffManager().addBuff(new StateBuff(MOMENT, 1));
        advance(battle);
        int instances = sparkle.getBuffManager().stacksOf(GIFT);
        System.out.println("[gift-laughs] gift instances=" + instances);
        Assertions.assertEquals(4, instances, "the state carries one instance per laugh");

        int before = evanescia.getResources().value(GIFT);
        sparkle.getBuffManager().removeState(GIFT);
        battle.processRequests();
        int after = evanescia.getResources().value(GIFT);
        System.out.println("[gift-laughs] hers " + before + " -> " + after);
        Assertions.assertEquals(before + 2, after, "half of four becomes hers");
    }

    private static void advance(Battle battle) {
        Signal signal = battle.queue.snapshot().stream()
                .filter(candidate -> candidate.getCanHit() == battle.allies.getFirst()).findFirst()
                .orElseThrow(() -> new AssertionError("precondition: a unit is in the queue"));
        battle.currentMove = signal;
        battle.beforeMove();
        battle.afterMove();
        battle.processRequests();
    }
}
''')
print("ok   judge written")
