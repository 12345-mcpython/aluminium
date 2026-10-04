"""1505's 「开不败」 (2026-10-02, objective ①-a's clause).

The sentence, verbatim: 「**开不败**：队友持有的【好活当赏】**结束时**，绯英会**将其中的 50%** 转化为自身的【好活当赏】。」

Measured before writing:
  * the moment exists and now carries the magnitude (this round's engine patch: one announcement per sweep, with the total);
  * HER model is the resource -- all three of her riders gate on `self_resource:好活当赏 >= 1` -- so the clause's result is a
    resource gain on herself, and `amountFromEvent` + `amountPercent` are both spellings her own file already uses;
  * the state's carrier is the event's `actor` (the event fires `(carrier, carrier, …)`).

What is NOT done here, and is registered instead: 1513's reward still applies 【好活当赏】 as an ordinary 2-turn state, so it
carries ONE instance rather than the Aha moment's 笑点 count -- 「50% of it」 is therefore 50% of 1 in real play. The rule below is
the clause; the count it reads is the state's instance count.
"""
import io
import json
import sys

CHAR = "src/main/resources/characters/1505.json"
RULE_ID = "kai_bu_bai_half_of_a_fallen_gift"

with io.open(CHAR, encoding="utf-8") as handle:
    doc = json.load(handle)
rules = doc["rules"] if isinstance(doc, dict) and "rules" in doc else doc
if any(isinstance(rule, dict) and rule.get("id") == RULE_ID for rule in rules):
    sys.exit("REFUSING: the rule is already there")
rules.append({
    "on": "STATE_ENDED",
    "id": RULE_ID,
    "when": ["actor state_ended \u597d\u6d3b\u5f53\u8d4f"],
    "do": [{"op": "GAIN_RESOURCE", "resource": "\u597d\u6d3b\u5f53\u8d4f",
            "amountFromEvent": True, "amountPercent": 0.5, "target": "self"}],
    "source": "1505 \u7eef\u82f1 \u884c\u8ff9 \u5f00\u4e0d\u8d25: \u300c\u961f\u53cb\u6301\u6709\u7684\u3010\u597d\u6d3b\u5f53\u8d4f\u3011**\u7ed3\u675f\u65f6**\uff0c\u7eef\u82f1\u4f1a**\u5c06\u5176\u4e2d\u7684 50%** \u8f6c\u5316\u4e3a\u81ea\u8eab\u7684\u3010\u597d\u6d3b\u5f53\u8d4f\u3011\u300d",
    "note": "\u300c\u961f\u53cb\u6301\u6709\u7684\u3010\u597d\u6d3b\u5f53\u8d4f\u3011\u7ed3\u675f\u65f6\u2026\u53d6\u5176\u4e2d\u7684 50%\u300d\u21d2 `STATE_ENDED` \u21d2 "
            "`GAIN_RESOURCE{\u597d\u6d3b\u5f53\u8d4f, amountFromEvent \u00d7 0.5, target: self}` \u2713\u3002"
            "\u26a0 \u6761\u4ef6\u7528 `actor state_ended \u597d\u6d3b\u5f53\u8d4f` \u2713\uff08\u4e8b\u4ef6\u4ee5 `(carrier, carrier, \u2026)` \u89e6\u53d1 \u2713 \u21d2 `actor` \u5c31\u662f\u6301\u6709\u8005 \u2713\uff09\uff1b"
            "\u26a0 \u4e8b\u4ef6\u643a\u5e26\u7684**\u91cf**\uff08\u672c\u8f6e\u5f15\u64ce\u6539\u52a8 \u2713\uff09\u5c31\u662f\u88ab\u7ed3\u675f\u72b6\u6001\u7684**\u5b9e\u4f8b\u6570** \u2713\u3002"
            "\u26a0 **\u767b\u8bb0**\uff1a`1513` \u7684\u5956\u52b1\u76ee\u524d\u4ecd\u4ee5**\u666e\u901a\u72b6\u6001**\uff08\u4e0d\u53ef\u53e0\u52a0 \u2717\uff09\u65bd\u52a0 \u597d\u6d3b\u5f53\u8d4f \u2717 "
            "\u21d2 \u5b83\u8f7d\u7684\u662f**\u4e00\u4e2a**\u5b9e\u4f8b\u800c\u4e0d\u662f\u963f\u54c8\u65f6\u523b\u7684\u300c**\u7b11\u70b9**\u300d\u6570 \u2717 \u21d2 \u771f\u5b9e\u5bf9\u5c40\u91cc\u300c50% of it\u300d\u662f **50% \u00d7 1** \u2717\u3002"
            "\u672c\u6761\u53ea\u8d1f\u8d23**\u53e5\u5b50\u672c\u8eab** \u2713\uff1b\u90a3\u6865\u63a5\uff08\u7b11\u70b9 \u2192 \u72b6\u6001\u5c42\u6570\uff09\u5355\u72ec\u767b\u8bb0 \u2713\u3002",
})
with io.open(CHAR, "w", encoding="utf-8", newline="\n") as handle:
    json.dump(doc, handle, ensure_ascii=False, indent=2)
    handle.write("\n")
print("ok   added %s to 1505.json" % RULE_ID)

JUDGE = "src/test/java/com/laosun/aluminium/test/KaiBuBaiTest.java"
io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.buff.StackableStateBuff;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 「开不败」: half of a teammate's ended 【好活当赏】 (2026-10-02).
 *
 * <p>Driven with content, because a hand-built TABLE never receives STATE_ENDED (measured earlier) -- but a state applied
 * through the manager does announce its own end, and this rule lives in 1505's own file.
 *
 * <p>The gift is applied as FOUR instances, so half of it is the whole number 2 -- the arithmetic the sentence promises,
 * with no rounding to hide behind.
 */
public class KaiBuBaiTest {
    private static final int EVANESCIA = 1505;
    private static final int TEAMMATE = 1513;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final String GIFT = "\\u597d\\u6d3b\\u5f53\\u8d4f";

    /** Four instances end, and she converts half of them. */
    @Test
    public void halfOfTheEndedGiftBecomesHers() {
        Character evanescia = CharacterFactory.create(EVANESCIA, LEVEL, false, null, null, 0);
        Character teammate = CharacterFactory.create(TEAMMATE, LEVEL, false, null, null, 0);
        Enemy victim = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(evanescia, teammate), List.of(victim), new Random(0));
        battle.startBattle();
        battle.processRequests();

        int before = evanescia.getResources().value(GIFT);
        for (int i = 0; i < 4; i++) {
            teammate.getBuffManager().addBuff(new StackableStateBuff(GIFT, 9, true, 99));
        }
        Assertions.assertEquals(4, teammate.getBuffManager().stacksOf(GIFT), "precondition: four instances");

        int removed = teammate.getBuffManager().removeState(GIFT);
        battle.processRequests();
        int after = evanescia.getResources().value(GIFT);
        System.out.println("[kai-bu-bai] removed=" + removed + " hers before=" + before + " after=" + after);
        Assertions.assertEquals(4, removed, "the sweep takes all four");
        Assertions.assertEquals(before + 2, after, "and half of four -- the whole number 2 -- becomes hers");
    }
}
''')
print("ok   judge written")
