"""1306's documented per-skill-point clause (2026-10-02), on the `event_amount` modifier shipped an hour ago.

Sentence (1306_花火.md:159): 「花火在场时，战技点上限额外增加2点。当我方目标每消耗1点战技点，则使我方全体造成的伤害提高
6.00%，该效果持续2回合，最多可叠加3层。」 -- the 强化前 half; the 强化后 half (:161) is the 【幻相】 variant and is left
registered.

Shape: `on: SKILL_POINT_SPENT` (an existing event), `MODIFY_ATTR` with `scale: "event_amount"`, `percent: 0.06` (the
document's 6.00%), `turns: 2`, `max_stacks: 3`, `target: "all_allies"` (the spelling content uses 158 times for 「我方全体」).

Judge: file-driven character, so the rule under test is hers; firing the event with 3 points must be 3x one point.
ASCII only.
"""
import io
import json

DATA = "src/main/resources/characters/1306.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/SkillPointBoostTest.java"
ID = "skill_point_spent_party_damage_up"

doc = json.load(io.open(DATA, encoding="utf-8"))
rules = doc["rules"] if isinstance(doc, dict) else doc
rules = [r for r in rules if not (isinstance(r, dict) and r.get("id") == ID)]
rules.append({
    "on": "SKILL_POINT_SPENT",
    "id": ID,
    "do": [{
        "op": "MODIFY_ATTR",
        "attribute": "ALL_DAMAGE_TYPE_BOOST",
        "scale": "event_amount",
        "percent": 0.06,
        "turns": 2,
        "max_stacks": 3,
        "target": "all_allies",
    }],
    "source": ("1306 花火 战技 强化前（`:159`）: "
               "「当我方目标**每消耗1点战技点**，则使我方全体造成的伤害提高 "
               "**6.00%**，该效果持续 **2** 回合，最多可叠加 **3** 层」"),
    "note": ("⭐ 用的是**同日出货**的 `scale: \"event_amount\"` ✓（修饰符那一档 ✓）"
             "⇒ **提高量 = 花掉的点数 × 6%** ✓。"
             "⚠ 触发是**已有**的 `SKILL_POINT_SPENT` ✓，**不需要任何新资源** ✓。"
             "⚠ `target: \"all_allies\"` 是内容里用了 **158** 次的「我方全体」拼写 ✓。"
             "⚠ 「最多叠加 3 层」写成 `max_stacks: 3` ✓（同 1407 天赋的写法 ✓）。"),
})
out = doc if isinstance(doc, dict) else {"rules": rules}
if isinstance(doc, dict):
    doc["rules"] = rules
    out = doc
else:
    out = rules
json.dump(out, io.open(DATA, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   1306.json: the per-skill-point party damage clause")

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
 * 「我方目标每消耗1点战技点，则使我方全体造成的伤害提高 6%」 (1306:159, 2026-10-02).
 *
 * <p>File-driven: the rule is hers, on the existing `SKILL_POINT_SPENT` event, and the magnitude follows what was spent.
 */
public class SkillPointBoostTest {
    private static final int OWNER = 1306;
    private static final int MONSTER = 1002011;

    /** ⭐ Three points spent raise it three times as far as one, and the rule is hers (she shares the target). */
    @Test
    public void thePartyBoostFollowsThePointsSpent() {
        double one = boostAfterSpending(1);
        double three = boostAfterSpending(3);
        Assertions.assertTrue(one > 0, "precondition: the clause lands (" + one + ")");
        Assertions.assertEquals(3 * one, three, one * 1e-6,
                "3 points is 3x one (" + one + " -> " + three + ")");
        Assertions.assertEquals(0, boostAfterSpending(0), 1e-9, "spending nothing changes nothing");
    }

    // ==================================================================

    private static double boostAfterSpending(int spent) {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        double before = owner.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        battle.fireTriggers(TriggerEvent.SKILL_POINT_SPENT, owner, owner, 0, spent);
        battle.processRequests();
        return owner.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get() - before;
    }
}
''')
print("ok   judge written")
