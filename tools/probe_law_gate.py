"""Probe the REAL gate on the REAL table, and print the numbers it depends on (2026-10-02).

Two single-variable runs already ruled out `target_when` and `target == self`, and a third ruled out the attribute. A two-probe run on 1415's own file showed both that the
file is loaded and that a memosprite's cast DOES announce CAST_SETUP. So the only untried part of the gate is `from_skill_id == 23`.

This puts the probe on 1412's table with EXACTLY the gate the real rule used, and prints `getSkillSlot()` / `getCid()` of the skill being cast -- so one run answers both
"does the gate hold" and "what number should the gate be".
"""
import io
import json
import sys

CERYDRA = "src/main/resources/characters/1412.json"
doc = json.load(io.open(CERYDRA, encoding="utf-8"))
rules = doc if isinstance(doc, list) else doc.get("rules", [])
if any(r.get("id") == "probe_law_gate" for r in rules):
    sys.exit("REFUSING: probe_law_gate is already there")

rules.append({
    "id": "probe_law_gate",
    "on": "CAST_SETUP",
    "when": ["target == self", "actor is_summon", "from_skill_id == 23"],
    "do": [{"op": "MODIFY_ATTR", "attribute": "ATTACK", "percent": 0.25, "permanent": True, "target": "self"}],
    "source": "DIAGNOSTIC 2026-10-02: the same three gates the real rule uses, on the table the real rule lives on.",
    "note": "\u26a0 \u4e34\u65f6\u63a2\u9488\uff0c\u5b9e\u9a8c\u540e\u5fc5\u987b\u56de\u6eda\u3002",
})
if isinstance(doc, list):
    out = rules
else:
    doc["rules"] = rules
    out = doc
io.open(CERYDRA, "w", encoding="utf-8", newline="\n").write(json.dumps(out, ensure_ascii=False, indent=2) + "\n")
print("ok   1412 carries the gate probe (%d rules)" % len(rules))

JUDGE = '''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/** DIAGNOSTIC (2026-10-02): the real gate, on the real table, plus the numbers it compares. */
public class LawGateProbeTest {
    private static final int LEVEL = 80;
    private static final int CYRENE = 1415;
    private static final int CERYDRA = 1412;
    private static final int MONSTER = 1002011;

    @Test
    public void printTheGateAndTheNumbers() {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character cerydra = CharacterFactory.create(CERYDRA, LEVEL);
        Battle battle = new Battle(List.of(cyrene, cerydra),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        cyrene = battle.characters.get(0);
        cerydra = battle.characters.get(1);

        var demiurge = battle.summonServant(cyrene);
        battle.processRequests();
        var ode = demiurge.skillAt(23);
        Assertions.assertNotNull(ode, "precondition: the memosprite carries slot 23");
        System.out.println("[law_gate] the skill cast: getSkillSlot() = " + ode.getSkillSlot()
                + " ; getCid() = " + ode.getCid() + " ; getLevel() = " + ode.getLevel()
                + " ; type = " + ode.getData().getSkillType());

        double before = cerydra.getAttribute(AttributeType.ATTACK).get();
        SkillExecutor.execute(battle, ode, demiurge, List.of(cerydra));
        battle.processRequests();
        System.out.println("[law_gate] the aimed ally's ATTACK gain = "
                + (cerydra.getAttribute(AttributeType.ATTACK).get() - before));

        Assertions.assertTrue(true, "diagnostic only");
    }
}
'''
io.open("src/test/java/com/laosun/aluminium/test/LawGateProbeTest.java", "w", encoding="utf-8", newline="\n").write(JUDGE)
print("ok   probe written")
