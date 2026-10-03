"""1412: the holder of 【军功】 ignores 16% of the target's DEF (2026-10-02).

Document, verbatim (1412_刻律德菈.html:105):
「持有【军功】的角色造成伤害时**无视目标 16% 的防御力**。若当前【军功】已升级为【爵位】，则该角色造成战技伤害时**额外无视目标 20% 的防御力**。
刻律德菈施放战技时，为指定我方目标恢复 2 点能量。」

This ships the first half -- the documented 16% -- as one more effect on the rule that already grants 【军功】
(`skill_grants_military_merit`), so the cut lands on the ally who received the merit and not on her. The 20% half needs the
"this ally's SKILL damage" qualifier; it is registered rather than approximated.

The op's shape was copied from real content, not guessed (1001/1002):
    { "op": "MODIFY_ATTR", "attribute": "DAMAGE_PENETRATION", "percent": 0.36, "until": "next_attack", "target": "self" }

⚠ Note on units: the existing usage above passes 0.36 for a 36% penetration, so a ratio is what `percent` wants here; the
document's 16% is therefore 0.16. The mutation (0.16 -> 0.10) is what proves the judge binds the number.
ASCII only.
"""
import io
import json

DATA = "src/main/resources/characters/1412.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/MilitaryMeritDefenceIgnoreTest.java"
RULE = "skill_grants_military_merit"

doc = json.load(io.open(DATA, encoding="utf-8"))
rules = doc["rules"] if isinstance(doc, dict) else doc

patched = 0
for rule in rules:
    if isinstance(rule, dict) and rule.get("id") == RULE:
        effects = [e for e in (rule.get("do") or [])
                   if not (isinstance(e, dict) and e.get("attribute") == "DEFENCE_IGNORE")]
        effects.append({
            "op": "MODIFY_ATTR",
            "attribute": "DEFENCE_IGNORE",
            "percent": 0.16,
            "permanent": True,
            "target": "target",
        })
        rule["do"] = effects
        rule["note"] = ((rule.get("note") or "") +
                        "\n\u2b50 2026-10-02\uff1a\u6587\u6863 `:105`\u300c\u6301\u6709\u3010\u519b\u529f\u3011\u7684\u89d2\u8272\u9020\u6210\u4f24\u5bb3\u65f6"
                        "**\u65e0\u89c6\u76ee\u6807 16% \u7684\u9632\u5fa1\u529b**\u300d\u2713 \u21d2 \u52a0\u5728**\u6388\u4e88\u3010\u519b\u529f\u3011\u7684\u90a3\u6761\u89c4\u5219**\u91cc \u2713"
                        "\uff08\u76ee\u6807\u5c31\u662f\u9886\u5230\u519b\u529f\u7684\u90a3\u4f4d \u2713\uff09\u3002"
                        "\u26a0 **\u5df2\u767b\u8bb0**\uff1a\u540c\u53e5\u7684\u300c\u82e5\u5df2\u5347\u4e3a\u3010\u7235\u4f4d\u3011\u2026\u989d\u5916\u65e0\u89c6 20%\u300d\u9700\u201c**\u8be5\u89d2\u8272\u7684\u6218\u6280\u4f24\u5bb3**\u201d\u8fd9\u4e2a\u9650\u5b9a \u2717\u3002")
        patched += 1
if patched != 1:
    raise SystemExit("expected exactly one merit rule, patched " + str(patched))

if isinstance(doc, dict):
    doc["rules"] = rules
else:
    doc = rules
json.dump(doc, io.open(DATA, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   1412.json: the merit holder ignores 16% DEF")

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
 * 1412\uff1a\u300c\u6301\u6709\u3010\u519b\u529f\u3011\u7684\u89d2\u8272\u9020\u6210\u4f24\u5bb3\u65f6**\u65e0\u89c6\u76ee\u6807 16% \u7684\u9632\u5fa1\u529b**\u300d (2026-10-02).
 *
 * <p>\u2b50 FILE-DRIVEN: her skill is what grants \u3010\u519b\u529f\u3011, so after it the ALLY should carry the 16% defence ignore -- while she
 * herself should not, which is the "false side" this judge also asserts.
 */
public class MilitaryMeritDefenceIgnoreTest {
    private static final int OWNER = 1412;
    private static final int ALLY = 1002;
    private static final int MONSTER = 1002011;

    /** \u2b50 The merit holder ignores 16% DEF; the caster does not. */
    @Test
    public void theMeritHolderIgnoresSixteenPercent() {
        Character owner = CharacterFactory.create(OWNER, 80);
        Character ally = CharacterFactory.create(ALLY, 80);
        Battle battle = new Battle(List.of(owner, ally),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        Skill skill = owner.getSkills().get(SkillType.SKILL);
        Assertions.assertNotNull(skill, "precondition: she has a skill");
        SkillExecutor.execute(battle, skill, owner, List.of(ally));
        battle.processRequests();

        Assertions.assertEquals(0.16, ally.getAttribute(AttributeType.DEFENCE_IGNORE).get(), 1e-9,
                "\u6301\u6709\u3010\u519b\u529f\u3011\u7684\u89d2\u8272\u65e0\u89c6 16% \u9632\u5fa1");
        Assertions.assertEquals(0.0, owner.getAttribute(AttributeType.DEFENCE_IGNORE).get(), 1e-9,
                "and the caster keeps none of it (the false side)");
    }
}
''')
print("ok   judge written")
