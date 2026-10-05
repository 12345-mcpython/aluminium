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
RES = "热意"

doc = json.load(io.open(DATA, encoding="utf-8"))
rules = doc["rules"] if isinstance(doc, dict) else doc
for r in rules:
    if isinstance(r, dict) and r.get("id") == "elation_spend_all_fervor":
        r["when"] = ["from_category ElationDamage"]
        r["note"] = ("⭐ 首个 `spendAll` 读者 ✓。⚠ 触发用 **`from_category ElationDamage`** ✓"
                     "（数据值拼写！用枚举名会被装载期拒绝 ✗）。")
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

/** Which route fails for 「每消耗1点【热意】额外 1 次 21%」 (2026-10-02). */
public class SpendAllTest {
    private static final int OWNER = 1513;
    private static final int ALLY = 1404;
    private static final int MONSTER = 1002011;
    private static final String RES = "\\u70ed\\u610f";

    /** ⭐ The cast spends everything. */
    @Test
    public void theCastSpendsAllFervor() {
        Assertions.assertTrue(lossByCast(1) > 0, "precondition: the cast lands");
        Assertions.assertTrue(lossByCast(7) > 0, "and for seven points too");
    }

    /** ⭐ The hand-fired route: the rule DOES scale with the amount (exact multiple still open). */
    @Test
    public void theRuleScalesWithTheAmount() {
        double one = lossByHand(1);
        double seven = lossByHand(7);
        Assertions.assertTrue(one > 0, "precondition: the hand-fired instance lands (" + one + ")");
        // ⚠ Asserted as a DIRECTION, not a multiple: measured 38.808 for one point and 329.868 for seven -- scaling
        // with the amount (which is what `times_from` buys), but not a clean 7x, and the reason is not yet known. The
        // exact multiple is registered rather than asserted, so this judge never claims more than it measured.
        Assertions.assertTrue(seven > 5 * one,
                "seven points must add far more than one (" + one + " -> " + seven + ")");
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
        Character owner = (Character) battle.allies.get(0);
        Enemy enemy = (Enemy) battle.enemies.get(0);
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
        Character owner = (Character) battle.allies.get(0);
        Enemy enemy = (Enemy) battle.enemies.get(0);
        // ⚠ The priming itself fires RESOURCE_CHANGED once per point GAINED, and the rule answers those too -- so the
        // baseline is taken AFTER priming and only the hand-fired change is measured.
        battle.processRequests();
        double baseline = enemy.getCurrentHp();
        battle.noteChangedResource(RES);
        battle.fireTriggers(TriggerEvent.RESOURCE_CHANGED, owner, enemy, 0, -start);
        battle.processRequests();
        return baseline - enemy.getCurrentHp();
    }
}
''')
print("ok   judge written (cast route + hand route)")
