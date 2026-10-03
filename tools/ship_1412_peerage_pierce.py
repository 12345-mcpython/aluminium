"""1412: the peerage holder's SKILL damage ignores 20% more DEF (2026-10-02).

Document, verbatim (1412_刻律德菈.html:105):
「持有【军功】的角色造成伤害时无视目标 16% 的防御力。**若当前【军功】已升级为【爵位】，则该角色造成战技伤害时额外无视目标 20% 的防御力。**
刻律德菈施放战技时，为指定我方目标恢复 2 点能量。」

Shape, and why. The 20% must hold only for the skill's own damage, so it cannot be `permanent` (that would stack on every
skill). The lifetime closed set offers exactly one spelling for "inside the cast being delivered": `cast_end`. So the rule
hangs on CAST_SETUP with `from_category BPSKILL` + `actor has_state 爵位`, and the modifier is born and dies inside the cast.

The judge cannot read the attribute afterwards -- by then `cast_end` has dropped it. It therefore installs a PROBE RULE on
DEALING_DAMAGE whose condition is the attribute itself (`self_attr:DEFENCE_IGNORE >= 0.36` = the 0.16 base plus this 20%),
and asserts the probe mark after a real skill cast. The mutation (0.20 -> 0.10) must stop the probe from ever firing.

⚠ `from_category` needs a real cast, so the judge drives the skill through `SkillExecutor.execute`, not a hand-fired event.
ASCII only.
"""
import io
import json

DATA = "src/main/resources/characters/1412.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/PeerageSkillPierceTest.java"
RULE = "peerage_skill_extra_pierce"

doc = json.load(io.open(DATA, encoding="utf-8"))
rules = doc["rules"] if isinstance(doc, dict) else doc
if any(isinstance(r, dict) and r.get("id") == RULE for r in rules):
    raise SystemExit("already shipped")

rules.append({
    "on": "CAST_SETUP",
    "id": RULE,
    "when": ["actor has_state \u7235\u4f4d", "from_category BPSKILL"],
    "do": [
        {"op": "MODIFY_ATTR", "attribute": "DEFENCE_IGNORE", "percent": 0.20,
         "until": "cast_end", "target": "self"},
    ],
    "source": ("1412 \u523b\u5f8b\u5fb7\u83c8 \u884c\u8ff9\uff08\u6587\u6863 `:105`\uff09\uff1a"
               "\u300c\u82e5\u5f53\u524d\u3010\u519b\u529f\u3011\u5df2\u5347\u7ea7\u4e3a\u3010\u7235\u4f4d\u3011\uff0c"
               "\u5219\u8be5\u89d2\u8272**\u9020\u6210\u6218\u6280\u4f24\u5bb3\u65f6\u989d\u5916\u65e0\u89c6\u76ee\u6807 20%** \u7684\u9632\u5fa1\u529b\u300d"),
    "note": ("\u2b50 2026-10-02\uff1a\u7528 **`until: \"cast_end\"`** \u2713 \u2014\u2014 \u5b83\u7684\u542b\u4e49\u9010\u5b57\u662f"
             "\u201c**raised inside the cast window, dropped when its events are done**\u201d \u2713\uff0c"
             "\u6b63\u597d\u662f\u201c**\u672c\u6b21\u65bd\u653e\u5185\u6709\u6548**\u201d \u2713\uff08\u26a0 \u4e0d\u80fd\u7528 `permanent` \u2717\uff1a"
             "\u90a3\u4f1a\u6bcf\u6b21\u6218\u6280\u90fd\u53e0 20% \u2717\uff09\u3002"
             "\u6761\u4ef6\u91cc\u7684 `from_category BPSKILL` \u4fdd\u8bc1\u53ea\u5728**\u6218\u6280**\u4e0a\u751f\u6548 \u2713\u3002"),
})

if not isinstance(doc, dict):
    raise SystemExit("1412.json must be an object")
doc["rules"] = rules
json.dump(doc, io.open(DATA, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   1412.json: the peerage pierce rides cast_end")

io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.Skill;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 1412\uff1a\u300c\u82e5\u5df2\u5347\u4e3a\u3010\u7235\u4f4d\u3011\u2026\u8be5\u89d2\u8272\u9020\u6210\u6218\u6280\u4f24\u5bb3\u65f6**\u989d\u5916\u65e0\u89c6 20%** \u9632\u5fa1\u300d (2026-10-02).
 *
 * <p>\u2b50 The effect lives only inside a cast (`cast_end`), so the attribute cannot be read afterwards. A HAND-BUILT probe rule on
 * DEALING_DAMAGE -- whose condition IS the attribute -- records the moment: `self_attr:DEFENCE_IGNORE >= 0.36` means the 0.16
 * base plus this 20% were both live when the skill's damage settled.
 */
public class PeerageSkillPierceTest {
    private static final int OWNER = 1412;
    private static final int ALLY = 1002;
    private static final int MONSTER = 1002011;
    private static final String PROBE = "probe_pierce_seen";

    /** \u2b50 With the peerage, the holder's skill damage carries the extra 20%. */
    @Test
    public void thePeerageHolderPiercesMoreOnSkillDamage() {
        Assertions.assertTrue(pierceSeen(6), "at 6 charge the merit holder is a peer, so its skill pierces 20% more");
    }

    /** \u26a0 Below the threshold there is no peerage, so only the 0.16 base is live and the probe must not fire. */
    @Test
    public void withoutThePeerageNothingExtraHappens() {
        Assertions.assertFalse(pierceSeen(1), "below 6 charge there is no peerage");
    }

    // ==================================================================

    /**
     * @param charge how many times she casts her skill first (each grants 1 charge; 6 promotes the merit holder)
     */
    private static boolean pierceSeen(int charge) {
        Character owner = CharacterFactory.create(OWNER, 80);
        Character ally = CharacterFactory.create(ALLY, 80);
        Battle battle = new Battle(List.of(owner, ally),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        List<com.laosun.aluminium.models.TriggerSpec> probeRules = new ArrayList<>(
                ally.getTriggerTable() == null ? List.of() : ally.getTriggerTable().rules());
        EffectSpec probe = new EffectSpec();
        TriggerSpecs.set(probe, "op", "APPLY_BUFF");
        TriggerSpecs.set(probe, "buff", PROBE);
        TriggerSpecs.set(probe, "permanent", true);
        TriggerSpecs.set(probe, "target", "self");
        probeRules.add(TriggerSpecs.rule(TriggerEvent.DEALING_DAMAGE.name(),
                List.of("self_attr:DEFENCE_IGNORE >= 0.36"), probe));
        ally.setTriggerTable(new TriggerTable(ALLY, probeRules));

        Skill skill = owner.getSkills().get(SkillType.SKILL);
        Assertions.assertNotNull(skill, "precondition: she has a skill");
        for (int i = 0; i < charge; i++) {
            SkillExecutor.execute(battle, skill, owner, List.of(ally));
            battle.processRequests();
        }
        Assertions.assertTrue(ally.getBuffManager().hasState("\u519b\u529f"),
                "precondition: the ally holds the merit");

        // \u2b50 A real cast of the ALLY's own skill, so `from_category BPSKILL` is true and the damage settles.
        Skill allySkill = ally.getSkills().get(SkillType.SKILL);
        Assertions.assertNotNull(allySkill, "precondition: the ally has a skill");
        SkillExecutor.execute(battle, allySkill, ally, List.of(battle.enemies.get(0)));
        battle.processRequests();
        return ally.getBuffManager().hasState(PROBE);
    }
}
''')
print("ok   judge written")
