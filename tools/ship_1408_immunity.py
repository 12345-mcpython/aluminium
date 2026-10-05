"""1408: 「卡厄斯兰那免疫控制类负面状态」 (2026-10-02, item 41).

Document, verbatim (1408_白厄.html, 卡厄斯兰那的天赋): 「卡厄斯兰那**免疫控制类负面状态**，拥有 1 个强化普攻和 2 个强化战技，无法施放终结技。」

THE SHAPE IS A SHIPPED READER'S, copied verbatim rather than composed: 1309's ultimate carries
    {"op": "RESIST_DEBUFF", "kind": "control", "percent": 1.0, "buff": "协奏", "permanent": true, "target": "self"}
for 「处于【协奏】状态时，知更鸟免疫控制类负面状态」 -- i.e. `percent: 1.0` IS immunity, `kind: control` IS the control class, and
the `buff:` link is what scopes it to the state. 1013 ships the same op at `percent: 0.35` for 「抵抗控制类负面状态的概率提高 35%」.
The op's own note says it was built for this sentence family: "「抵抗控制类负面状态的概率提高35%」 / 「免疫控制类负面状态」".

⚠ So nothing is invented here: the resistance rides the SAME rule that applies 变身, linked to 变身 -- which is the "during the
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
             "buff": "变身", "permanent": True, "target": "self"})
rule["do"] = kept
rule["source"] = ((rule.get("source") or "") +
                  "\n⭐ 2026-10-02（文档，卡厄斯兰那的天赋）：「卡厄斯兰那**免疫控制类负面状态**」✓")
rule["note"] = ((rule.get("note") or "") +
                "\n⭐ 2026-10-02：免疫用 **`RESIST_DEBUFF{kind: control, percent: 1.0}`** ✓ —— "
                "⚠ **拼法逐字抄自 `1309`** ✓（它的终结技里就是 `percent: 1.0` ⾋ `buff: \"协奏\"` ✓，"
                "源文正是「**免疫**控制类负面状态」 ✓）；⾋`1013` 用同一 op 的 **0.35** 做“抵抗” ✓"
                "⇒ 所以 `1.0` 就是免疫 ✓。⚠ **绑到【变身】** ✓（`buff:` ✓）⇒ “**卡厄斯兰那**免疫”的作用域 ✓。")

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
 * 1408：「卡厄斯兰那**免疫控制类负面状态**」 (2026-10-02).
 *
 * <p>⭐ SAME SCENE, ONE VARIABLE: a spare unit applies 【冻结】 (a control, from the document's own glossary) to her; the only
 * difference between the two runs is whether she is transformed. The applier carries a hand-built table so the judge can cast a
 * control on demand -- and that unit's own content is irrelevant here, because only HER state is asserted.
 */
public class ControlImmunityTest {
    private static final int OWNER = 1408;
    private static final int APPLIER = 1002;
    private static final int MONSTER = 1002011;
    private static final String STATE = "变身";
    private static final String FREEZE = "冻结";

    /** ⭐ Transformed: the control does not land. */
    @Test
    public void theTransformedFormShrugsOffControl() {
        Assertions.assertFalse(controlledAfter(true), "「卡厄斯兰那免疫控制类负面状态」");
    }

    /** ⚠ Untransformed: the same control lands, so the immunity really is the transformation's. */
    @Test
    public void withoutTheTransformationTheControlLands() {
        Assertions.assertTrue(controlledAfter(false), "「变身期间」-- outside it she is controlable");
    }

    // ==================================================================

    private static boolean controlledAfter(boolean transform) {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Character applier = CharacterFactory.create(APPLIER, 80);
        applier.setTriggerTable(new TriggerTable(APPLIER, List.of(
                TriggerSpecs.rule(TriggerEvent.SKILL_CAST.name(), List.of("actor == self"), control()))));

        Battle battle = new Battle(List.of(owner, applier),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        if (transform) {
            Skill ult = owner.getSkills().get(SkillType.ULTRA);
            Assertions.assertNotNull(ult, "precondition: she has an ultimate");
            SkillExecutor.execute(battle, ult, owner, List.of(owner));
            battle.processRequests();
            Assertions.assertTrue(owner.getBuffManager().hasState(STATE), "precondition: the transformation is on");
        } else {
            Assertions.assertFalse(owner.getBuffManager().hasState(STATE), "precondition: not transformed");
        }
        Assertions.assertFalse(owner.getBuffManager().hasState(FREEZE),
                "precondition: nothing has controlled her yet");

        // ⭐ NOW the control is aimed at her -- AFTER the transformation, which is the whole point. Measured: a control that lands at
        // BATTLE_START is simply there when the transformation begins, and immunity cannot retroactively remove it.
        Skill theirs = applier.getSkills().get(SkillType.SKILL);
        Assertions.assertNotNull(theirs, "precondition: the applier has a skill");
        SkillExecutor.execute(battle, theirs, applier, List.of(battle.enemies.getFirst()));
        battle.processRequests();
        return owner.getBuffManager().hasState(FREEZE);
    }

    /** APPLY_CONTROL of 【冻结】 on the rule owner's own target -- the spare aims it at 1408 through the trigger's target. */
    private static EffectSpec control() {
        EffectSpec e = new EffectSpec();
        TriggerSpecs.set(e, "op", "APPLY_CONTROL");
        TriggerSpecs.set(e, "control", FREEZE);
        TriggerSpecs.set(e, "turns", 3);
        // ⚠ `other_allies`, NOT `all_enemies`: from the applier's side the "enemies" are the monster, and the point of the judge is to
        // aim the control AT HER. Measured: with `all_enemies` the control went to the monster and the untransformed run never saw it.
        TriggerSpecs.set(e, "target", "other_allies");
        return e;
    }
}
''')
print("ok   judge written")
