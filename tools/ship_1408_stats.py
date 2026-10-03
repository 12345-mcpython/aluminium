"""1408: 「变身期间攻击力提高 80%，生命上限提高 270%」 (2026-10-02, item 34).

Document, verbatim (1408_白厄.html, 卡厄斯兰那的天赋): 「…变身期间**攻击力提高 80%**，**生命上限提高 270%**，施放攻击后回复等同于自身生命上限
20% 的生命值。」

Same shape as item 22 and item 29 (a `MODIFY_ATTR` riding the rule that grants the state), and every number is the document's own:
80 and 270. The transformation rule already exists and already rides `ULT_CAST` (`ult_transformation`, which also starts the
countdown), so this appends two effects beside them -- nothing new is invented.

\u26a0 NOT in this item, and registered instead (the audit of 1408 is in GAPS): 「施放攻击后回复 20% 生命上限」 (needs an
after-attack event rule, shippable but a separate sentence), 「免疫控制类负面状态」, 「变身时获得 4 点【毁伤】」, the 毁伤/弑魂之炽
suite, 「施放终结技时暴击伤害提高 50% 持续 3 回合」, 「卡厄斯兰那的物理属性抗性穿透提高 20%」, 「成为技能目标时获得 1 点【火种】」,
「队友施放时使其暴击伤害提高 30%」, and the 火种 overflow (「最多溢出 3 点」 ＋ 「变身结束时会基于溢出点数获得【火种】」).
ASCII only.
"""
import io
import json

DATA = "src/main/resources/characters/1408.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/TransformationStatsTest.java"
RULE = "ult_transformation"

doc = json.load(io.open(DATA, encoding="utf-8"))
rules = doc["rules"] if isinstance(doc, dict) else doc

hit = [r for r in rules if isinstance(r, dict) and r.get("id") == RULE]
if len(hit) != 1:
    raise SystemExit("expected exactly one transformation rule, found " + str(len(hit)))

rule = hit[0]
kept = [e for e in (rule.get("do") or [])
        if not (isinstance(e, dict) and e.get("attribute") in ("ATTACK", "HEALTH"))]
kept.append({"op": "MODIFY_ATTR", "attribute": "ATTACK", "percent": 0.80,
             "permanent": True, "target": "self"})
kept.append({"op": "MODIFY_ATTR", "attribute": "HEALTH", "percent": 2.70,
             "permanent": True, "target": "self"})
rule["do"] = kept
rule["source"] = ((rule.get("source") or "") +
                  "\n\u2b50 2026-10-02\uff08\u6587\u6863\uff0c\u5361\u5384\u65af\u5170\u90a3\u7684\u5929\u8d4b\uff09\uff1a\u300c\u2026\u53d8\u8eab\u671f\u95f4**\u653b\u51fb\u529b\u63d0\u9ad8 80%**\uff0c"
                  "**\u751f\u547d\u4e0a\u9650\u63d0\u9ad8 270%**\u300d\u2713")
rule["note"] = ((rule.get("note") or "") +
                "\n\u2b50 2026-10-02\uff1a\u53d8\u8eab\u7684**\u5c5e\u6027\u5757** \u2713\uff08`ATTACK +80%` \u2713\uff0f`HEALTH +270%` \u2713\uff09"
                "\u2014\u2014 \u5f62\u72b6\u540c\u7b2c 22\u2f8b29 \u4ef6\uff08`MODIFY_ATTR` \u968f\u6388\u4e88\u89c4\u5219\u4e00\u8d77\u843d\u4e0b \u2713\uff09\u3002"
                "\u26a0 \u4ecd\u767b\u8bb0\uff1a\u201c\u6b63\u5728\u53d8\u8eab\u201d\u7684\u5176\u4f59\u53e5\u5b50\uff08\u89c1 `source` \u91cc\u7684\u6e05\u5355 \u2717\uff09\u3002")

if not isinstance(doc, dict):
    raise SystemExit("1408.json must be an object")
doc["rules"] = rules
json.dump(doc, io.open(DATA, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   1408.json: the transformation's stat block")

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
 * 1408\uff1a\u300c\u53d8\u8eab\u671f\u95f4**\u653b\u51fb\u529b\u63d0\u9ad8 80%**\uff0c**\u751f\u547d\u4e0a\u9650\u63d0\u9ad8 270%**\u300d (2026-10-02).
 *
 * <p>\u2b50 TWO-WAY, file-driven: her ultimate transforms her, and the block is what the transformation is worth. The control is the
 * same character measured before the ultimate, so nothing else differs.
 */
public class TransformationStatsTest {
    private static final double EPS = 1e-6;
    private static final int OWNER = 1408;
    private static final int MONSTER = 1002011;

    /** \u2b50 The transformation is worth exactly +80% ATK and +270% Max HP. */
    @Test
    public void theTransformationRaisesAtkAndMaxHp() {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        double atk0 = owner.getAttribute(AttributeType.ATTACK).get();
        double hp0 = owner.getAttribute(AttributeType.HEALTH).get();
        Assertions.assertTrue(atk0 > 0 && hp0 > 0, "precondition: the panel reads");

        Skill ult = owner.getSkills().get(SkillType.ULTRA);
        Assertions.assertNotNull(ult, "precondition: she has an ultimate");
        SkillExecutor.execute(battle, ult, owner, List.of(owner));
        battle.processRequests();
        Assertions.assertTrue(owner.getBuffManager().hasState("\u53d8\u8eab"),
                "precondition: the transformation is on");

        Assertions.assertEquals(atk0 * 1.8, owner.getAttribute(AttributeType.ATTACK).get(), atk0 * 0.001,
                "\u300c\u653b\u51fb\u529b\u63d0\u9ad8 80%\u300d (before=" + atk0 + ")");
        Assertions.assertEquals(hp0 * 3.7, owner.getAttribute(AttributeType.HEALTH).get(), hp0 * 0.001,
                "\u300c\u751f\u547d\u4e0a\u9650\u63d0\u9ad8 270%\u300d (before=" + hp0 + ")");
    }

    /** \u26a0 Without the ultimate there is no transformation, so the block is not there either. */
    @Test
    public void withoutTheUltimateTheBlockIsAbsent() {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        Assertions.assertFalse(owner.getBuffManager().hasState("\u53d8\u8eab"), "no transformation yet");
        double atk = owner.getAttribute(AttributeType.ATTACK).get();
        double hp = owner.getAttribute(AttributeType.HEALTH).get();
        Assertions.assertTrue(atk > 0 && hp > 0, "the plain panel is what it is, and the test asserts nothing else");
    }
}
''')
print("ok   judge written")
