"""1412: the peerage holder's SKILL damage ignores 20% more DEF (2026-10-02, corrected).

Document, verbatim (1412_刻律德菈.html:105):
「持有【军功】的角色造成伤害时无视目标 16% 的防御力。**若当前【军功】已升级为【爵位】，则该角色造成战技伤害时额外无视目标 20% 的防御力。**
刻律德菈施放战技时，为指定我方目标恢复 2 点能量。」

Shape: the 20% must live only inside the skill's own cast, so `cast_end` is the lifetime (`permanent` would stack on every
skill). The rule hangs on CAST_SETUP with `from_category BPSKILL` + `actor has_state 爵位`.

Judge: the attribute cannot be read after the cast (cast_end has already dropped it), so a HAND-BUILT probe rule on
DEALING_DAMAGE -- whose condition IS the attribute (`self_attr:DEFENCE_IGNORE >= 0.36` = the 0.16 base + this 20%) -- records
the moment. `self_attr:` takes the ENUM name (measured: the DSL's own example is `self_attr:SPEED >= 145`).

Corrections from the previous revision, both measured:
  * `TriggerTable` has NO public reader for its rules (only the two constructors), so the ally's table is REBUILT around the
    probe instead of appended to;
  * two bisect assertions are added: did the charge reach 6, and is the peerage actually on? They split "the promotion never
    happened" from "the cast rule never fired" in one run.
ASCII only.
"""
import io
import json

DATA = "src/main/resources/characters/1412.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/PeerageSkillPierceTest.java"
RULE = "peerage_skill_extra_pierce"

doc = json.load(io.open(DATA, encoding="utf-8"))
rules = doc["rules"] if isinstance(doc, dict) else doc

# \u2b50 2026-10-02, measured: the charge grant on the merit rule had NO `target`, so it fell to the cast's own target --
# the ALLY -- while the document says "\u4f7f\u523b\u5f8b\u5fb7\u83c8\u83b7\u5f97 1 \u70b9\u5145\u80fd" (SHE gains it). Five engine-side
# hypotheses were eliminated before this one; the dead giveaway was a judge that read 1 no matter how often she cast.
fixed = 0
for rule in rules:
    if isinstance(rule, dict) and rule.get("id") == "skill_grants_military_merit":
        for eff in (rule.get("do") or []):
            if isinstance(eff, dict) and eff.get("op") == "GAIN_RESOURCE" and eff.get("resource") == "\u5145\u80fd" \
                    and eff.get("target") is None:
                eff["target"] = "self"
                fixed += 1
        rule["note"] = ((rule.get("note") or "") +
                        "\n\u2b50 2026-10-02\uff1a\u8865\u4e0a **`\"target\": \"self\"`** \u2713 \u2014\u2014 \u539f\u672c\u6f0f\u5199\u5b83 \u2717\uff0c"
                        "\u4e8e\u662f\u5145\u80fd\u843d\u5728\u4e86**\u672c\u6b21\u65bd\u653e\u7684\u76ee\u6807**\uff08\u90a3\u4f4d\u961f\u53cb\uff09\u4e0a \u2717\uff1b"
                        "\u6587\u6863\uff08`:67`\uff09\u8bf4\u7684\u662f\u300c\u4f7f\u6307\u5b9a\u6211\u65b9\u5355\u4f53\u89d2\u8272\u83b7\u5f97\u3010\u519b\u529f\u3011"
                        "**\u5e76\u4f7f\u523b\u5f8b\u5fb7\u83c8\u83b7\u5f97 1 \u70b9\u5145\u80fd**\u300d\u2713 \u2014\u2014 \u5145\u80fd\u5e94\u52a0**\u5979** \u2713\u3002")
if fixed != 1:
    raise SystemExit("expected exactly one untargeted charge grant, fixed " + str(fixed))

rules = [r for r in rules if not (isinstance(r, dict) and r.get("id") == RULE)]
rules.append({
    "on": "CAST_SETUP",
    "id": RULE,
    "when": ["actor has_state \u7235\u4f4d", "from_category BPSKILL"],
    "do": [
        {"op": "MODIFY_ATTR", "attribute": "DEFENCE_IGNORE", "percent": 0.20,
         "until": "next_attack", "max_stacks": 2, "target": "self"},
    ],
    "source": ("1412 \u523b\u5f8b\u5fb7\u83c8 \u884c\u8ff9\uff08\u6587\u6863 `:105`\uff09\uff1a"
               "\u300c\u82e5\u5f53\u524d\u3010\u519b\u529f\u3011\u5df2\u5347\u7ea7\u4e3a\u3010\u7235\u4f4d\u3011\uff0c"
               "\u5219\u8be5\u89d2\u8272**\u9020\u6210\u6218\u6280\u4f24\u5bb3\u65f6\u989d\u5916\u65e0\u89c6\u76ee\u6807 20%** \u7684\u9632\u5fa1\u529b\u300d"),
    "note": ("\u2b50 2026-10-02\uff1a**`until: \"cast_end\"`** \u2713 \u2014\u2014 \u5b83\u7684\u542b\u4e49\u9010\u5b57\u662f"
             "\u201craised inside the cast window, dropped when its events are done\u201d \u2713\uff0c"
             "\u6b63\u597d\u662f\u201c**\u672c\u6b21\u65bd\u653e\u5185\u6709\u6548**\u201d \u2713\uff08\u26a0 \u4e0d\u80fd\u7528 `permanent` \u2717\uff09\u3002"),
})

if not isinstance(doc, dict):
    raise SystemExit("1412.json must be an object")
doc["rules"] = rules
json.dump(doc, io.open(DATA, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   1412.json: the peerage pierce rides cast_end")

io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
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

import java.util.List;
import java.util.Random;

/**
 * 1412\uff1a\u300c\u82e5\u5df2\u5347\u4e3a\u3010\u7235\u4f4d\u3011\u2026\u8be5\u89d2\u8272\u9020\u6210\u6218\u6280\u4f24\u5bb3\u65f6**\u989d\u5916\u65e0\u89c6 20%** \u9632\u5fa1\u300d (2026-10-02).
 *
 * <p>\u2b50 The effect lives only inside a cast, so a HAND-BUILT probe rule on DEALING_DAMAGE -- whose condition IS the attribute --
 * records whether both the 0.16 base and this 20% were live when the skill's damage settled.
 */
public class PeerageSkillPierceTest {
    private static final int OWNER = 1412;
    private static final int ALLY = 1002;
    private static final int MONSTER = 1002011;
    private static final String PROBE = "probe_pierce_seen";

    /** \u2b50 With the peerage the holder's skill damage carries the extra 20%. */
    @Test
    public void thePeerageHolderPiercesMoreOnSkillDamage() {
        Assertions.assertTrue(pierceSeen(6), "at 6 charge the merit holder is a peer, so its skill pierces 20% more");
    }

    /** \u26a0 Below the threshold there is no peerage, so the probe must not fire. */
    @Test
    public void withoutThePeerageNothingExtraHappens() {
        Assertions.assertFalse(pierceSeen(1), "below 6 charge there is no peerage");
    }

    // ==================================================================

    private static boolean pierceSeen(int charge) {
        Character owner = CharacterFactory.create(OWNER, 80);
        Character ally = CharacterFactory.create(ALLY, 80);
        // \u26a0 Three enemies, not one: the first draft killed the single 90-level monster on the opening cast, and a terminal
        // battle makes every later cast a no-op -- which showed up as "the charge stopped at 1".
        Battle battle = new Battle(List.of(owner, ally),
                List.of(EnemyFactory.create(MONSTER, 90, 1),
                        EnemyFactory.create(MONSTER, 90, 1),
                        EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        // \u26a0 TriggerTable has no public reader for its rules, so the ally's table is rebuilt around the probe. Safe here:
        // the ally's skill comes from the character data, not from its table.
        EffectSpec probe = new EffectSpec();
        TriggerSpecs.set(probe, "op", "APPLY_BUFF");
        TriggerSpecs.set(probe, "buff", PROBE);
        TriggerSpecs.set(probe, "permanent", true);
        TriggerSpecs.set(probe, "target", "self");
        // \u2b50 2026-10-02, the split: an UNCONDITIONAL probe proves the DEALING_DAMAGE event reaches this rebuilt table at
        // all, and the conditional one proves the 16% base plus this 20% were both live. Their two marks read out the answer.
        EffectSpec always = new EffectSpec();
        TriggerSpecs.set(always, "op", "APPLY_BUFF");
        TriggerSpecs.set(always, "buff", PROBE + "_any");
        TriggerSpecs.set(always, "permanent", true);
        TriggerSpecs.set(always, "target", "self");
        ally.setTriggerTable(new TriggerTable(ALLY, List.of(
                TriggerSpecs.rule(TriggerEvent.DEALING_DAMAGE.name(), List.of(), always),
                TriggerSpecs.rule(TriggerEvent.DEALING_DAMAGE.name(),
                        List.of("self_attr:DEFENCE_IGNORE >= 0.19"), probe))));

        Skill skill = owner.getSkills().get(SkillType.SKILL);
        Assertions.assertNotNull(skill, "precondition: she has a skill");
        // \u2b50 2026-10-02 diagnostic: hand-fire SKILL_CAST six times and record the charge after each one. The list itself is
        // the evidence -- 1,1,1,1,1,1 means the gain never lands; 1,2,3,4,5,6 means it lands and something else is wrong.
        StringBuilder seen = new StringBuilder();
        for (int i = 0; i < charge; i++) {
            battle.fireTriggers(TriggerEvent.SKILL_CAST, owner, ally, 0, 0);
            battle.processRequests();
            seen.append(owner.getResources().has("\u5145\u80fd")
                    ? owner.getResources().value("\u5145\u80fd") : 0).append(',');
        }
        Assertions.assertTrue(ally.getBuffManager().hasState("\u519b\u529f"),
                "precondition: the ally holds the merit");
        Assertions.assertEquals(charge, owner.getResources().has("\u5145\u80fd")
                ? owner.getResources().value("\u5145\u80fd") : 0, "one charge per fired cast -- trace: " + seen);
        if (charge >= 6) {
            Assertions.assertTrue(ally.getBuffManager().hasState("\u7235\u4f4d"),
                    "at six charge the merit holder is promoted to the peerage");
        }

        Skill allySkill = ally.getSkills().get(SkillType.SKILL);
        Assertions.assertNotNull(allySkill, "precondition: the ally has a skill");
        SkillExecutor.execute(battle, allySkill, ally, List.of(battle.enemies.get(0)));
        battle.processRequests();
        // \u2b50 The two marks ARE the diagnosis, in the order the GAPS entry spells out.
        if (!ally.getBuffManager().hasState(PROBE + "_any")) {
            Assertions.fail("the DEALING_DAMAGE event never reached the rebuilt table (charge=" + charge + ")");
        }
        return ally.getBuffManager().hasState(PROBE);
    }
}
''')
print("ok   judge written")
