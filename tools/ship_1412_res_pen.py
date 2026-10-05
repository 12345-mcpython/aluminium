"""1412: the peerage holder's All-Type RES PEN +10% (2026-10-02, item 29).

Document, verbatim (1412_刻律德菈.html:67):
「持有【爵位】的角色造成的战技伤害的暴击伤害提高 **72%**、全属性**抗性穿透提高 10.00%**，对敌方目标施放战技时触发奇袭。
  奇袭结束后，消耗 6 点充能使【爵位】变回【军功】。」

This ships the SECOND of those three clauses, because it is the one that is exactly expressible today:
  * it carries no scope qualifier (unlike the 72%, which is "for their dealt Skill DMG" and would be an approximation
    without a skill-scoped modifier -- so it stays registered);
  * 「抗性穿透」 is defined at :68 as "when dealing damage, ignore part of the target's corresponding resistance", and the tree
    already maps that spelling to `DAMAGE_PENETRATION` -- shipped for 1502's 「全属性抗性穿透提高 10%」;
  * its shape is the same one that worked for this character's 16% DEF ignore (item 22): a `MODIFY_ATTR` riding the rule that
    grants the state, with `target` naming the unit that receives it.

⚠ The upgrade rule already exists and already names its target explicitly --
`peerage_upgrade_at_six_charge`, `when: ["actor == self", "self_resource:充能 >= 6"]`,
`do: [{ "op": "APPLY_BUFF", "buff": "爵位", "target": "holder_of:军功", "permanent": true }]` -- so this ship appends one effect
beside it with the SAME target. Nothing new is invented.
ASCII only.
"""
import io
import json

DATA = "src/main/resources/characters/1412.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/PeerageResPenTest.java"
RULE = "peerage_upgrade_at_six_charge"

doc = json.load(io.open(DATA, encoding="utf-8"))
rules = doc["rules"] if isinstance(doc, dict) else doc

hit = [r for r in rules if isinstance(r, dict) and r.get("id") == RULE]
if len(hit) != 1:
    raise SystemExit("expected exactly one peerage upgrade rule, found " + str(len(hit)))

rule = hit[0]
do = [e for e in (rule.get("do") or [])
      if not (isinstance(e, dict) and e.get("attribute") == "DAMAGE_PENETRATION")]
do.append({"op": "MODIFY_ATTR", "attribute": "DAMAGE_PENETRATION", "percent": 0.10,
           "permanent": True, "max_stacks": 2, "target": "holder_of:军功"})
rule["do"] = do
rule["source"] = ((rule.get("source") or "") +
                  "\n⭐ 2026-10-02（文档 `:67`）：「持有【爵位】的角色…"
                  "全属性**抗性穿透提高 10.00%**」✓")
rule["note"] = ((rule.get("note") or "") +
                "\n⭐ 2026-10-02：加上**全属性抗性穿透 +10%** ✓ —— 形状照拄同一条规则里"
                "已有的写法（`target: \"holder_of:军功\"` ✓），而 `DAMAGE_PENETRATION` 对应文档 `:68` 的"
                "「抗性穿透」定义 ✓（同拼写已在 1502 出货过 ✓）。"
                "⚠ **仍登记**：同句的“**战技伤害的爆伤 +72%**”（带“战技”限定 ✗）"
                "与“**奇袭**”及其结束时的“消耗 6 点充能、爵位变回【军功】”。")

if not isinstance(doc, dict):
    raise SystemExit("1412.json must be an object")
doc["rules"] = rules
json.dump(doc, io.open(DATA, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   1412.json: the peerage grants ten percent RES penetration")

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
 * 1412：「持有【爵位】的角色…全属性抗性穿透提高 10.00%」 (2026-10-02).
 *
 * <p>⭐ TWO-WAY, file-driven: reaching six Charge promotes the merit holder, and the promotion is what carries the 10%. One
 * cast gives the merit but not the peerage, so the control is the same sentence one step earlier.
 */
public class PeerageResPenTest {
    private static final double EPS = 1e-9;
    private static final int OWNER = 1412;
    private static final int ALLY = 1002;
    private static final int MONSTER = 1002011;

    /** ⭐ At six Charge the holder is a peer, and being a peer is worth exactly 10% more All-Type RES PEN. */
    @Test
    public void thePeerHoldsTenPercentMoreResPen() {
        Assertions.assertEquals(0.10, resPenAfter(6) - resPenAfter(1), EPS,
                "「持有【爵位】的角色…全属性抗性穿透提高 10.00%」");
    }

    /** ⚠ Five Charge is still short of the threshold, so the sentence has not started yet. */
    @Test
    public void fiveChargeIsStillShortOfThePeerage() {
        Assertions.assertEquals(resPenAfter(1), resPenAfter(5), EPS,
                "below six Charge the sentence has not started");
    }

    // ==================================================================

    private static double resPenAfter(int casts) {
        Character owner = CharacterFactory.create(OWNER, 80);
        Character ally = CharacterFactory.create(ALLY, 80);
        Battle battle = new Battle(List.of(owner, ally),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        Skill skill = owner.getSkills().get(SkillType.SKILL);
        Assertions.assertNotNull(skill, "precondition: she has a skill");
        for (int i = 0; i < casts; i++) {
            SkillExecutor.execute(battle, skill, owner, List.of(ally));
            battle.processRequests();
        }
        Assertions.assertTrue(ally.getBuffManager().hasState("军功"),
                "precondition: the ally holds the merit");
        Assertions.assertEquals(casts >= 6, ally.getBuffManager().hasState("爵位"),
                "precondition: six casts is what promotes the holder");
        return ally.getAttribute(AttributeType.DAMAGE_PENETRATION).get();
    }
}
''')
print("ok   judge written")
