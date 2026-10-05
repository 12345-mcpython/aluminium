"""1415's memosprite skill 18 「献予「理性」之诗」 -- the skill point and the immediate action (2026-10-02).

Verbatim (1141518, params [3, 0.2, 0.3, 1]): 「单次生效，对那刻夏施放时，<b>为我方恢复 #4 个战技点并使那刻夏立即行动</b>，使其战技的伤害次数增加 #1 次，持续1回合。…」

Both halves have shipped spellings, measured:
  * 「为我方恢复 #4 个战技点」 -> `GAIN_SKILL_POINT` (reader 1101.json: `{"op": "GAIN_SKILL_POINT", "amount": 1}`), and #4 is 1 at every level;
  * 「使那刻夏立即行动」      -> `ADVANCE` (reader 1101.json: `{"op": "ADVANCE", "percent": 1.0, "target": "target"}`).

那刻夏 is cid 1405 in our own data ("Anaxa").

\u26d4 Registered: 「使其战技的伤害次数增加 #1 次，持续1回合」 (a count of hits inside his skill) and the 【真知】 clause that follows.
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
    "source": ("1415 \u6614\u6d9f \u5fc6\u7075\u6280\u80fd 18 \u300c\u732e\u4e88\u300c\u7406\u6027\u300d\u4e4b\u8bd7\u300d\uff08\u6570\u636e\u69fd\u4f4d 18\uff0cSkillID 1141518\uff09\uff1a"
               "\u300c\u5355\u6b21\u751f\u6548\uff0c\u5bf9\u90a3\u523b\u590f\u65bd\u653e\u65f6\uff0c**\u4e3a\u6211\u65b9\u6062\u590d #4 \u4e2a\u6218\u6280\u70b9\u5e76\u4f7f\u90a3\u523b\u590f\u7acb\u5373\u884c\u52a8**\u3002\u300d"),
    "note": ("\u2b50 \u4e24\u534a\u90fd\u6709**\u51fa\u8d27\u62fc\u6cd5**\uff08**\u5b9e\u6d4b**\uff09\uff1a`GAIN_SKILL_POINT`\uff08\u8bfb\u8005 1101\uff1a`{\"amount\": 1}`\uff09"
             "\u4e0e `ADVANCE`\uff08\u8bfb\u8005 1101\uff1a`{\"percent\": 1.0, \"target\": \"target\"}`\uff09\u3002"
             "\u2b50 `#4` \u5728 1141518 \u7684**\u5341\u884c\u91cc\u5168\u662f 1**\uff08**\u5b9e\u6d4b**\uff09\u3002"),
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
        "source": "1415 \u6614\u6d9f \u5fc6\u7075\u6280\u80fd 18 \u300c\u732e\u4e88\u300c\u7406\u6027\u300d\u4e4b\u8bd7\u300d\uff08\u6570\u636e\u69fd\u4f4d 18\uff09\uff1a\u5de5\u4f5c\u5728\u89c4\u5219\u4fa7\u3002",
        "note": "\u2b50 \u6ca1\u6709\u6761\u76ee\u5c31\u4e0d\u53ef\u4ea4\u4ed8\u3002",
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
 * 1415's memosprite skill 18 \u300c\u732e\u4e88\u300c\u7406\u6027\u300d\u4e4b\u8bd7\u300d: \u300c\u4e3a\u6211\u65b9\u6062\u590d #4 \u4e2a\u6218\u6280\u70b9\u5e76\u4f7f\u90a3\u523b\u590f\u7acb\u5373\u884c\u52a8\u300d (2026-10-02).
 *
 * <p>\u2b50 Two readings, one run: the team's skill points rise by #4 = 1, and his action value DROPS (which is what \u300c\u7acb\u5373\u884c\u52a8\u300d means). A rule that only granted
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
