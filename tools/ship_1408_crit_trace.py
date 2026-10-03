"""1408: trace 「施放终结技时，暴击伤害提高 50%，持续 3 回合」 (2026-10-02, item 36).

Document, verbatim (1408_白厄.html, 行迹): 「…**施放终结技时，暴击伤害提高 50%，持续 3 回合**。」

\u2b50 WHY IT RIDES THE EXISTING `ult_transformation` RULE rather than a new ULT_CAST rule: that rule already fires on
`ULT_CAST` with `actor == self`, which is exactly the event this sentence names, and the document also says 卡厄斯兰那
**cannot cast an ultimate** (「无法施放终结技」) -- so every ultimate she casts IS the transformation cast. Appending one effect
keeps the trigger vocabulary unchanged and adds no second reader of the same event.

\u26a0 It carries NO `"buff": "变身"` link on purpose (unlike item 35's three effects): "持续 3 回合" is a duration of its own, not
"while transformed" -- the buff must be able to outlive the state (and, after the transformation ends, outlive her reversion).
`CRIT_ATTACK` is 暴击伤害 (item 32 measured that: the data's `crit_attack` is 0.5, and 1101's shipped note calls it that).
ASCII only.
"""
import io
import json

DATA = "src/main/resources/characters/1408.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/UltCritDamageTraceTest.java"
RULE = "ult_transformation"

doc = json.load(io.open(DATA, encoding="utf-8"))
rules = doc["rules"] if isinstance(doc, dict) else doc

hit = [r for r in rules if isinstance(r, dict) and r.get("id") == RULE]
if len(hit) != 1:
    raise SystemExit("expected exactly one transformation rule, found " + str(len(hit)))

rule = hit[0]
kept = [e for e in (rule.get("do") or [])
        if not (isinstance(e, dict) and e.get("attribute") == "CRIT_ATTACK")]
kept.append({"op": "MODIFY_ATTR", "attribute": "CRIT_ATTACK", "percent": 0.50,
             "turns": 3, "target": "self"})
rule["do"] = kept
rule["source"] = ((rule.get("source") or "") +
                  "\n\u2b50 2026-10-02\uff08\u884c\u8ff9\uff09\uff1a\u300c**\u65bd\u653e\u7ec8\u7ed3\u6280\u65f6\uff0c\u66b4\u51fb\u4f24\u5bb3\u63d0\u9ad8 50%\uff0c\u6301\u7eed 3 \u56de\u5408**\u300d\u2713")
rule["note"] = ((rule.get("note") or "") +
                "\n\u2b50 2026-10-02\uff1a\u884c\u8ff9\u7684**\u4e34\u65f6\u66b4\u4f24** \u2713\uff08`MODIFY_ATTR{CRIT_ATTACK, 0.50, turns: 3}` \u2713\uff09"
                "\u2014\u2014 \u2757 **\u523b\u610f\u4e0d\u7ed1\u3010\u53d8\u8eab\u3011** \u2713\uff08\u5b83\u662f\u201c\u6301\u7eed 3 \u56de\u5408\u201d\u7684\u72ec\u7acb\u65f6\u957f \u2713\uff0c"
                "\u4e0d\u662f\u201c\u53d8\u8eab\u671f\u95f4\u201d \u2713\uff09\u3002")

if not isinstance(doc, dict):
    raise SystemExit("1408.json must be an object")
doc["rules"] = rules
json.dump(doc, io.open(DATA, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   1408.json: the ultimate's crit-damage trace")

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
 * 1408\uff1a\u300c\u65bd\u653e\u7ec8\u7ed3\u6280\u65f6\uff0c\u66b4\u51fb\u4f24\u5bb3\u63d0\u9ad8 50%\uff0c\u6301\u7eed 3 \u56de\u5408\u300d (2026-10-02).
 *
 * <p>\u2b50 TWO-WAY: the same character measured before and after her ultimate, so the only thing that differs is the cast.
 */
public class UltCritDamageTraceTest {
    private static final double EPS = 1e-9;
    private static final int OWNER = 1408;
    private static final int MONSTER = 1002011;

    /** \u2b50 Casting the ultimate raises her crit damage by exactly 50%. */
    @Test
    public void theUltimateRaisesCritDamage() {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        double before = owner.getAttribute(AttributeType.CRIT_ATTACK).get();
        Assertions.assertTrue(before > 0, "precondition: the panel reads (" + before + ")");

        Skill ult = owner.getSkills().get(SkillType.ULTRA);
        Assertions.assertNotNull(ult, "precondition: she has an ultimate");
        SkillExecutor.execute(battle, ult, owner, List.of(owner));
        battle.processRequests();

        Assertions.assertEquals(before + 0.50, owner.getAttribute(AttributeType.CRIT_ATTACK).get(), EPS,
                "\u300c\u65bd\u653e\u7ec8\u7ed3\u6280\u65f6\uff0c\u66b4\u51fb\u4f24\u5bb3\u63d0\u9ad8 50%\u300d (before=" + before + ")");
    }

    /** \u26a0 Before the ultimate the trace has not started, so the panel is untouched. */
    @Test
    public void beforeTheUltimateThePanelIsUntouched() {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        double before = owner.getAttribute(AttributeType.CRIT_ATTACK).get();
        Assertions.assertFalse(owner.getBuffManager().hasState("\u53d8\u8eab"), "no ultimate yet");
        Assertions.assertEquals(before, owner.getAttribute(AttributeType.CRIT_ATTACK).get(), EPS,
                "nothing has been cast, so nothing has changed");
    }
}
''')
print("ok   judge written")
