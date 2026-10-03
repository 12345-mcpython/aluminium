"""DIAGNOSTIC -- does an after-attack rule fire at all for 1408's transformed form? (2026-10-02)

The heal on attack (「变身期间…施放攻击后回复等同于自身生命上限 20% 的生命值」) measured 0.0 across three attempts, and that
single number cannot tell "the rule never fired" apart from "it fired and `HEAL` did nothing". This script splits the two by
replacing the EFFECT with an observable mark -- and it tests BOTH candidate events at once, so one run answers which one fires:

    ALLY_ATTACK     (SkillExecutor:203)  ->  marks  diag_mark_ally
    ATTACK_FINISHED (Battle:2489)        ->  marks  diag_mark_finished

\u26a0 This is a DIAGNOSTIC, not a shipment: the `HEAL` is gone, the marks are throwaway, and the caller is expected to restore
1408.json afterwards. Nothing here belongs in the tree.
ASCII only.
"""
import io
import json

DATA = "src/main/resources/characters/1408.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/DiagAfterAttackTest.java"
STATE = "\u53d8\u8eab"
IDS = ("diag_mark_on_ally_attack", "diag_mark_on_attack_finished")

doc = json.load(io.open(DATA, encoding="utf-8"))
rules = doc["rules"] if isinstance(doc, dict) else doc
rules = [r for r in rules if not (isinstance(r, dict) and r.get("id") in IDS)]

for rid, event, mark in (("diag_mark_on_ally_attack", "ALLY_ATTACK", "diag_mark_ally"),
                         ("diag_mark_on_attack_finished", "ATTACK_FINISHED", "diag_mark_finished")):
    rules.append({
        "on": event,
        "id": rid,
        "when": ["actor == self", "self has_state " + STATE],
        "do": [{"op": "HEAL", "amount": 200, "target": "self"},
               {"op": "APPLY_BUFF", "buff": mark, "permanent": True, "target": "self"}],
        "note": "DIAGNOSTIC ONLY (2026-10-02) -- not a shipment; the caller restores the file afterwards.",
    })

if not isinstance(doc, dict):
    raise SystemExit("1408.json must be an object")
doc["rules"] = rules
io.open(DATA, "w", encoding="utf-8", newline="\n").write(
    json.dumps(doc, ensure_ascii=False, indent=2))
print("ok   1408.json: two diagnostic marks installed")

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

/** DIAGNOSTIC (2026-10-02): which after-attack event reaches 1408's table while she is transformed? */
public class DiagAfterAttackTest {
    private static final int OWNER = 1408;
    private static final int MONSTER = 1002011;
    private static final String STATE = "\u53d8\u8eab";

    @Test
    public void reportWhichMarksAppeared() {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        Skill ult = owner.getSkills().get(SkillType.ULTRA);
        SkillExecutor.execute(battle, ult, owner, List.of(owner));
        battle.processRequests();
        boolean transformed = owner.getBuffManager().hasState(STATE);

        // \u26a0 Hurt her FIRST, so a heal has somewhere to go: fully-qualified so the diagnostic needs no new import.
        battle.applyTrueDamage(battle.enemies.get(0), owner, com.laosun.aluminium.enums.DamageElement.ICE,
                owner.getMaxHp() * 0.5);
        battle.processRequests();
        double hurt = owner.getCurrentHp();
        double maxHp = owner.getMaxHp();
        Skill basic = owner.getSkills().get(SkillType.COMMON);
        boolean hasBasic = basic != null;
        if (hasBasic) {
            SkillExecutor.execute(battle, basic, owner, List.of(battle.enemies.get(0)));
            battle.processRequests();
        }

        Assertions.fail("PROBE transformed=" + transformed + " hasBasic=" + hasBasic
                + " hurt=" + hurt + " of " + maxHp + " after=" + owner.getCurrentHp()
                + " allyAttackMark=" + owner.getBuffManager().hasState("diag_mark_ally")
                + " attackFinishedMark=" + owner.getBuffManager().hasState("diag_mark_finished"));
    }
}
''')
print("ok   judge written")
