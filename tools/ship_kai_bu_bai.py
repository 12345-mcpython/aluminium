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
    "when": ["actor state_ended 好活当赏"],
    "do": [{"op": "GAIN_RESOURCE", "resource": "好活当赏",
            "amountFromEvent": True, "amountPercent": 0.5, "target": "self"}],
    "source": "1505 绯英 行迹 开不败: 「队友持有的【好活当赏】**结束时**，绯英会**将其中的 50%** 转化为自身的【好活当赏】」",
    "note": "「队友持有的【好活当赏】结束时…取其中的 50%」⇒ `STATE_ENDED` ⇒ "
            "`GAIN_RESOURCE{好活当赏, amountFromEvent × 0.5, target: self}` ✓。"
            "⚠ 条件用 `actor state_ended 好活当赏` ✓（事件以 `(carrier, carrier, …)` 触发 ✓ ⇒ `actor` 就是持有者 ✓）；"
            "⚠ 事件携带的**量**（本轮引擎改动 ✓）就是被结束状态的**实例数** ✓。"
            "⚠ **登记**：`1513` 的奖励目前仍以**普通状态**（不可叠加 ✗）施加 好活当赏 ✗ "
            "⇒ 它载的是**一个**实例而不是阿哈时刻的「**笑点**」数 ✗ ⇒ 真实对局里「50% of it」是 **50% × 1** ✗。"
            "本条只负责**句子本身** ✓；那桥接（笑点 → 状态层数）单独登记 ✓。",
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
