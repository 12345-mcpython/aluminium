"""A VALID comparison for the swap question (round 6 of the goal).

What was wrong last time: the "direct cast" number came from a scene whose trigger table had been REPLACED, so `level_convention`
never ran and slot 9 sat at level 1, while the content runs at level 15. Two levels are not a comparison.

The valid one: run the SAME content scene twice, once with the swap in the rule and once without it (that is the mutant). Both
scenes then have his real table, his real levels, and the same enemy -- the only difference is the swap, so the damage difference
is attributable to it.
"""
import io
import json
import sys

CHAR = "src/main/resources/characters/1404.json"
PROBE = "src/test/java/com/laosun/aluminium/test/SwapDamageProbeTest.java"

with io.open(CHAR, encoding="utf-8") as handle:
    doc = json.load(handle)
rule = next(entry for entry in doc["rules"]
            if isinstance(entry, dict) and entry.get("id") == "turn_start_autocasts_skill")
if not any(effect.get("op") == "REPLACE_SKILL" for effect in rule["do"]):
    rule["do"].insert(0, {"op": "REPLACE_SKILL", "skill": "SKILL", "skill_id": 9, "turns": 1, "target": "self"})
with io.open(CHAR, "w", encoding="utf-8", newline="\n") as handle:
    json.dump(doc, handle, ensure_ascii=False, indent=2)
    handle.write("\n")
print("ok   the swap is back in the rule (turns: 1)")

io.open(PROBE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.buff.StateBuff;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/** Probe only: the content scene's commanded-cast damage, with his REAL table (so levels are the shipped ones). */
public class SwapDamageProbeTest {
    private static final String BLOODFEUD = "\\u8840\\u4ec7";

    @Test
    public void theContentScenesDamage() {
        Character him = CharacterFactory.create(1404, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(him),
                List.of(EnemyFactory.create(1002011, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        him.getBuffManager().addBuff(new StateBuff(BLOODFEUD, 9, true));
        battle.processRequests();
        double before = battle.enemies.getFirst().getCurrentHp();
        Signal signal = battle.queue.snapshot().stream()
                .filter(candidate -> candidate.getCanHit() == him).findFirst().orElseThrow();
        battle.currentMove = signal;
        battle.beforeMove();
        battle.afterMove();
        battle.processRequests();
        System.out.println("[swap-damage] commanded=" + (before - battle.enemies.getFirst().getCurrentHp())
                + " ; SKILL slot now=" + him.getSkills().get(com.laosun.aluminium.enums.SkillType.SKILL).getSkillSlot()
                + " ; SKILL level=" + him.getSkills().get(com.laosun.aluminium.enums.SkillType.SKILL).getLevel());
    }
}
''')
print("ok   the probe prints the damage from the real table")
