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

\u26a0 The upgrade rule already exists and already names its target explicitly --
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
           "permanent": True, "buff": "\u7235\u4f4d", "target": "holder_of:\u519b\u529f"})
rule["do"] = do
rule["source"] = ((rule.get("source") or "") +
                  "\n\u2b50 2026-10-02\uff08\u6587\u6863 `:67`\uff09\uff1a\u300c\u6301\u6709\u3010\u7235\u4f4d\u3011\u7684\u89d2\u8272\u2026"
                  "\u5168\u5c5e\u6027**\u6297\u6027\u7a7f\u900f\u63d0\u9ad8 10.00%**\u300d\u2713")
rule["note"] = ((rule.get("note") or "") +
                "\n\u2b50 2026-10-02\uff1a\u52a0\u4e0a**\u5168\u5c5e\u6027\u6297\u6027\u7a7f\u900f +10%** \u2713 \u2014\u2014 \u5f62\u72b6\u7167\u62c4\u540c\u4e00\u6761\u89c4\u5219\u91cc"
                "\u5df2\u6709\u7684\u5199\u6cd5\uff08`target: \"holder_of:\u519b\u529f\"` \u2713\uff09\uff0c\u800c `DAMAGE_PENETRATION` \u5bf9\u5e94\u6587\u6863 `:68` \u7684"
                "\u300c\u6297\u6027\u7a7f\u900f\u300d\u5b9a\u4e49 \u2713\uff08\u540c\u62fc\u5199\u5df2\u5728 1502 \u51fa\u8d27\u8fc7 \u2713\uff09\u3002"
                "\u26a0 **\u4ecd\u767b\u8bb0**\uff1a\u540c\u53e5\u7684\u201c**\u6218\u6280\u4f24\u5bb3\u7684\u7206\u4f24 +72%**\u201d\uff08\u5e26\u201c\u6218\u6280\u201d\u9650\u5b9a \u2717\uff09"
                "\u4e0e\u201c**\u5947\u88ad**\u201d\u53ca\u5176\u7ed3\u675f\u65f6\u7684\u201c\u6d88\u8017 6 \u70b9\u5145\u80fd\u3001\u7235\u4f4d\u53d8\u56de\u3010\u519b\u529f\u3011\u201d\u3002")

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
 * 1412\uff1a\u300c\u6301\u6709\u3010\u7235\u4f4d\u3011\u7684\u89d2\u8272\u2026\u5168\u5c5e\u6027\u6297\u6027\u7a7f\u900f\u63d0\u9ad8 10.00%\u300d (2026-10-02).
 *
 * <p>\u2b50 TWO-WAY, file-driven: reaching six Charge promotes the merit holder, and the promotion is what carries the 10%. One
 * cast gives the merit but not the peerage, so the control is the same sentence one step earlier.
 */
public class PeerageResPenTest {
    private static final double EPS = 1e-9;
    private static final int OWNER = 1412;
    private static final int ALLY = 1002;
    private static final int MONSTER = 1002011;

    /** \u2b50 At six Charge the holder is a peer, and being a peer is worth exactly 10% more All-Type RES PEN. */
    @Test
    public void thePeerHoldsTenPercentMoreResPen() {
        Assertions.assertEquals(0.10, resPenAfter(6) - resPenAfter(1), EPS,
                "\u300c\u6301\u6709\u3010\u7235\u4f4d\u3011\u7684\u89d2\u8272\u2026\u5168\u5c5e\u6027\u6297\u6027\u7a7f\u900f\u63d0\u9ad8 10.00%\u300d");
    }

    /** \u26a0 Five Charge is still short of the threshold, so the sentence has not started yet. */
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
        Assertions.assertTrue(ally.getBuffManager().hasState("\u519b\u529f"),
                "precondition: the ally holds the merit");
        Assertions.assertEquals(casts >= 6, ally.getBuffManager().hasState("\u7235\u4f4d"),
                "precondition: six casts is what promotes the holder");
        return ally.getAttribute(AttributeType.DAMAGE_PENETRATION).get();
    }
}
''')
print("ok   judge written")
