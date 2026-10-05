"""1106 and 8005: the other two "technique + ult" pairs (2026-10-02).

Both have the same shape as 1401, which is now shipped and measured: a BATTLE_START rule gated on `self has_state 秘技`, and a
ULT_CAST rule writing the SAME slot on the SAME units. The later writer is the ult in both, so the ult states `max_stacks: 2`.

  * 1106: `ult_zone_suppression` (-40% DEFENCE, all enemies) vs `technique_defence_down` (-20% DEFENCE, all enemies)
  * 8005: `ult_dance` (+30% BREAKING_EFFECT, all allies) vs `technique_breaking_effect` (+30% BREAKING_EFFECT, all allies)

Each judge is file-driven, reads four numbers (neither / technique only / ult only / both) and asserts SUPERPOSITION --
the differences add. No share of any total is assumed, because measuring showed that assumption is wrong (a +60% modifier
sits on the base while the displayed value carries other bonuses).
ASCII only.
"""
import io
import json

def note_for(block, other):
    return (block +
            " ⭐ 2026-10-02：`\"max_stacks\": 2` ✓ —— 同一单位同一槽上有**两个来源**（"
            + other + "）⇒ 按实测机制，**后写的那条必须可叠加** ✓"
            "（`StatModifierBuff.isStackable()` ≡ `maxStacks > 1` ✓）；本条是**后触发**的那个"
            "（`ULT_CAST` 晚于 `BATTLE_START` ✓）。⚠ 判据用 **`battle.markTechniqueUsed(owner)`**"
            "在 `startBattle()` 之前给状态 ✓（⭐ 而不是自己造 `APPLY_BUFF` 规则 ✗）。")

def add_max_stacks(path, rule_id, attribute, block, other):
    doc = json.load(io.open(path, encoding="utf-8"))
    rules = doc["rules"] if isinstance(doc, dict) else doc
    for r in rules:
        if isinstance(r, dict) and r.get("id") == rule_id:
            for s in r.get("do", []):
                if isinstance(s, dict) and s.get("attribute") == attribute:
                    s["max_stacks"] = 2
            r["note"] = (r.get("note") or "") + note_for(block, other)
    json.dump(doc, io.open(path, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
    print("ok  ", path, rule_id)

add_max_stacks("src/main/resources/characters/1106.json", "ult_zone_suppression", "DEFENCE",
               "终结技降防（-40%）", "秘技降防 -20% ✓")
add_max_stacks("src/main/resources/characters/8005.json", "ult_dance", "BREAKING_EFFECT",
               "终结技加击破特攻（+30%）", "秘技 +30% ✓")

JUDGE = '''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * %TITLE% (2026-10-02).
 *
 * <p>⭐ FILE-DRIVEN, four readings, and SUPERPOSITION is the claim: with both sources present the two differences must add.
 * ⚠ `秘技` comes from `battle.markTechniqueUsed(owner)` BEFORE `startBattle()` -- the engine's own entry point.
 */
public class %CLASS% {
    private static final int OWNER = %OWNER%;
    private static final int MONSTER = 1002011;

    /** ⭐ Both sources present means the two differences add. */
    @Test
    public void bothSourcesAreCounted() {
        double plain = read(false, false);
        double techniqueOnly = read(true, false);
        double ultOnly = read(false, true);
        double both = read(true, true);

        Assertions.assertTrue(Math.abs(techniqueOnly - plain) > 1e-9,
                "the technique's share lands (" + plain + " -> " + techniqueOnly + ")");
        Assertions.assertTrue(Math.abs(ultOnly - plain) > 1e-9,
                "and the ult's lands on its own (" + plain + " -> " + ultOnly + ")");
        Assertions.assertEquals((techniqueOnly - plain) + (ultOnly - plain), both - plain, 1e-6,
                "with both present the two differences must ADD: (" + techniqueOnly + " - " + plain + ") + ("
                        + ultOnly + " - " + plain + ") vs (" + both + " - " + plain + ")");
    }

    // ==================================================================

    private static double read(boolean usedTechnique, boolean castUlt) {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(owner), List.of(enemy), new Random(0));
        if (usedTechnique) {
            battle.markTechniqueUsed(owner);
        }
        battle.startBattle();
        battle.processRequests();
        if (castUlt) {
            battle.fireTriggers(TriggerEvent.ULT_CAST, owner, owner, 0, 0);
            battle.processRequests();
        }
        return %READ%;
    }
}
'''

io.open("src/test/java/com/laosun/aluminium/test/PelaDefenceStackingTest.java", "w",
        encoding="utf-8", newline="").write(
    JUDGE.replace("%TITLE%", "\\u7ec8\\u7ed3\\u6280\\u7684 -40% \\u4e0e\\u79d8\\u6280\\u7684 -20% \\u964d\\u9632\\u90fd\\u8981\\u7b97 (1106)")
         .replace("%CLASS%", "PelaDefenceStackingTest")
         .replace("%OWNER%", "1106")
         .replace("%READ%", "enemy.getAttribute(AttributeType.DEFENCE).get()"))
print("ok   PelaDefenceStackingTest")

io.open("src/test/java/com/laosun/aluminium/test/TrailblazerBreakingEffectStackingTest.java", "w",
        encoding="utf-8", newline="").write(
    JUDGE.replace("%TITLE%", "\\u7ec8\\u7ed3\\u6280\\u4e0e\\u79d8\\u6280\\u7684 +30% \\u51fb\\u7834\\u7279\\u653b\\u90fd\\u8981\\u7b97 (8005)")
         .replace("%CLASS%", "TrailblazerBreakingEffectStackingTest")
         .replace("%OWNER%", "8005")
         .replace("%READ%", "owner.getAttribute(AttributeType.BREAKING_EFFECT).get()"))
print("ok   TrailblazerBreakingEffectStackingTest")
