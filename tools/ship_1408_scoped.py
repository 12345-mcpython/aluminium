"""1408: 「卡厄斯兰那的物理属性抗性穿透提高 20%」 -- and the transformation's stats are bound to the state (2026-10-02, item 35).

Document, verbatim:
  * trace: 「卡厄斯兰那的**物理属性抗性穿透提高 20%**。」 -- 卡厄斯兰那 IS the transformed form, so the sentence is scoped to the
    transformation. The tree already maps 「抗性穿透」 to `DAMAGE_PENETRATION` (shipped for 1502 and for 1412's peerage, item 29).
  * the transformation's own block: 「**变身期间**攻击力提高 80%，生命上限提高 270%…」 -- "DURING the transformation".

\u26a0 THE DEFECT THIS ITEM FIXES (found while auditing, not while failing): item 34 appended the ATK/MaxHP modifiers WITHOUT linking
them to 【变身】, and a `MODIFY_ATTR` is an ordinary modifier -- removing the state would leave them behind, so "变身期间" would
quietly mean "for the rest of the battle". `MODIFY_ATTR` takes a `buff:` link (the shipped shape in 8006: a modifier that carries
`"buff": "伴舞"`), which is what ties a modifier's life to a state. All three effects here carry it, and the judge proves it by
removing the state and watching the numbers come back down.

Readers (2): the trace above, and the transformation block -- one sentence needs the link, the other needs the 20%.
ASCII only.
"""
import io
import json

DATA = "src/main/resources/characters/1408.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/TransformationScopedStatsTest.java"
RULE = "ult_transformation"
STATE = "\u53d8\u8eab"

doc = json.load(io.open(DATA, encoding="utf-8"))
rules = doc["rules"] if isinstance(doc, dict) else doc

hit = [r for r in rules if isinstance(r, dict) and r.get("id") == RULE]
if len(hit) != 1:
    raise SystemExit("expected exactly one transformation rule, found " + str(len(hit)))

rule = hit[0]
kept = [e for e in (rule.get("do") or [])
        if not (isinstance(e, dict) and e.get("attribute") in ("ATTACK", "HEALTH", "DAMAGE_PENETRATION"))]
kept.append({"op": "MODIFY_ATTR", "attribute": "ATTACK", "percent": 0.80,
             "permanent": True, "buff": STATE, "target": "self"})
kept.append({"op": "MODIFY_ATTR", "attribute": "HEALTH", "percent": 2.70,
             "permanent": True, "buff": STATE, "target": "self"})
kept.append({"op": "MODIFY_ATTR", "attribute": "DAMAGE_PENETRATION", "percent": 0.20,
             "permanent": True, "buff": STATE, "target": "self"})
rule["do"] = kept
rule["source"] = ((rule.get("source") or "") +
                  "\n\u2b50 2026-10-02\uff08\u884c\u8ff9\uff09\uff1a\u300c\u5361\u5384\u65af\u5170\u90a3\u7684\u7269\u7406\u5c5e\u6027**\u6297\u6027\u7a7f\u900f\u63d0\u9ad8 20%**\u300d\u2713"
                  "\uff08\u26a0 \u5361\u5384\u65af\u5170\u90a3\u5c31\u662f**\u53d8\u8eab\u5f62\u6001** \u2713 \u21d2 \u672c\u53e5\u4f5c\u7528\u57df\u662f\u201c\u53d8\u8eab\u671f\u95f4\u201d \u2713\uff09")
rule["note"] = ((rule.get("note") or "") +
                "\n\u2b50 2026-10-02\uff1a\u4e09\u6761\u5c5e\u6027\u90fd**\u7ed1\u5230\u3010\u53d8\u8eab\u3011** \u2713\uff08`\"buff\": \"\u53d8\u8eab\"` \u2713 \u2014\u2014 "
                "\u26a0 \u7b2c 34 \u4ef6\u5f53\u65f6**\u6f0f\u4e86\u8fd9\u4e2a\u7ed1\u5b9a** \u2717 \u21d2 \u201c\u53d8\u8eab\u671f\u95f4\u201d\u4f1a\u9ed8\u9ed8\u53d8\u6210\u201c\u672c\u573a\u6218\u6597\u5269\u4e0b\u7684\u65f6\u95f4\u201d \u2717\uff09\u3002"
                "\u26a0 **\u4ecd\u767b\u8bb0**\uff1a\u540c\u53e5\u540e\u534a\u201c\u5355\u6b21\u65bd\u653e\u3010\u652f\u67f1\u2022\u6b7b\u661f\u5929\u88c1\u3011\u6d88\u8017\u3010\u6bc1\u4f24\u3011\u8fbe\u5230 **4** \u70b9\u65f6\u83b7\u5f97 1 \u4e2a\u989d\u5916\u56de\u5408\u201d \u2717\u3002")

if not isinstance(doc, dict):
    raise SystemExit("1408.json must be an object")
doc["rules"] = rules
json.dump(doc, io.open(DATA, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   1408.json: the transformation's stats are bound to the state")

io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
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
 * 1408\uff1a\u300c\u5361\u5384\u65af\u5170\u90a3\u7684\u7269\u7406\u5c5e\u6027**\u6297\u6027\u7a7f\u900f\u63d0\u9ad8 20%**\u300d\u4e0e\u300c**\u53d8\u8eab\u671f\u95f4**\u653b\u51fb\u529b\u63d0\u9ad8 80%\uff0c\u751f\u547d\u4e0a\u9650\u63d0\u9ad8 270%\u300d
 * (2026-10-02).
 *
 * <p>\u2b50 THE POINT IS THE THIRD ASSERTION: the block must live exactly as long as the state. Removing \u3010\u53d8\u8eab\u3011 and watching ATK
 * come back down is what tells "during the transformation" apart from "for the rest of the battle" -- and item 34 shipped
 * without that link, so this judge would have caught it.
 */
public class TransformationScopedStatsTest {
    private static final double EPS = 1e-9;
    private static final int OWNER = 1408;
    private static final int MONSTER = 1002011;
    private static final String STATE = "\u53d8\u8eab";

    /** \u2b50 Transformed: +80% ATK, +270% Max HP, +20% RES PEN. Un-transformed: none of them. */
    @Test
    public void theBlockIsWorthWhatTheSentencesSay() {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        double atk0 = owner.getAttribute(AttributeType.ATTACK).get();
        double hp0 = owner.getAttribute(AttributeType.HEALTH).get();
        double pen0 = owner.getAttribute(AttributeType.DAMAGE_PENETRATION).get();

        Skill ult = owner.getSkills().get(SkillType.ULTRA);
        SkillExecutor.execute(battle, ult, owner, List.of(owner));
        battle.processRequests();
        Assertions.assertTrue(owner.getBuffManager().hasState(STATE), "precondition: the transformation is on");

        Assertions.assertEquals(atk0 * 1.8, owner.getAttribute(AttributeType.ATTACK).get(), atk0 * 1e-9,
                "\u300c\u653b\u51fb\u529b\u63d0\u9ad8 80%\u300d");
        Assertions.assertEquals(hp0 * 3.7, owner.getAttribute(AttributeType.HEALTH).get(), hp0 * 1e-9,
                "\u300c\u751f\u547d\u4e0a\u9650\u63d0\u9ad8 270%\u300d");
        Assertions.assertEquals(pen0 + 0.20, owner.getAttribute(AttributeType.DAMAGE_PENETRATION).get(), EPS,
                "\u300c\u7269\u7406\u5c5e\u6027\u6297\u6027\u7a7f\u900f\u63d0\u9ad8 20%\u300d");
    }

    /** \u2b50\u2b50 "During the transformation" means the block dies with the state. */
    @Test
    public void theBlockDiesWithTheState() {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        double atk0 = owner.getAttribute(AttributeType.ATTACK).get();
        double pen0 = owner.getAttribute(AttributeType.DAMAGE_PENETRATION).get();

        Skill ult = owner.getSkills().get(SkillType.ULTRA);
        SkillExecutor.execute(battle, ult, owner, List.of(owner));
        battle.processRequests();
        Assertions.assertTrue(owner.getAttribute(AttributeType.ATTACK).get() > atk0, "precondition: the block is on");

        owner.getBuffManager().removeState(STATE);
        battle.processRequests();
        Assertions.assertFalse(owner.getBuffManager().hasState(STATE), "the transformation is off");
        Assertions.assertEquals(atk0, owner.getAttribute(AttributeType.ATTACK).get(), atk0 * 1e-9,
                "\u300c\u53d8\u8eab\u671f\u95f4\u300d-- the attack bonus must be gone with the state");
        Assertions.assertEquals(pen0, owner.getAttribute(AttributeType.DAMAGE_PENETRATION).get(), EPS,
                "and the RES PEN too");
    }
}
''')
print("ok   judge written")
