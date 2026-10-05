"""1513's reward hands 【好活当赏】 the 【笑点】 it spent -- the last step of ①-a (2026-10-02).

The corpus: 「阿哈时刻结束时，使参演的角色获得本次计入笑点的【好活当赏】状态，持续 2 回合。**阿哈行动后会消耗全部笑点**」 and
「将本次阿哈时刻的笑点**计入该状态**」. So the state's INSTANCE COUNT is the 【笑点】 count, which is what 1505's 「开不败」 reads
when the state ends.

Measured before writing: the count can now come from the party counter (`party_resource:`, item 69) and a stackable state can
carry that many instances (item 70). The cap is the one 【笑点】 itself declares -- no invented number: 【笑点】 is declared
`max: 2147483647`, i.e. uncapped, and the state has to state a cap to be stackable.

The judge drives the whole chain: her skill grants 4 【笑点】, an 【阿哈时刻】 ends, the reward gives the gift FOUR instances, and
ending it pays 1505 half of four.
"""
import io
import json
import sys

CHAR = "src/main/resources/characters/1513.json"
RULE_ID = "elation_moment_reward"

with io.open(CHAR, encoding="utf-8") as handle:
    doc = json.load(handle)
rules = doc["rules"]
rule = next((entry for entry in rules if isinstance(entry, dict) and entry.get("id") == RULE_ID), None)
if rule is None:
    sys.exit("REFUSING: %s is not there" % RULE_ID)
if rule["do"][0].get("stackable"):
    sys.exit("REFUSING: already carries the count")

LAUGHS_MAX = next(entry.get("max") for entry in doc["resources"] if entry.get("id") == "笑点")
rule["do"] = [{
    "op": "APPLY_BUFF",
    "buff": "好活当赏",
    "turns": 2,
    "target": "self",
    "stackable": True,
    "maxStacks": LAUGHS_MAX,
    "scale": "party_resource:笑点",
    "percent": 1.0,
}]
rule["note"] = (
    "「阿哈时刻结束时，使参演的角色获得本次计入笑点的【好活当赏】状态，持续 2 回合」✓ ⇒ "
    "`STATE_ENDED[\"自己的阿哈时刻\"]` ⇒ ⭐ **可叠加状态** ✓，层数＝"
    "`scale: party_resource:笑点` × 1 ✓（第 69＋70 件：队级计数可当数值读 ✓、可叠加状态可一次施加 N 个实例 ✓）。"
    "⚠ **上限用【笑点】自己声明的上限** ✓（`max: 2147483647` ⇒ 无上限 ✓）—— "
    "不臆造数字 ✓；下游 `1505`「开不败」就是读这个层数取 50% ✓（第 68 件 ✓）。"
    "⚠ 同句「阿哈行动后会消耗全部笑点」（消耗 ✗）仍未写 ✓。"
)

with io.open(CHAR, "w", encoding="utf-8", newline="\n") as handle:
    json.dump(doc, handle, ensure_ascii=False, indent=2)
    handle.write("\n")
print("ok   the reward carries the count (cap = the one 笑点 declares)")

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
 * <p>Content only: 1513's skill grants 4 【笑点】, an 【阿哈时刻】 ends, her reward applies the gift with FOUR instances, and
 * ending it pays 1505 half of four.
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

        // her skill is the sentence that grants the laughs
        battle.castImmediate(sparkle.getSkills().get(SkillType.SKILL), sparkle, List.of(victim));
        battle.processRequests();
        int laughs = battle.partyResourceValue(LAUGHS);
        System.out.println("[gift-laughs] laughs=" + laughs);
        Assertions.assertEquals(4, laughs, "precondition: the skill grants four");

        // an Aha moment ends: applied here so the scene does not depend on her autocast firing
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
