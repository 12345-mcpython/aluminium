"""1408: 「变身期间攻击力提高 80%，生命上限提高 270%」 (2026-10-02, item 34).

Document, verbatim (1408_白厄.html, 卡厄斯兰那的天赋): 「…变身期间**攻击力提高 80%**，**生命上限提高 270%**，施放攻击后回复等同于自身生命上限
20% 的生命值。」

Same shape as item 22 and item 29 (a `MODIFY_ATTR` riding the rule that grants the state), and every number is the document's own:
80 and 270. The transformation rule already exists and already rides `ULT_CAST` (`ult_transformation`, which also starts the
countdown), so this appends two effects beside them -- nothing new is invented.

⚠ NOT in this item, and registered instead (the audit of 1408 is in GAPS): 「施放攻击后回复 20% 生命上限」 (needs an
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
                  "\n⭐ 2026-10-02（文档，卡厄斯兰那的天赋）：「…变身期间**攻击力提高 80%**，"
                  "**生命上限提高 270%**」✓")
rule["note"] = ((rule.get("note") or "") +
                "\n⭐ 2026-10-02：变身的**属性块** ✓（`ATTACK +80%` ✓／`HEALTH +270%` ✓）"
                "—— 形状同第 22⾋29 件（`MODIFY_ATTR` 随授予规则一起落下 ✓）。"
                "⚠ 仍登记：“正在变身”的其余句子（见 `source` 里的清单 ✗）。")

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
 * 1408：「变身期间**攻击力提高 80%**，**生命上限提高 270%**」 (2026-10-02).
 *
 * <p>⭐ TWO-WAY, file-driven: her ultimate transforms her, and the block is what the transformation is worth. The control is the
 * same character measured before the ultimate, so nothing else differs.
 */
public class TransformationStatsTest {
    private static final double EPS = 1e-6;
    private static final int OWNER = 1408;
    private static final int MONSTER = 1002011;

    /** ⭐ The transformation is worth exactly +80% ATK and +270% Max HP. */
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
        Assertions.assertTrue(owner.getBuffManager().hasState("变身"),
                "precondition: the transformation is on");

        Assertions.assertEquals(atk0 * 1.8, owner.getAttribute(AttributeType.ATTACK).get(), atk0 * 0.001,
                "「攻击力提高 80%」 (before=" + atk0 + ")");
        Assertions.assertEquals(hp0 * 3.7, owner.getAttribute(AttributeType.HEALTH).get(), hp0 * 0.001,
                "「生命上限提高 270%」 (before=" + hp0 + ")");
    }

    /** ⚠ Without the ultimate there is no transformation, so the block is not there either. */
    @Test
    public void withoutTheUltimateTheBlockIsAbsent() {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        Assertions.assertFalse(owner.getBuffManager().hasState("变身"), "no transformation yet");
        double atk = owner.getAttribute(AttributeType.ATTACK).get();
        double hp = owner.getAttribute(AttributeType.HEALTH).get();
        Assertions.assertTrue(atk > 0 && hp > 0, "the plain panel is what it is, and the test asserts nothing else");
    }
}
''')
print("ok   judge written")
