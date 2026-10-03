"""1408: 「卡厄斯兰那免疫控制类负面状态」 (2026-10-02, item 41).

Document, verbatim (1408_白厄.html, 卡厄斯兰那的天赋): 「卡厄斯兰那**免疫控制类负面状态**，拥有 1 个强化普攻和 2 个强化战技，无法施放终结技。」

THE SHAPE IS A SHIPPED READER'S, copied verbatim rather than composed: 1309's ultimate carries
    {"op": "RESIST_DEBUFF", "kind": "control", "percent": 1.0, "buff": "协奏", "permanent": true, "target": "self"}
for 「处于【协奏】状态时，知更鸟免疫控制类负面状态」 -- i.e. `percent: 1.0` IS immunity, `kind: control` IS the control class, and
the `buff:` link is what scopes it to the state. 1013 ships the same op at `percent: 0.35` for 「抵抗控制类负面状态的概率提高 35%」.
The op's own note says it was built for this sentence family: "「抵抗控制类负面状态的概率提高35%」 / 「免疫控制类负面状态」".

\u26a0 So nothing is invented here: the resistance rides the SAME rule that applies 变身, linked to 变身 -- which is the "during the
transformation" scoping this span already had to fix once (item 35).
ASCII only.
"""
import io
import json

DATA = "src/main/resources/characters/1408.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/ControlImmunityTest.java"
RULE = "ult_transformation"

doc = json.load(io.open(DATA, encoding="utf-8"))
rules = doc["rules"] if isinstance(doc, dict) else doc

hit = [r for r in rules if isinstance(r, dict) and r.get("id") == RULE]
if len(hit) != 1:
    raise SystemExit("expected exactly one transformation rule, found " + str(len(hit)))

rule = hit[0]
kept = [e for e in (rule.get("do") or []) if not (isinstance(e, dict) and e.get("op") == "RESIST_DEBUFF")]
kept.append({"op": "RESIST_DEBUFF", "kind": "control", "percent": 1.0,
             "buff": "\u53d8\u8eab", "permanent": True, "target": "self"})
rule["do"] = kept
rule["source"] = ((rule.get("source") or "") +
                  "\n\u2b50 2026-10-02\uff08\u6587\u6863\uff0c\u5361\u5384\u65af\u5170\u90a3\u7684\u5929\u8d4b\uff09\uff1a\u300c\u5361\u5384\u65af\u5170\u90a3**\u514d\u75ab\u63a7\u5236\u7c7b\u8d1f\u9762\u72b6\u6001**\u300d\u2713")
rule["note"] = ((rule.get("note") or "") +
                "\n\u2b50 2026-10-02\uff1a\u514d\u75ab\u7528 **`RESIST_DEBUFF{kind: control, percent: 1.0}`** \u2713 \u2014\u2014 "
                "\u26a0 **\u62fc\u6cd5\u9010\u5b57\u6284\u81ea `1309`** \u2713\uff08\u5b83\u7684\u7ec8\u7ed3\u6280\u91cc\u5c31\u662f `percent: 1.0` \u2f8b `buff: \"\u534f\u594f\"` \u2713\uff0c"
                "\u6e90\u6587\u6b63\u662f\u300c**\u514d\u75ab**\u63a7\u5236\u7c7b\u8d1f\u9762\u72b6\u6001\u300d \u2713\uff09\uff1b\u2f8b`1013` \u7528\u540c\u4e00 op \u7684 **0.35** \u505a\u201c\u62b5\u6297\u201d \u2713"
                "\u21d2 \u6240\u4ee5 `1.0` \u5c31\u662f\u514d\u75ab \u2713\u3002\u26a0 **\u7ed1\u5230\u3010\u53d8\u8eab\u3011** \u2713\uff08`buff:` \u2713\uff09\u21d2 \u201c**\u5361\u5384\u65af\u5170\u90a3**\u514d\u75ab\u201d\u7684\u4f5c\u7528\u57df \u2713\u3002")

if not isinstance(doc, dict):
    raise SystemExit("1408.json must be an object")
doc["rules"] = rules
json.dump(doc, io.open(DATA, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   1408.json: the transformed form cannot be controlled")

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
 * 1408\uff1a\u300c\u5361\u5384\u65af\u5170\u90a3**\u514d\u75ab\u63a7\u5236\u7c7b\u8d1f\u9762\u72b6\u6001**\u300d (2026-10-02).
 *
 * <p>\u2b50 SAME SCENE, ONE VARIABLE: a spare unit applies \u3010\u51bb\u7ed3\u3011 (a control, from the document's own glossary) to her; the only
 * difference between the two runs is whether she is transformed. The applier carries a hand-built table so the judge can cast a
 * control on demand -- and that unit's own content is irrelevant here, because only HER state is asserted.
 */
public class ControlImmunityTest {
    private static final int OWNER = 1408;
    private static final int APPLIER = 1002;
    private static final int MONSTER = 1002011;
    private static final String STATE = "\u53d8\u8eab";
    private static final String FREEZE = "\u51bb\u7ed3";

    /** \u2b50 Transformed: the control does not land. */
    @Test
    public void theTransformedFormShrugsOffControl() {
        Assertions.assertFalse(controlledAfter(true), "\u300c\u5361\u5384\u65af\u5170\u90a3\u514d\u75ab\u63a7\u5236\u7c7b\u8d1f\u9762\u72b6\u6001\u300d");
    }

    /** \u26a0 Untransformed: the same control lands, so the immunity really is the transformation's. */
    @Test
    public void withoutTheTransformationTheControlLands() {
        Assertions.assertTrue(controlledAfter(false), "\u300c\u53d8\u8eab\u671f\u95f4\u300d-- outside it she is controlable");
    }

    // ==================================================================

    private static boolean controlledAfter(boolean transform) {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Character applier = CharacterFactory.create(APPLIER, 80);
        applier.setTriggerTable(new TriggerTable(APPLIER, List.of(
                TriggerSpecs.rule(TriggerEvent.BATTLE_START.name(), List.of(), control()))));

        Battle battle = new Battle(List.of(owner, applier),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));

        if (transform) {
            battle.startBattle();
            battle.processRequests();
            Skill ult = owner.getSkills().get(SkillType.ULTRA);
            Assertions.assertNotNull(ult, "precondition: she has an ultimate");
            SkillExecutor.execute(battle, ult, owner, List.of(owner));
            battle.processRequests();
            Assertions.assertTrue(owner.getBuffManager().hasState(STATE), "precondition: the transformation is on");
        } else {
            // \u26a0 The applier's control fires at BATTLE_START, so the untransformed run must let it land BEFORE anything else --
            // hence the same startBattle(), and the assertion below is that the control is there and 变身 is not.
            battle.startBattle();
            battle.processRequests();
            Assertions.assertFalse(owner.getBuffManager().hasState(STATE), "precondition: not transformed");
        }
        return owner.getBuffManager().hasState(FREEZE);
    }

    /** APPLY_CONTROL of \u3010\u51bb\u7ed3\u3011 on the rule owner's own target -- the spare aims it at 1408 through the trigger's target. */
    private static EffectSpec control() {
        EffectSpec e = new EffectSpec();
        TriggerSpecs.set(e, "op", "APPLY_CONTROL");
        TriggerSpecs.set(e, "control", FREEZE);
        TriggerSpecs.set(e, "turns", 3);
        TriggerSpecs.set(e, "target", "all_enemies");
        return e;
    }
}
''')
print("ok   judge written")
