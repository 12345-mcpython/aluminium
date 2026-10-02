"""Split the two remaining hypotheses for 1513's per-point clause (2026-10-02).

Measured so far: the cast spends ALL of 【热意】 (green), but the enemy's loss is the SAME 60.984 for one point and for
seven. Two candidates:
  (a) the nested `RESOURCE_CHANGED` rule never fires during a real cast;
  (b) it fires, but reads the wrong amount (so `times_from` gives 1).

The judge now measures BOTH routes in one file, so the failing assertion names which one:
  * cast route   -- `SkillExecutor.execute` (the engine's own path);
  * hand route   -- `fireTriggers(RESOURCE_CHANGED, owner, enemy, 0, -n)` (the route EventAmountTest already binds).
If the hand route scales 7x and the cast route does not, the rule works and the CAST's nested fire is the problem; if
neither scales, the rule's own `when` is the problem.

Replays tools/add_spend_all3.py (engine + content, idempotent), fixes the trigger spelling to the data value, writes the
judge. ASCII only.
"""
import io
import json

exec(io.open("tools/add_spend_all3.py", encoding="utf-8").read())

DATA = "src/main/resources/characters/1513.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/SpendAllTest.java"
RES = "\u70ed\u610f"

doc = json.load(io.open(DATA, encoding="utf-8"))
rules = doc["rules"] if isinstance(doc, dict) else doc
for r in rules:
    if isinstance(r, dict) and r.get("id") == "elation_spend_all_fervor":
        r["when"] = ["from_category ElationDamage"]
        r["note"] = ("\u2b50 \u9996\u4e2a `spendAll` \u8bfb\u8005 \u2713\u3002\u26a0 \u89e6\u53d1\u7528 **`from_category ElationDamage`** \u2713"
                     "\uff08\u6570\u636e\u503c\u62fc\u5199\uff01\u7528\u679a\u4e3e\u540d\u4f1a\u88ab\u88c5\u8f7d\u671f\u62d2\u7edd \u2717\uff09\u3002")
json.dump(doc if isinstance(doc, dict) else {"rules": rules},
          io.open(DATA, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   1513.json trigger = from_category ElationDamage")

io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/** Which route fails for \u300c\u6bcf\u6d88\u80171\u70b9\u3010\u70ed\u610f\u3011\u989d\u5916 1 \u6b21 21%\u300d (2026-10-02). */
public class SpendAllTest {
    private static final int OWNER = 1513;
    private static final int ALLY = 1404;
    private static final int MONSTER = 1002011;
    private static final String RES = "\\u70ed\\u610f";

    /** \u2b50 The cast spends everything. */
    @Test
    public void theCastSpendsAllFervor() {
        Assertions.assertTrue(lossByCast(1) > 0, "precondition: the cast lands");
        Assertions.assertTrue(lossByCast(7) > 0, "and for seven points too");
    }

    /** \u2b50 The hand-fired route: does the rule scale with the amount at all? */
    @Test
    public void theRuleScalesWithTheAmount() {
        double one = lossByHand(1);
        double seven = lossByHand(7);
        Assertions.assertTrue(one > 0, "precondition: the hand-fired instance lands (" + one + ")");
        Assertions.assertEquals(7 * one, seven, one * 1e-6,
                "the nested rule must repeat once per point (" + one + " -> " + seven + ")");
    }

    // ==================================================================

    private static Battle primed(int start) {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Character ally = CharacterFactory.create(ALLY, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(owner, ally),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        for (int i = 0; i < start; i++) {
            battle.fireTriggers(TriggerEvent.ALLY_ATTACK, ally, ally, 0, 0);
        }
        battle.processRequests();
        return battle;
    }

    private static double lossByCast(int start) {
        Battle battle = primed(start);
        Character owner = (Character) battle.getAllies().get(0);
        Enemy enemy = (Enemy) battle.getEnemies().get(0);
        Assertions.assertEquals(start, owner.getResources().value(RES),
                "precondition: her own rule primed it to " + start);
        double before = enemy.getCurrentHp();
        SkillExecutor.execute(battle, owner.getSkills().get(SkillType.ELATION_SKILL), owner, List.of(enemy));
        battle.processRequests();
        Assertions.assertEquals(0, owner.getResources().value(RES),
                "the cast spends ALL of it (" + start + " -> 0)");
        return before - enemy.getCurrentHp();
    }

    private static double lossByHand(int start) {
        Battle battle = primed(start);
        Character owner = (Character) battle.getAllies().get(0);
        Enemy enemy = (Enemy) battle.getEnemies().get(0);
        battle.noteChangedResource(RES);
        double before = enemy.getCurrentHp();
        battle.fireTriggers(TriggerEvent.RESOURCE_CHANGED, owner, enemy, 0, -start);
        battle.processRequests();
        return before - enemy.getCurrentHp();
    }
}
''')
print("ok   judge written (cast route + hand route)")
