"""1408: 「卡厄斯兰那的物理属性抗性穿透提高 20%」 -- and the transformation's stats are bound to the state (2026-10-02, item 35).

Document, verbatim:
  * trace: 「卡厄斯兰那的**物理属性抗性穿透提高 20%**。」 -- 卡厄斯兰那 IS the transformed form, so the sentence is scoped to the
    transformation. The tree already maps 「抗性穿透」 to `DAMAGE_PENETRATION` (shipped for 1502 and for 1412's peerage, item 29).
  * the transformation's own block: 「**变身期间**攻击力提高 80%，生命上限提高 270%…」 -- "DURING the transformation".

⚠ THE DEFECT THIS ITEM FIXES (found while auditing, not while failing): item 34 appended the ATK/MaxHP modifiers WITHOUT linking
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
STATE = "变身"

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
                  "\n⭐ 2026-10-02（行迹）：「卡厄斯兰那的物理属性**抗性穿透提高 20%**」✓"
                  "（⚠ 卡厄斯兰那就是**变身形态** ✓ ⇒ 本句作用域是“变身期间” ✓）")
rule["note"] = ((rule.get("note") or "") +
                "\n⭐ 2026-10-02：三条属性都**绑到【变身】** ✓（`\"buff\": \"变身\"` ✓ —— "
                "⚠ 第 34 件当时**漏了这个绑定** ✗ ⇒ “变身期间”会默默变成“本场战斗剩下的时间” ✗）。"
                "⚠ **仍登记**：同句后半“单次施放【支柱•死星天裁】消耗【毁伤】达到 **4** 点时获得 1 个额外回合” ✗。")

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
 * 1408：「卡厄斯兰那的物理属性**抗性穿透提高 20%**」与「**变身期间**攻击力提高 80%，生命上限提高 270%」
 * (2026-10-02).
 *
 * <p>⭐ THE POINT IS THE THIRD ASSERTION: the block must live exactly as long as the state. Removing 【变身】 and watching ATK
 * come back down is what tells "during the transformation" apart from "for the rest of the battle" -- and item 34 shipped
 * without that link, so this judge would have caught it.
 */
public class TransformationScopedStatsTest {
    private static final double EPS = 1e-9;
    private static final int OWNER = 1408;
    private static final int MONSTER = 1002011;
    private static final String STATE = "变身";

    /** ⭐ Transformed: +80% ATK, +270% Max HP, +20% RES PEN. Un-transformed: none of them. */
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
                "「攻击力提高 80%」");
        Assertions.assertEquals(hp0 * 3.7, owner.getAttribute(AttributeType.HEALTH).get(), hp0 * 1e-9,
                "「生命上限提高 270%」");
        Assertions.assertEquals(pen0 + 0.20, owner.getAttribute(AttributeType.DAMAGE_PENETRATION).get(), EPS,
                "「物理属性抗性穿透提高 20%」");
    }

    /** ⭐⭐ "During the transformation" means the block dies with the state. */
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
                "「变身期间」-- the attack bonus must be gone with the state");
        Assertions.assertEquals(pen0, owner.getAttribute(AttributeType.DAMAGE_PENETRATION).get(), EPS,
                "and the RES PEN too");
    }
}
''')
print("ok   judge written")
