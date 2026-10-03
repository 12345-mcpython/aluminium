"""1412: 「已是【爵位】⇒ 造成战技伤害时额外无视目标 20% 的防御力」-- judged by DAMAGE, not by an attribute (2026-10-02).

Document, verbatim (1412_刻律德菈.html:105): 「持有【军功】的角色造成伤害时无视目标 16% 的防御力。若当前【军功】已升级为【爵位】，
则该角色造成**战技伤害**时额外无视目标 **20%** 的防御力。」

WHY THIS ATTEMPT LOOKS DIFFERENT (measured, not guessed): the attribute probe has been the wrong instrument all along.
`self_attr:DEFENCE_IGNORE` reads the ATTRIBUTE SHEET, and this sentence's effect lives in the settled DAMAGE of one instance.
The curve probe said so plainly:
    DEFENCE_IGNORE (ally / owner) by casts: 1->0.16/0.0  2->0.16/0.0 ... 6->0.16/0.0  7->0.16/0.0
-- the 16% (item 22) is always there, the 20% never is, on either unit. So judge the sentence the way item 27's 28% rider was
judged: change ONE variable and compare the DAMAGE. Here the variable is the peerage itself -- the control runs the same six
casts and then removes 【爵位】, so the scene is identical in every other respect (the discipline "same scene, one variable").

\u2b50 The modifier rides the instance route (`instance: true`), which is the engine's documented way to scope a modifier to a
damage instance -- `MODIFY_ATTR` with instance=true supports DEFENCE_IGNORE.

\u26a0 A duration is still required on the instance route: the loader refuses without it (verbatim: Op MODIFY_ATTR requires
"turns" (how long the buff lasts), "permanent": true ...).
ASCII only.
"""
import io
import json

DATA = "src/main/resources/characters/1412.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/PeeragePierceDamageTest.java"
RULE = "peerage_skill_extra_pierce"
PEERAGE = "\u7235\u4f4d"

doc = json.load(io.open(DATA, encoding="utf-8"))
rules = doc["rules"] if isinstance(doc, dict) else doc
rules = [r for r in rules if not (isinstance(r, dict) and r.get("id") == RULE)]

rules.append({
    "on": "DEALING_DAMAGE",
    "id": RULE,
    "when": ["actor has_state " + PEERAGE, "from_category BPSKILL"],
    "do": [{"op": "MODIFY_ATTR", "attribute": "DEFENCE_IGNORE", "percent": 0.20,
            "instance": True, "permanent": True, "target": "self"}],
    "source": ("1412 \u523b\u5f8b\u5fb7\u83c8 \u884c\u8ff9\uff08\u6587\u6863 `:105`\uff09\uff1a\u300c\u82e5\u5f53\u524d\u3010\u519b\u529f\u3011\u5df2\u5347\u7ea7\u4e3a"
               "\u3010\u7235\u4f4d\u3011\uff0c\u5219\u8be5\u89d2\u8272**\u9020\u6210\u6218\u6280\u4f24\u5bb3\u65f6\u989d\u5916\u65e0\u89c6\u76ee\u6807 20%** \u7684\u9632\u5fa1\u529b\u300d"),
    "note": ("\u2b50 2026-10-02\uff1a\u8d70 **instance \u8def\u7531** \u2713\uff08`instance: true` \u2713 \u2014\u2014 \u5f15\u64ce\u6587\u6863\uff1a"
             "\u201c`MODIFY_ATTR` with instance=true supports DEFENCE_IGNORE\u201d \u2713\uff09\uff0c\u26a0 \u4f46\u4ecd\u9700\u65f6\u957f \u2717"
             "\uff08\u88c5\u8f7d\u5668\u9010\u5b57\uff1a*requires \"turns\" \u2026 \"permanent\": true*\uff09\u3002"
             "\u26a0 **\u5224\u636e\u4e0d\u518d\u95ee\u5c5e\u6027\u8868** \u2713\uff08\u5c5e\u6027\u63a2\u9488\u5df2\u91cf\u51fa\uff1a16% \u6052\u5728\u3001\u4e24\u4e2a\u5355\u4f4d\u90fd\u770b\u4e0d\u5230 20% \u2717\uff09"
             "\u800c\u662f**\u6bd4\u4f24\u5bb3** \u2713\uff08\u540c\u4e00\u573a\u620f\u3001\u53ea\u6362\u201c\u6709\u6ca1\u6709\u7235\u4f4d\u201d \u2713\uff09\u3002"),
})

if not isinstance(doc, dict):
    raise SystemExit("1412.json must be an object")
doc["rules"] = rules
json.dump(doc, io.open(DATA, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   1412.json: the peerage pierce, judged by damage")

io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
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
 * 1412\uff1a\u300c\u82e5\u5df2\u5347\u4e3a\u3010\u7235\u4f4d\u3011\u2026\u8be5\u89d2\u8272\u9020\u6210\u6218\u6280\u4f24\u5bb3\u65f6**\u989d\u5916\u65e0\u89c6 20%** \u9632\u5fa1\u300d (2026-10-02).
 *
 * <p>\u2b50 SAME SCENE, ONE VARIABLE: both runs reach six Charge (so the merit holder is a peer), cast the same skill at the same
 * enemy; the control then removes \u3010\u7235\u4f4d\u3011. What is compared is the DAMAGE -- because `self_attr:` reads the sheet, and a
 * sentence about "when dealing Skill DMG" lives in one settled instance.
 */
public class PeeragePierceDamageTest {
    private static final int OWNER = 1412;
    private static final int ALLY = 1002;
    private static final int MONSTER = 1002011;
    private static final String PEERAGE = "\u7235\u4f4d";

    /** \u2b50 A peer's skill hits harder than the same skill without the peerage. */
    @Test
    public void thePeerPiercesMoreOnSkillDamage() {
        double withPeer = damageDealt(true);
        double without = damageDealt(false);
        Assertions.assertTrue(without > 0, "precondition: the control deals damage (" + without + ")");
        Assertions.assertTrue(withPeer > without,
                "\u300c\u989d\u5916\u65e0\u89c6 20% \u9632\u5fa1\u300d (with=" + withPeer + ", without=" + without + ")");
    }

    private static double damageDealt(boolean keepPeerage) {
        Character owner = CharacterFactory.create(OWNER, 80);
        Character ally = CharacterFactory.create(ALLY, 80);
        Battle battle = new Battle(List.of(owner, ally),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        Skill hers = owner.getSkills().get(SkillType.SKILL);
        for (int i = 0; i < 6; i++) {
            SkillExecutor.execute(battle, hers, owner, List.of(ally));
            battle.processRequests();
        }
        Assertions.assertTrue(ally.getBuffManager().hasState("\u519b\u529f"),
                "precondition: the ally holds the merit");
        Assertions.assertTrue(ally.getBuffManager().hasState(PEERAGE),
                "precondition: six casts promote the holder");
        if (!keepPeerage) {
            ally.getBuffManager().removeState(PEERAGE);
            battle.processRequests();
        }

        double before = battle.enemies.get(0).getCurrentHp();
        Skill his = ally.getSkills().get(SkillType.SKILL);
        Assertions.assertNotNull(his, "precondition: the ally has a skill");
        SkillExecutor.execute(battle, his, ally, List.of(battle.enemies.get(0)));
        battle.processRequests();
        return before - battle.enemies.get(0).getCurrentHp();
    }
}
''')
print("ok   judge written")
