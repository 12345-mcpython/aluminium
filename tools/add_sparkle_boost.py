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
    "source": ("1306 \u82b1\u706b \u6218\u6280 \u5f3a\u5316\u524d\uff08`:159`\uff09: "
               "\u300c\u5f53\u6211\u65b9\u76ee\u6807**\u6bcf\u6d88\u80171\u70b9\u6218\u6280\u70b9**\uff0c\u5219\u4f7f\u6211\u65b9\u5168\u4f53\u9020\u6210\u7684\u4f24\u5bb3\u63d0\u9ad8 "
               "**6.00%**\uff0c\u8be5\u6548\u679c\u6301\u7eed **2** \u56de\u5408\uff0c\u6700\u591a\u53ef\u53e0\u52a0 **3** \u5c42\u300d"),
    "note": ("\u2b50 \u7528\u7684\u662f**\u540c\u65e5\u51fa\u8d27**\u7684 `scale: \"event_amount\"` \u2713\uff08\u4fee\u9970\u7b26\u90a3\u4e00\u6863 \u2713\uff09"
             "\u21d2 **\u63d0\u9ad8\u91cf = \u82b1\u6389\u7684\u70b9\u6570 \u00d7 6%** \u2713\u3002"
             "\u26a0 \u89e6\u53d1\u662f**\u5df2\u6709**\u7684 `SKILL_POINT_SPENT` \u2713\uff0c**\u4e0d\u9700\u8981\u4efb\u4f55\u65b0\u8d44\u6e90** \u2713\u3002"
             "\u26a0 `target: \"all_allies\"` \u662f\u5185\u5bb9\u91cc\u7528\u4e86 **158** \u6b21\u7684\u300c\u6211\u65b9\u5168\u4f53\u300d\u62fc\u5199 \u2713\u3002"
             "\u26a0 \u300c\u6700\u591a\u53e0\u52a0 3 \u5c42\u300d\u5199\u6210 `max_stacks: 3` \u2713\uff08\u540c 1407 \u5929\u8d4b\u7684\u5199\u6cd5 \u2713\uff09\u3002"),
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
 * \u300c\u6211\u65b9\u76ee\u6807\u6bcf\u6d88\u80171\u70b9\u6218\u6280\u70b9\uff0c\u5219\u4f7f\u6211\u65b9\u5168\u4f53\u9020\u6210\u7684\u4f24\u5bb3\u63d0\u9ad8 6%\u300d (1306:159, 2026-10-02).
 *
 * <p>File-driven: the rule is hers, on the existing `SKILL_POINT_SPENT` event, and the magnitude follows what was spent.
 */
public class SkillPointBoostTest {
    private static final int OWNER = 1306;
    private static final int MONSTER = 1002011;

    /** \u2b50 Three points spent raise it three times as far as one, and the rule is hers (she shares the target). */
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
