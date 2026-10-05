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
STATE = "变身"

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
    "source": ("1408 白厄 终结技（文档 `:77`）："
               "「**变身为卡厄斯兰那**，变身期间展开境界【时墟铁墓】」"),
    "note": ("⭐ 2026-10-02：变身建成**永久状态** ✓（因为它的结束**不是时长** ✓："
             "文档 `:120` 说是「最后 1 个倒计时回合…结束变身」 ✓，"
             "而**显式移除现在会公告 `STATE_ENDED`** ✓ —— 本段第 20 件 ✓）。"
             "⚠ **已登记、本条不猜** ✗：【毁伤】的上限（它是 "
             "`SetPhainonChargePoint`，上限是**编译表达式** ✗）；**8 个倒计时回合与速度 60%** ✗；"
             "**【毁伤】门槛换额外回合** ✗；**结束时的溢出奖励** ✗。"),
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
 * 1408 白厄：「**变身为卡厄斯兰那**，变身期间展开境界【时墟铁墓】」 (2026-10-02).
 *
 * <p>⭐ FILE-DRIVEN, and only the half the document states with no number in it: her ultimate puts the transformation STATE
 * on. It is permanent because its end is the last countdown turn (文档 :120) -- an explicit removal, which this arc made
 * announce `STATE_ENDED`, so an end-reader now has something to hang on.
 */
public class TransformationStartsTest {
    private static final int OWNER = 1408;
    private static final int MONSTER = 1002011;
    private static final String STATE = "变身";

    /** ⭐ The ultimate starts the transformation. */
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
