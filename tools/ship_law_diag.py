"""Slot 23, the discriminating experiment (2026-10-02). ONE thing changes: the target filter.

The shipped shape for 「持有【军功】的角色」 would be `target: "all_allies"` + `target_when: ["target has_state 军功"]` (1103 uses exactly that for 「所有触电状态下的敌方目标」). The
first attempt used it and NOTHING gained -- 0.0 on the marked ally and on the unmarked one -- so either the gate (`target == self` / `actor is_summon` / `from_skill_id`)
never held, or the filter swallowed both.

This run keeps `all_allies` and drops ONLY `target_when`, and the judge prints the gain for a marked ally AND an unmarked one:
  * marked gains, unmarked doesn't -> the gate is fine and the filter is the blocker;
  * neither gains                  -> the gate itself never held, and the question moves there.
"""
import io
import json
import sys

CERYDRA = "src/main/resources/characters/1412.json"
SE = "src/main/resources/data/skill_effects.json"
SLOT = 23
MARK = "军功"

doc = json.load(io.open(CERYDRA, encoding="utf-8"))
rules = doc if isinstance(doc, list) else doc.get("rules", [])
RULE_ID = "memosprite_ode_of_law_raises_the_meritorious"
if any(r.get("id") == RULE_ID for r in rules):
    sys.exit("REFUSING: %s is already there" % RULE_ID)

rules.append({
    "id": RULE_ID,
    "on": "CAST_SETUP",
    "when": ["target == self", "actor is_summon", "from_skill_id == " + str(SLOT)],
    "do": [{
        "op": "MODIFY_ATTR",
        "attribute": "CRIT_ATTACK",
        "percent_from_cast_param": 0,
        "permanent": True,
        "target": "all_allies",              # ⚠ NO target_when in this run -- that is the one variable
    }],
    "source": ("1415 昔涟 忆灵技能 17 「献予「律法」之诗」（数据槽位 23，SkillID 1141523）："
               "「整场生效，对刻律德菈施放后，**持有【" + MARK + "】的角色暴击伤害提高 #1%**。」"),
    "note": ("⚠ **本次是一次分辨实验**：与上一次相比**只去掉** "
             "`target_when`（保留 `all_allies`）。若有印记的那位现在加成 ⇒ 门是好的、过滤器是拦路的；"
             "若仍不动 ⇒ 门本身没成立。"),
})

if isinstance(doc, list):
    out = rules
else:
    doc["rules"] = rules
    out = doc
io.open(CERYDRA, "w", encoding="utf-8", newline="\n").write(json.dumps(out, ensure_ascii=False, indent=2) + "\n")
print("ok   1412 carries the rule WITHOUT target_when (%d rules)" % len(rules))

effects = json.load(io.open(SE, encoding="utf-8"))
if str(SLOT) not in effects.get("11415", {}):
    effects.setdefault("11415", {})[str(SLOT)] = {
        "effect": "Rules",
        "source": "1415 昔涟 忆灵技能 17 「献予「律法」之诗」（数据槽位 23）：工作在规则侧。",
        "note": "⭐ 没有条目就不可交付。",
    }
    io.open(SE, "w", encoding="utf-8", newline="\n").write(json.dumps(effects, ensure_ascii=False, indent=2) + "\n")
    print("ok   skill_effects.json: 11415/%d = Rules" % SLOT)

JUDGE = '''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * DIAGNOSTIC (2026-10-02): which door stopped slot 23's crit clause?
 *
 * <p>The judge only PRINTS. It is deliberately one experiment with one variable -- the content in this run has no `target_when` -- so that the two numbers decide between
 * "the gate never held" and "the filter swallowed both".
 */
public class LawOdeDiagnosticTest {
    private static final int LEVEL = 80;
    private static final int CYRENE = 1415;
    private static final int CERYDRA = 1412;
    private static final int PLAIN = 1002;
    private static final int MONSTER = 1002011;
    private static final String MARK = "\\u519b\\u529f";

    @Test
    public void printTheTwoGains() {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character cerydra = CharacterFactory.create(CERYDRA, LEVEL);
        Character plain = CharacterFactory.create(PLAIN, LEVEL);
        Battle battle = new Battle(List.of(cyrene, cerydra, plain),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        cyrene = battle.characters.get(0);
        cerydra = battle.characters.get(1);
        plain = battle.characters.get(2);

        EffectSpec mark = new EffectSpec();
        TriggerSpecs.set(mark, "op", "APPLY_BUFF");
        TriggerSpecs.set(mark, "buff", MARK);
        TriggerSpecs.set(mark, "permanent", Boolean.TRUE);
        TriggerSpecs.set(mark, "target", "self");
        cerydra.setTriggerTable(new TriggerTable(CERYDRA, List.of(
                TriggerSpecs.rule("BATTLE_START", List.of(), mark))));
        battle.fireTriggers(TriggerEvent.BATTLE_START);

        double markedBefore = cerydra.getAttribute(AttributeType.CRIT_ATTACK).get();
        double plainBefore = plain.getAttribute(AttributeType.CRIT_ATTACK).get();
        boolean markedHasState = cerydra.getBuffManager().hasState(MARK);

        var demiurge = battle.summonServant(cyrene);
        var ode = demiurge.skillAt(23);
        Assertions.assertNotNull(ode, "precondition: the memosprite carries slot 23");
        SkillExecutor.execute(battle, ode, demiurge, List.of(cerydra));
        battle.processRequests();

        System.out.println("[law_diag] the aim was the marked ally; it has the state = " + markedHasState
                + " ; marked CRIT_ATTACK gain = " + (cerydra.getAttribute(AttributeType.CRIT_ATTACK).get() - markedBefore)
                + " ; unmarked gain = " + (plain.getAttribute(AttributeType.CRIT_ATTACK).get() - plainBefore));

        Assertions.assertTrue(true, "diagnostic only");
    }
}
'''
io.open("src/test/java/com/laosun/aluminium/test/LawOdeDiagnosticTest.java", "w", encoding="utf-8", newline="\n").write(JUDGE)
print("ok   diagnostic written")
