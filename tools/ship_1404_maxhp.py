"""1404: 「【血仇】状态下生命上限提高，数值等同于当前生命上限的 #5%」 (2026-10-02, item 50).

Document, verbatim (1404 万敌 天赋「以血还血」, read as UTF-8 via a file): 「充能达到100时消耗100点充能**进入【血仇】状态**并回复…#1%…
**【血仇】状态下生命上限提高，数值等同于当前生命上限的 #5%**，防御力保持为0。」

PARAMETER (measured: `skills.json` -> 1404/4/param_list, first row `[0.15, 0, 150, 0.5, 0.5]`): #5 = **0.5**.

\u2b50 WHY "等同于**当前**生命上限的 50%" IS EXACT as an additive +50% on HEALTH: at the moment the state is applied there is only one
Max HP in play, so "a share of the current one" and "an additive percentage of the base" describe the same number. (This is NOT the
case for a stat that other effects have already moved -- it is exact here because it is the entry moment.)

\u2b50 AND IT RIDES THE STATE: `"buff": "血仇"` ties the modifier's life to the state (the link measured in item 35), so removing 血仇 --
which is exactly how the paragraph ends it -- takes the extra Max HP with it. No second rule, no cleanup.

\u26a0 REGISTERED, not shipped, from the same sentence: 「**防御力保持为 0**」. It is not a reduction but a PIN -- other effects may raise DEF
and it must still be 0 -- and `MODIFY_ATTR` gives a base attribute an ADDITIVE percentage (they sum), so "-100%" would be cancelled by any
later additive bonus. The exact spelling needs a MULTIPLY-percent modifier, which the op does not offer for base attributes.
\u26a0 ALSO REGISTERED, from the next sentence of the same paragraph: 「充能达到 150 点时…获得 1 个额外回合并**自动施放【弑神登神】**」 --
`CAST_SKILL` names a SLOT (`SkillType`: COMMON / SKILL / ULTRA / TALENT / MAZE / TECHNIQUE), and 【弑神登神】 is data skill id 11 with no slot, so
it cannot be named today; the extra turn half (`EXTRA_TURN`) is expressible and waits on that.
ASCII only.
"""
import io
import json

DATA = "src/main/resources/characters/1404.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/BloodfeudMaxHpTest.java"
ENTER = "talent_enters_bloodfeud_at_a_hundred"
STATE = "\u8840\u4ec7"
CHARGE = "\u5929\u8d4b\u5145\u80fd"

doc = json.load(io.open(DATA, encoding="utf-8"))
isObject = isinstance(doc, dict)
rules = doc["rules"] if isObject else doc

hit = [r for r in rules if isinstance(r, dict) and r.get("id") == ENTER]
if len(hit) != 1:
    raise SystemExit("expected the bloodfeud entry rule, found " + str(len(hit)))

rule = hit[0]
kept = [e for e in (rule.get("do") or []) if not (isinstance(e, dict) and e.get("attribute") == "HEALTH")]
kept.append({"op": "MODIFY_ATTR", "attribute": "HEALTH", "percent": 0.50,
             "permanent": True, "buff": STATE, "target": "self"})
rule["do"] = kept
rule["note"] = ((rule.get("note") or "") +
                "\n\u2b50 2026-10-02\uff1a**\u72b6\u6001\u5185\u7684\u751f\u547d\u4e0a\u9650** \u2713\uff08`HEALTH +50%` \u2713 \u2f8b `buff: \"\u8840\u4ec7\"` \u7ed1\u5b9a \u2713\uff09"
                "\u2014\u2014 \u26a0 **\u201c\u9632\u5fa1\u529b\u4fdd\u6301\u4e3a 0\u201d\u4ecd\u767b\u8bb0** \u2717\uff08\u90a3\u662f**\u9489\u4f4f**\uff0c\u9700\u8981**\u4e58\u6cd5**\u4fee\u9970 \u2717\uff09\u3002")

if isObject:
    doc["rules"] = rules
else:
    doc = rules
json.dump(doc, io.open(DATA, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   1404.json: bloodfeud raises Max HP, and the raise dies with the state")

io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageElement;
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
 * 1404\uff1a\u300c\u3010\u8840\u4ec7\u3011\u72b6\u6001\u4e0b\u751f\u547d\u4e0a\u9650\u63d0\u9ad8\uff0c\u6570\u503c\u7b49\u540c\u4e8e\u5f53\u524d\u751f\u547d\u4e0a\u9650\u7684 50%\u300d (2026-10-02).
 *
 * <p>\u2b50 ONE VARIABLE, in one scene: the state goes ON (a hundred charge, five ultimates) and then OFF (the lethal blow the paragraph
 * names as its only exit). Max HP must follow it both ways.
 */
public class BloodfeudMaxHpTest {
    private static final int MYDEI = 1404;
    private static final int MONSTER = 1002011;
    private static final String STATE = "\u8840\u4ec7";
    private static final String CHARGE = "\u5929\u8d4b\u5145\u80fd";

    /** \u2b50 +50% Max HP while \u3010\u8840\u4ec7\u3011 is on, and back to the base when it leaves. */
    @Test
    public void theRaiseFollowsTheState() {
        Character him = CharacterFactory.create(MYDEI, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(him),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        double base = him.getMaxHp();

        Skill ult = him.getSkills().get(SkillType.ULTRA);
        Assertions.assertNotNull(ult, "precondition: he has an ultimate");
        for (int i = 0; i < 5; i++) {
            SkillExecutor.execute(battle, ult, him, List.of(battle.enemies.getFirst()));
            battle.processRequests();
        }
        Assertions.assertTrue(him.getBuffManager().hasState(STATE), "precondition: \u3010\u8840\u4ec7\u3011 is on");
        Assertions.assertEquals(base * 1.5, him.getMaxHp(), base * 0.01,
                "\u300c\u3010\u8840\u4ec7\u3011\u72b6\u6001\u4e0b\u751f\u547d\u4e0a\u9650\u63d0\u9ad8\uff0c\u6570\u503c\u7b49\u540c\u4e8e\u5f53\u524d\u751f\u547d\u4e0a\u9650\u7684 50%\u300d");

        // \u2b50 The paragraph's ONLY exit: the lethal blow. The raise must go with the state.
        battle.applyTrueDamage(battle.enemies.getFirst(), him, DamageElement.ICE, him.getCurrentHp() * 2.0);
        battle.processRequests();
        Assertions.assertFalse(him.getBuffManager().hasState(STATE), "precondition: the lethal blow ended it");
        Assertions.assertEquals(base, him.getMaxHp(), base * 0.01,
                "\u300c\u9000\u51fa\u3010\u8840\u4ec7\u3011\u72b6\u6001\u300d-- the extra Max HP leaves with it");
    }
}
''')
print("ok   judge written")
