"""1408's transformation, first two documented halves (2026-10-02).

Document, verbatim (1408_白厄.html):
  * :77 「变身为卡厄斯兰那，变身期间展开境界【时墟铁墓】…」
  * :90 「卡厄斯兰那的天赋。**变身时获得 4 点【毁伤】**。…」

This ships only what the document states outright and the engine can already express: the transformation becomes a STATE
(permanent, because its end is not a duration -- :120 says the LAST countdown turn ends it, which is an explicit removal,
and item 20 of this arc made explicit removals announce STATE_ENDED), and 【毁伤】 is declared and granted at +4.

⚠ Both unknowns stay registered rather than guessed:
  * 【毁伤】's ceiling is not stated where the sentence is, so the resource is declared WITHOUT `max`;
  * the 8 countdown turns and the speed at 60% (:77) need either an op or a mechanic; and the end-of-transformation reward
    (「基于溢出点数获得【火种】」) needs an overflow counter above 火种's hard cap of 12.

Readers: the 【毁伤】 grant is read straight off her sentence; the state is what a later end-reader will hang on.
ASCII only.
"""
import io
import json

DATA = "src/main/resources/characters/1408.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/TransformationStartsTest.java"
WOUND = "\u6bc1\u4f24"
STATE = "\u53d8\u8eab"

doc = json.load(io.open(DATA, encoding="utf-8"))
rules = doc["rules"] if isinstance(doc, dict) else doc

for rule in rules:
    if isinstance(rule, dict) and rule.get("id") == "ult_transformation":
        raise SystemExit("already shipped")

rules.append({
    "on": "ULT_CAST",
    "id": "ult_transformation",
    "when": ["actor == self"],
    "do": [
        {"op": "APPLY_BUFF", "buff": STATE, "permanent": True, "target": "self"},
        {"op": "GAIN_RESOURCE", "resource": WOUND, "amount": 4},
    ],
    "source": ("1408 \u767d\u5384 \u7ec8\u7ed3\u6280\u4e0e\u5361\u5384\u65af\u5170\u90a3\u5929\u8d4b\uff08\u6587\u6863 `:77`\u3001`:90`\uff09\uff1a"
               "\u300c\u53d8\u8eab\u4e3a\u5361\u5384\u65af\u5170\u90a3\uff0c\u53d8\u8eab\u671f\u95f4\u5c55\u5f00\u5883\u754c\u3010\u65f6\u589f\u94c1\u5893\u3011\u300d\uff1b"
               "\u300c**\u53d8\u8eab\u65f6\u83b7\u5f97 4 \u70b9\u3010\u6bc1\u4f24\u3011**\u300d"),
    "note": ("\u2b50 2026-10-02\uff1a\u53d8\u8eab\u5efa\u6210**\u6c38\u4e45\u72b6\u6001** \u2713\uff08\u56e0\u4e3a\u5b83\u7684\u7ed3\u675f**\u4e0d\u662f\u65f6\u957f** \u2713\uff1a"
             "\u6587\u6863 `:120` \u8bf4\u662f\u300c\u6700\u540e 1 \u4e2a\u5012\u8ba1\u65f6\u56de\u5408\u2026\u7ed3\u675f\u53d8\u8eab\u300d\u2713\uff0c"
             "\u800c**\u663e\u5f0f\u79fb\u9664\u73b0\u5728\u4f1a\u516c\u544a `STATE_ENDED`** \u2713 \u2014\u2014 \u672c\u6bb5\u7b2c 20 \u4ef6 \u2713\uff09\u3002"
             "\u26a0 \u4e24\u4e2a\u672a\u77e5\u4ecd\u767b\u8bb0\uff1a**\u3010\u6bc1\u4f24\u3011\u4e0a\u9650\u672a\u7ed9** \u2717\uff1b"
             "**8 \u4e2a\u5012\u8ba1\u65f6\u56de\u5408\u4e0e\u901f\u5ea6 60%** \u2717\uff1b**\u7ed3\u675f\u65f6\u7684\u6ea2\u51fa\u5956\u52b1** \u2717\u3002"),
})

if isinstance(doc, dict):
    doc["rules"] = rules
    resources = doc.get("resources") or []
    if not any(isinstance(r, dict) and r.get("id") == WOUND for r in resources):
        resources.append({
            "id": WOUND,
            "initial": 0,
            "note": ("\u2b50 2026-10-02\uff08\u6587\u6863 `:90`\uff09\uff1a\u300c\u53d8\u8eab\u65f6\u83b7\u5f97 4 \u70b9\u3010\u6bc1\u4f24\u3011\u300d\u3002"
                     "\u26a0 **\u4e0a\u9650\u5728\u8be5\u53e5\u5904\u672a\u7ed9** \u2717 \u21d2 \u6545**\u4e0d\u58f0\u660e `max`** \u2713\uff08\u6309\u7eaa\u5f8b\u4e0d\u731c \u2713\uff09\u3002"),
        })
        doc["resources"] = resources
else:
    raise SystemExit("1408.json must be an object with resources")
json.dump(doc, io.open(DATA, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   1408.json: the transformation starts, and 【毁伤】 is declared")

io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.Skill;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1408 \u767d\u5384\uff1a\u300c\u53d8\u8eab\u4e3a\u5361\u5384\u65af\u5170\u90a3\u2026\u300d\u5341\u300c**\u53d8\u8eab\u65f6\u83b7\u5f97 4 \u70b9\u3010\u6bc1\u4f24\u3011**\u300d (2026-10-02).
 *
 * <p>\u2b50 FILE-DRIVEN: her ultimate puts the transformation state on and pays the 4 \u3010\u6bc1\u4f24\u3011 her own talent names. The
 * state is permanent on purpose -- its end is the last countdown turn (\u6587\u6863 :120), i.e. an explicit removal, not a duration.
 */
public class TransformationStartsTest {
    private static final int OWNER = 1408;
    private static final int MONSTER = 1002011;
    private static final String STATE = "\u53d8\u8eab";
    private static final String WOUND = "\u6bc1\u4f24";

    /** \u2b50 The ultimate starts the transformation and pays 4 \u3010\u6bc1\u4f24\u3011. */
    @Test
    public void theUltimateStartsTheTransformation() {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        Assertions.assertFalse(owner.getBuffManager().hasState(STATE), "precondition: not transformed yet");
        Skill ult = owner.getSkills().get(SkillType.ULTRA);
        Assertions.assertNotNull(ult, "precondition: she has an ultimate");
        SkillExecutor.execute(battle, ult, owner, List.of(owner));
        battle.processRequests();

        Assertions.assertTrue(owner.getBuffManager().hasState(STATE),
                "the transformation is on (got " + owner.getBuffManager().hasState(STATE) + ")");
        Assertions.assertEquals(4, owner.getResources().has(WOUND) ? owner.getResources().value(WOUND) : 0,
                "\u53d8\u8eab\u65f6\u83b7\u5f97 4 \u70b9\u3010\u6bc1\u4f24\u3011");
    }
}
''')
print("ok   judge written")
