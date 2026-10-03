"""1408's transformation state, and only that (2026-10-02, revised).

Document, verbatim (1408_白厄.html:77): 「**变身为卡厄斯兰那**，变身期间展开境界【时墟铁墓】…」

Why this ships alone. The same talent also says 「变形时获得 4 点【毁伤】」 (…:90) and 【毁伤】 turned out to be
`RPG.GameCore.SetPhainonChargePoint` whose ceiling is a COMPILED expression (`FixedValues: []`, one dynamic hash) -- the
same wall as 1412's `#2`. A resource declaration cannot omit its ceiling (the loader really does refuse an incomplete
`ResourceSpec`, measured), so 【毁伤】 is registered instead of guessed, and this ships the half the document states with no
number at all: the transformation is a STATE.

It is `permanent: true` on purpose: :120 says the LAST countdown turn ends the transformation, i.e. an explicit removal --
and item 20 of this arc made explicit removals announce `STATE_ENDED`, so a later end-reader has something to hang on.

Registered, not guessed: 【毁伤】's ceiling, the 8 countdown turns and the 60% speed (:77), the extra turn at the 【毁伤】
threshold (:15232700682284445042), and the end reward 「基于溢出点数获得【火种】」 (needs an overflow counter above
火种's hard cap of 12).
ASCII only.
"""
import io
import json

DATA = "src/main/resources/characters/1408.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/TransformationStartsTest.java"
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
    ],
    "source": ("1408 \u767d\u5384 \u7ec8\u7ed3\u6280\uff08\u6587\u6863 `:77`\uff09\uff1a"
               "\u300c**\u53d8\u8eab\u4e3a\u5361\u5384\u65af\u5170\u90a3**\uff0c\u53d8\u8eab\u671f\u95f4\u5c55\u5f00\u5883\u754c\u3010\u65f6\u589f\u94c1\u5893\u3011\u300d"),
    "note": ("\u2b50 2026-10-02\uff1a\u53d8\u8eab\u5efa\u6210**\u6c38\u4e45\u72b6\u6001** \u2713\uff08\u56e0\u4e3a\u5b83\u7684\u7ed3\u675f**\u4e0d\u662f\u65f6\u957f** \u2713\uff1a"
             "\u6587\u6863 `:120` \u8bf4\u662f\u300c\u6700\u540e 1 \u4e2a\u5012\u8ba1\u65f6\u56de\u5408\u2026\u7ed3\u675f\u53d8\u8eab\u300d \u2713\uff0c"
             "\u800c**\u663e\u5f0f\u79fb\u9664\u73b0\u5728\u4f1a\u516c\u544a `STATE_ENDED`** \u2713 \u2014\u2014 \u672c\u6bb5\u7b2c 20 \u4ef6 \u2713\uff09\u3002"
             "\u26a0 **\u5df2\u767b\u8bb0\u3001\u672c\u6761\u4e0d\u731c** \u2717\uff1a\u3010\u6bc1\u4f24\u3011\u7684\u4e0a\u9650\uff08\u5b83\u662f "
             "`SetPhainonChargePoint`\uff0c\u4e0a\u9650\u662f**\u7f16\u8bd1\u8868\u8fbe\u5f0f** \u2717\uff09\uff1b**8 \u4e2a\u5012\u8ba1\u65f6\u56de\u5408\u4e0e\u901f\u5ea6 60%** \u2717\uff1b"
             "**\u3010\u6bc1\u4f24\u3011\u95e8\u69db\u6362\u989d\u5916\u56de\u5408** \u2717\uff1b**\u7ed3\u675f\u65f6\u7684\u6ea2\u51fa\u5956\u52b1** \u2717\u3002"),
})

if not isinstance(doc, dict):
    raise SystemExit("1408.json must be an object")
doc["rules"] = rules
json.dump(doc, io.open(DATA, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   1408.json: the transformation state ships on its own")

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
 * 1408 \u767d\u5384\uff1a\u300c**\u53d8\u8eab\u4e3a\u5361\u5384\u65af\u5170\u90a3**\uff0c\u53d8\u8eab\u671f\u95f4\u5c55\u5f00\u5883\u754c\u3010\u65f6\u589f\u94c1\u5893\u3011\u300d (2026-10-02).
 *
 * <p>\u2b50 FILE-DRIVEN, and only the half the document states with no number in it: her ultimate puts the transformation STATE
 * on. It is permanent because its end is the last countdown turn (\u6587\u6863 :120) -- an explicit removal, which this arc made
 * announce \u0060STATE_ENDED\u0060, so an end-reader now has something to hang on.
 */
public class TransformationStartsTest {
    private static final int OWNER = 1408;
    private static final int MONSTER = 1002011;
    private static final String STATE = "\u53d8\u8eab";

    /** \u2b50 The ultimate starts the transformation. */
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
    }
}
''')
print("ok   judge written")
