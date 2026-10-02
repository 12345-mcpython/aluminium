"""1106 and 8005: the other two "technique + ult" pairs (2026-10-02).

Both have the same shape as 1401, which is now shipped and measured: a BATTLE_START rule gated on `self has_state \u79d8\u6280`, and a
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
            " \u2b50 2026-10-02\uff1a`\"max_stacks\": 2` \u2713 \u2014\u2014 \u540c\u4e00\u5355\u4f4d\u540c\u4e00\u69fd\u4e0a\u6709**\u4e24\u4e2a\u6765\u6e90**\uff08"
            + other + "\uff09\u21d2 \u6309\u5b9e\u6d4b\u673a\u5236\uff0c**\u540e\u5199\u7684\u90a3\u6761\u5fc5\u987b\u53ef\u53e0\u52a0** \u2713"
            "（`StatModifierBuff.isStackable()` \u2261 `maxStacks > 1` \u2713\uff09\uff1b\u672c\u6761\u662f**\u540e\u89e6\u53d1**\u7684\u90a3\u4e2a"
            "\uff08`ULT_CAST` \u665a\u4e8e `BATTLE_START` \u2713\uff09\u3002\u26a0 \u5224\u636e\u7528 **`battle.markTechniqueUsed(owner)`**"
            "\u5728 `startBattle()` \u4e4b\u524d\u7ed9\u72b6\u6001 \u2713\uff08\u2b50 \u800c\u4e0d\u662f\u81ea\u5df1\u9020 `APPLY_BUFF` \u89c4\u5219 \u2717\uff09\u3002")

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
               "\u7ec8\u7ed3\u6280\u964d\u9632\uff08-40%\uff09", "\u79d8\u6280\u964d\u9632 -20% \u2713")
add_max_stacks("src/main/resources/characters/8005.json", "ult_dance", "BREAKING_EFFECT",
               "\u7ec8\u7ed3\u6280\u52a0\u51fb\u7834\u7279\u653b\uff08+30%\uff09", "\u79d8\u6280 +30% \u2713")

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
 * <p>\u2b50 FILE-DRIVEN, four readings, and SUPERPOSITION is the claim: with both sources present the two differences must add.
 * \u26a0 `\u79d8\u6280` comes from `battle.markTechniqueUsed(owner)` BEFORE `startBattle()` -- the engine's own entry point.
 */
public class %CLASS% {
    private static final int OWNER = %OWNER%;
    private static final int MONSTER = 1002011;

    /** \u2b50 Both sources present means the two differences add. */
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

io.open("src/test/java/com/laosun/aluminium/test/DefenceStacking1106Test.java", "w",
        encoding="utf-8", newline="").write(
    JUDGE.replace("%TITLE%", "\\u7ec8\\u7ed3\\u6280\\u7684 -40% \\u4e0e\\u79d8\\u6280\\u7684 -20% \\u964d\\u9632\\u90fd\\u8981\\u7b97 (1106)")
         .replace("%CLASS%", "DefenceStacking1106Test")
         .replace("%OWNER%", "1106")
         .replace("%READ%", "enemy.getAttribute(AttributeType.DEFENCE).get()"))
print("ok   DefenceStacking1106Test")

io.open("src/test/java/com/laosun/aluminium/test/BreakingEffectStacking8005Test.java", "w",
        encoding="utf-8", newline="").write(
    JUDGE.replace("%TITLE%", "\\u7ec8\\u7ed3\\u6280\\u4e0e\\u79d8\\u6280\\u7684 +30% \\u51fb\\u7834\\u7279\\u653b\\u90fd\\u8981\\u7b97 (8005)")
         .replace("%CLASS%", "BreakingEffectStacking8005Test")
         .replace("%OWNER%", "8005")
         .replace("%READ%", "owner.getAttribute(AttributeType.BREAKING_EFFECT).get()"))
print("ok   BreakingEffectStacking8005Test")
