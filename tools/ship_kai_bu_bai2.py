"""1505's 「开不败」, with the engine finally carrying the number where the spelling reads it (round 1681).

Everything measured by now:
  * a `STATE_ENDED` rule in a CONTENT file does receive a teammate's state ending (proved with a constant: 20 -> 22);
  * `gainResource`'s `amountFromEvent` branch reads `ctx.amount() * amountPercent`, so the two spellings DO combine;
  * the magnitude was landing in the `hitCount` slot, so `ctx.amount()` was always 0 -- fixed this round.

The reading: four instances of 【好活当赏】 end on a teammate, and half of them -- the whole number 2 -- become hers. Four is
chosen so the 50% has no rounding to hide behind.
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
    sys.exit("REFUSING: already there")
rules.append({
    "on": "STATE_ENDED",
    "id": RULE_ID,
    "when": ["actor state_ended 好活当赏"],
    "do": [{"op": "GAIN_RESOURCE", "resource": "好活当赏",
            "amountFromEvent": True, "amountPercent": 0.5, "target": "self"}],
    "source": "1505 绯英 行迹 开不败: 「队友持有的【好活当赏】**结束时**，绯英会**将其中的 50%** 转化为自身的【好活当赏】」",
    "note": "「队友持有的【好活当赏】结束时…取其中的 **50%**」⇒ `STATE_ENDED` ⇒ "
            "`GAIN_RESOURCE{好活当赏, **amountFromEvent × 0.5**, target: self}` ✓。"
            "⚠ 条件 `actor state_ended 好活当赏` ✓（事件以 `(carrier, carrier, …)` 触发 ✓）。"
            "⚠ 事件携带的量（本轮）就是**被结束状态的实例数** ✓。"
            "⚠ **登记**：`1513` 的奖励仍以**普通状态**施加 好活当赏 ✗（不可叠加 ✗）"
            "⇒ 真实对局里它只有 **1** 个实例 ✗ ⇒ 「50% of it」在实战里是 0.5 ✗；"
            "那桥接（笑点 → 状态层数）单独登记 ✓。",
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
 * <p>Four instances end, so half of them is the whole number 2 and no rounding can hide behind the arithmetic. The state is
 * applied through the manager (a hand-built TABLE never receives STATE_ENDED -- measured), and the rule under test lives in
 * 1505's own content file.
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
        Assertions.assertEquals(before + 2, after, "half of four -- the whole number 2 -- becomes hers");
    }
}
''')
print("ok   judge written")
