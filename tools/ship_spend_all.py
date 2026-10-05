"""Ship `spendAll` + 1513's documented clause, with a judge that performs a REAL elation cast (2026-10-02).

Everything needed is measured:
  * the trigger spelling is `from_category ELATION_DAMAGE` (SkillCategory.ELATION_DAMAGE exists; from_category is used
    153 times in content) -- so no numeric skill row is needed;
  * `CAST_SKILL`'s `skill` is a SkillType slot (`SkillType.valueOf(...)`), and `SkillType.ELATION_SKILL` is the slot her
    file already casts;
  * `SkillExecutor.execute(battle, skill, user, targets)` is public static, so the judge casts through the ENGINE;
  * "one extra hit per point" uses the already-shipped `times_from: "event_amount"` on the nested RESOURCE_CHANGED.

This replays tools/add_spend_all3.py (engine + content, idempotent), fixes the content trigger, and writes the judge.
ASCII only.
"""
import io
import json
import os
import sys

exec(io.open("tools/add_spend_all3.py", encoding="utf-8").read())

DATA = "src/main/resources/characters/1513.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/SpendAllTest.java"
RES = "热意"

doc = json.load(io.open(DATA, encoding="utf-8"))
rules = doc["rules"] if isinstance(doc, dict) else doc
for r in rules:
    if isinstance(r, dict) and r.get("id") == "elation_spend_all_fervor":
        r["when"] = ["from_category ElationDamage"]
        r["note"] = ("⭐ 首个 `spendAll` 读者 ✓。⚠ 触发用 **`from_category ELATION_DAMAGE`** ✓"
                     "（`SkillCategory.ELATION_DAMAGE` ✓，内容里 `from_category` 用了 **153** 次 ✓）"
                     "—— ⚠ 我前三轮以为要写**数据行 id** ✗，那是错的 ✓。")
out = doc if isinstance(doc, dict) else {"rules": rules}
json.dump(out, io.open(DATA, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   1513.json: trigger is from_category ELATION_DAMAGE")

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

/**
 * 「消耗所有【热意】，每消耗1点都会额外造成1次 21% 伤害」 (1513:283, 2026-10-02).
 *
 * <p>File-driven throughout: the resource and the primer (队友攻击后 +1 【热意】, `actor is_other_ally`) come from her
 * own file, and the cast that spends it goes through `SkillExecutor.execute` -- the engine's own path, so the event
 * carries the cast category the rule keys off. ⚠ Measured earlier: hand-firing an event reaches nothing.
 */
public class SpendAllTest {
    private static final int OWNER = 1513;
    private static final int ALLY = 1404;
    private static final int MONSTER = 1002011;
    private static final String RES = "\\u70ed\\u610f";

    /** ⭐ The cast spends everything, and each point it spent adds one more instance. */
    @Test
    public void theCastSpendsAllFervorAndEachPointAddsAHit() {
        double one = lossAfterCastingWith(1);
        double seven = lossAfterCastingWith(7);
        Assertions.assertTrue(one > 0, "precondition: the instances land (" + one + ")");
        Assertions.assertEquals(7 * one, seven, one * 1e-6,
                "7 points spent add 7 instances (" + one + " -> " + seven + ")");
    }

    // ==================================================================

    /** Prime 【热意】 to {@code start} with her own rule, cast, and report the ENEMY's loss. */
    private static double lossAfterCastingWith(int start) {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Character ally = CharacterFactory.create(ALLY, 80, false, null, null, 0);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(owner, ally), List.of(enemy), new Random(0));
        battle.startBattle();

        for (int i = 0; i < start; i++) {
            battle.fireTriggers(TriggerEvent.ALLY_ATTACK, ally, ally, 0, 0);
        }
        battle.processRequests();
        Assertions.assertEquals(start, owner.getResources().value(RES),
                "precondition: her own rule primed \\u3010\\u70ed\\u610f\\u3011 to " + start);

        double before = enemy.getCurrentHp();
        SkillExecutor.execute(battle, owner.getSkills().get(SkillType.ELATION_SKILL), owner, List.of(enemy));
        battle.processRequests();
        Assertions.assertEquals(0, owner.getResources().value(RES),
                "the cast spends ALL of it (" + start + " -> 0)");
        return before - enemy.getCurrentHp();
    }
}
''')
print("ok   judge written (real elation cast through SkillExecutor)")
