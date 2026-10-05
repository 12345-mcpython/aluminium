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

⭐ The modifier rides the instance route (`instance: true`), which is the engine's documented way to scope a modifier to a
damage instance -- `MODIFY_ATTR` with instance=true supports DEFENCE_IGNORE.

⚠ A duration is still required on the instance route: the loader refuses without it (verbatim: Op MODIFY_ATTR requires
"turns" (how long the buff lasts), "permanent": true ...).
ASCII only.
"""
import io
import json

DATA = "src/main/resources/characters/1412.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/PeeragePierceDamageTest.java"
RULE = "peerage_skill_extra_pierce"
PEERAGE = "爵位"

doc = json.load(io.open(DATA, encoding="utf-8"))
rules = doc["rules"] if isinstance(doc, dict) else doc
rules = [r for r in rules if not (isinstance(r, dict) and r.get("id") == RULE)]

rules.append({
    "on": "DEALING_DAMAGE",
    "id": RULE,
    "when": ["actor has_state " + PEERAGE, "from_category BPSKILL"],
    "do": [{"op": "MODIFY_ATTR", "attribute": "DEFENCE_IGNORE", "percent": 0.20,
            "instance": True, "permanent": True, "target": "self"}],
    "source": ("1412 刻律德菈 行迹（文档 `:105`）：「若当前【军功】已升级为"
               "【爵位】，则该角色**造成战技伤害时额外无视目标 20%** 的防御力」"),
    "note": ("⭐ 2026-10-02：走 **instance 路由** ✓（`instance: true` ✓ —— 引擎文档："
             "“`MODIFY_ATTR` with instance=true supports DEFENCE_IGNORE” ✓），⚠ 但仍需时长 ✗"
             "（装载器逐字：*requires \"turns\" … \"permanent\": true*）。"
             "⚠ **判据不再问属性表** ✓（属性探针已量出：16% 恒在、两个单位都看不到 20% ✗）"
             "而是**比伤害** ✓（同一场戏、只换“有没有爵位” ✓）。"),
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
 * 1412：「若已升为【爵位】…该角色造成战技伤害时**额外无视 20%** 防御」 (2026-10-02).
 *
 * <p>⭐ SAME SCENE, ONE VARIABLE: both runs reach six Charge (so the merit holder is a peer), cast the same skill at the same
 * enemy; the control then removes 【爵位】. What is compared is the DAMAGE -- because `self_attr:` reads the sheet, and a
 * sentence about "when dealing Skill DMG" lives in one settled instance.
 */
public class PeeragePierceDamageTest {
    private static final int OWNER = 1412;
    private static final int ALLY = 1002;
    private static final int MONSTER = 1002011;
    private static final String PEERAGE = "爵位";

    /** ⭐ A peer's skill hits harder than the same skill without the peerage. */
    @Test
    public void thePeerPiercesMoreOnSkillDamage() {
        double withPeer = damageDealt(true);
        double without = damageDealt(false);
        Assertions.assertTrue(without > 0, "precondition: the control deals damage (" + without + ")");
        Assertions.assertTrue(withPeer > without,
                "「额外无视 20% 防御」 (with=" + withPeer + ", without=" + without + ")");
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
        Assertions.assertTrue(ally.getBuffManager().hasState("军功"),
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
