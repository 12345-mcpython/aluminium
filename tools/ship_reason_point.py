"""1415's memosprite skill 18 「献予「理性」之诗」 -- the skill point and the immediate action (2026-10-02).

Verbatim (1141518, params [3, 0.2, 0.3, 1]): 「单次生效，对那刻夏施放时，<b>为我方恢复 #4 个战技点并使那刻夏立即行动</b>，使其战技的伤害次数增加 #1 次，持续1回合。…」

Both halves have shipped spellings, measured:
  * 「为我方恢复 #4 个战技点」 -> `GAIN_SKILL_POINT` (reader 1101.json: `{"op": "GAIN_SKILL_POINT", "amount": 1}`), and #4 is 1 at every level;
  * 「使那刻夏立即行动」      -> `ADVANCE` (reader 1101.json: `{"op": "ADVANCE", "percent": 1.0, "target": "target"}`).

那刻夏 is cid 1405 in our own data ("Anaxa").

⛔ Registered: 「使其战技的伤害次数增加 #1 次，持续1回合」 (a count of hits inside his skill) and the 【真知】 clause that follows.
"""
import io
import json
import os
import sys

ANAXA = "src/main/resources/characters/1405.json"
SE = "src/main/resources/data/skill_effects.json"
TB = "E:/turnbasedgamedata"
SLOT = 18

rows = json.load(io.open(TB + "/ExcelOutput/AvatarServantSkillConfig.json", encoding="utf-8"))
levels = [r for r in rows if r["SkillID"] == 1141518]
if not levels:
    sys.exit("REFUSING: no 1141518 rows")
points = {p.get("Value") for r in levels for i, p in enumerate(r.get("ParamList") or []) if i == 3}
if points != {1}:
    sys.exit("REFUSING: #4 is %s, not 1 at every level -- a literal would be an approximation" % points)
print("ok   #4 is 1 at all %d levels" % len(levels))

existed = os.path.exists(ANAXA)
if existed:
    doc = json.load(io.open(ANAXA, encoding="utf-8"))
    rules = doc if isinstance(doc, list) else doc.get("rules", [])
    print("ok   1405.json already exists with %d rules -- appending" % len(rules))
else:
    doc, rules = {"rules": []}, []
    print("ok   1405.json does not exist -- creating it")

RULE_ID = "memosprite_ode_of_reason_gives_a_point_and_advances_him"
if any(r.get("id") == RULE_ID for r in rules):
    sys.exit("REFUSING: %s is already there" % RULE_ID)

rules.append({
    "id": RULE_ID,
    "on": "CAST_SETUP",
    "when": ["target == self", "actor is_summon", "from_skill_id == " + str(SLOT)],
    "do": [
        {"op": "GAIN_SKILL_POINT", "amount": 1},
        {"op": "ADVANCE", "percent": 1.0, "target": "self"},
    ],
    "source": ("1415 昔涟 忆灵技能 18 「献予「理性」之诗」（数据槽位 18，SkillID 1141518）："
               "「单次生效，对那刻夏施放时，**为我方恢复 #4 个战技点并使那刻夏立即行动**。」"),
    "note": ("⭐ 两半都有**出货拼法**（**实测**）：`GAIN_SKILL_POINT`（读者 1101：`{\"amount\": 1}`）"
             "与 `ADVANCE`（读者 1101：`{\"percent\": 1.0, \"target\": \"target\"}`）。"
             "⭐ `#4` 在 1141518 的**十行里全是 1**（**实测**）。"),
})

if isinstance(doc, list):
    out = rules
else:
    doc["rules"] = rules
    out = doc
io.open(ANAXA, "w", encoding="utf-8", newline="\n").write(json.dumps(out, ensure_ascii=False, indent=2) + "\n")
print("ok   1405 now carries %s (%d rules)" % (RULE_ID, len(rules)))

effects = json.load(io.open(SE, encoding="utf-8"))
if str(SLOT) not in effects.get("11415", {}):
    effects.setdefault("11415", {})[str(SLOT)] = {
        "effect": "Rules",
        "source": "1415 昔涟 忆灵技能 18 「献予「理性」之诗」（数据槽位 18）：工作在规则侧。",
        "note": "⭐ 没有条目就不可交付。",
    }
    io.open(SE, "w", encoding="utf-8", newline="\n").write(json.dumps(effects, ensure_ascii=False, indent=2) + "\n")
    print("ok   skill_effects.json: 11415/%d = Rules" % SLOT)

JUDGE = '''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1415's memosprite skill 18 「献予「理性」之诗」: 「为我方恢复 #4 个战技点并使那刻夏立即行动」 (2026-10-02).
 *
 * <p>⭐ Two readings, one run: the team's skill points rise by #4 = 1, and his action value DROPS (which is what 「立即行动」 means). A rule that only granted
 * the point, or only advanced him, cannot pass both. Nothing is replaced.
 */
public class ReasonOdePointAndActionTest {
    private static final int LEVEL = 80;
    private static final int CYRENE = 1415;
    private static final int ANAXA = 1405;
    private static final int MONSTER = 1002011;

    @Test
    public void itGivesAPointAndMovesHimUp() {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character anaxa = CharacterFactory.create(ANAXA, LEVEL);
        Battle battle = new Battle(List.of(cyrene, anaxa),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        cyrene = battle.characters.get(0);
        anaxa = battle.characters.get(1);

        var demiurge = battle.summonServant(cyrene);
        battle.processRequests();
        var ode = demiurge.skillAt(18);
        Assertions.assertNotNull(ode, "precondition: the memosprite carries slot 18");

        double avBefore = anaxa.getActionValue();
        int pointsBefore = battle.skillPoints();
        SkillExecutor.execute(battle, ode, demiurge, List.of(anaxa));
        battle.processRequests();
        double avAfter = anaxa.getActionValue();
        int pointsAfter = battle.skillPoints();
        System.out.println("[reason] skill points " + pointsBefore + " -> " + pointsAfter
                + " ; his action value " + avBefore + " -> " + avAfter);

        Assertions.assertEquals(pointsBefore + 1, pointsAfter,
                "\\u300c\\u4e3a\\u6211\\u65b9\\u6062\\u590d #4 \\u4e2a\\u6218\\u6280\\u70b9\\u300d-- and #4 is 1 at every level");
        Assertions.assertTrue(avAfter < avBefore,
                "\\u300c\\u4f7f\\u90a3\\u523b\\u590f\\u7acb\\u5373\\u884c\\u52a8\\u300d-- his action value must come DOWN (" + avBefore + " -> " + avAfter + ")");
    }
}
'''
io.open("src/test/java/com/laosun/aluminium/test/ReasonOdePointAndActionTest.java", "w", encoding="utf-8", newline="\n").write(JUDGE)
print("ok   judge written")
