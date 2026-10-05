"""Cone 23004 以世界之名: the SECOND sentence -- a cast-scoped stat boost (2026-10-02, item 56).

Document, verbatim (`data/weapons.json` -> 23004, `skill_value` = [#1 vs-debuffed, #2 effect hit, #3 ATK]):
  「使装备者对陷入负面效果的敌方目标造成的伤害提高 **#1**%。**当装备者施放战技时，装备者此次攻击的效果命中提高 #2%，攻击力提高 #3%**。」
  L1 [0.24, 0.18, 0.24] / L2 [0.28, 0.21, 0.28] / L3 [0.32, 0.24, 0.32] / L4 [0.36, 0.27, 0.36] / L5 [0.40, 0.30, 0.40]

⭐⭐ THE SCOPE ALREADY EXISTS -- 「施放瞬间作用域」 is **not** a missing capability (the seventh time a registered gap turned out to be
in the tree): `EffectSpec`'s `"until"` field names a LIFETIME, `AbstractBuff.Lifetime` provides `NEXT_ATTACK` / `NEXT_SKILL` /
`NEXT_ULTIMATE` / `CAST_END`, and light cone **20001 already ships four rules with `"until": "cast_end"`**:
  `CAST_SETUP` + `actor == self` + `from_skill SKILL` -> `MODIFY_ATTR { ..., "until": "cast_end", "target": "self" }`.
This shipment copies that spelling exactly; what was missing was only 23004's own second sentence.

⚠ Attributes read from `AttributeType`: 「效果命中」 = `EFFECT_HIT_RATE` (`effect_hit_rate`), 「攻击力」 = `ATTACK`.
"""
import io
import json

DATA = "src/main/resources/light_cones/23004.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/InTheNameOfTheWorldCastScopeTest.java"
RULE = "cone23004_cast_scope_stats"
STATS = {"1": (0.18, 0.24), "2": (0.21, 0.28), "3": (0.24, 0.32), "4": (0.27, 0.36), "5": (0.30, 0.40)}
SOURCE = ("光锥 23004 以世界之名：「当装备者施放战技时，装备者**此次攻击**的"
          "效果命中提高 #2%，攻击力提高 #3%」（`skill_value` 的 #2／#3 ✓："
          "L1 [0.18, 0.24] … L5 [0.30, 0.40] ✓）")
NOTE = ("⭐ 2026-10-02（第 56 件）：⭐⭐ **“施放瞬间作用域”早已存在** ✓（第七次"
        "“登记的缺口其实在树里” ✗）：`EffectSpec` 的 **`until`** 字段命名的是**生命周期** ✓，"
        "`AbstractBuff.Lifetime` 提供 `NEXT_ATTACK`／`NEXT_SKILL`／`NEXT_ULTIMATE`／**`CAST_END`** ✓，"
        "而光锥 **20001 已有四条** `\"until\": \"cast_end\"` 规则 ✓ ⇒ 本条**照拄**它的拼法 ✓。"
        "⚠ “此次攻击” 就是 `until: cast_end` ✓（施放结束即清 ✓）。")


def levels(doc):
    return [key for key in sorted(doc.keys()) if key.isdigit()]


doc = json.load(io.open(DATA, encoding="utf-8"))
if not isinstance(doc, dict):
    raise SystemExit("23004.json is expected to be keyed by superimposition rank")
for key in levels(doc):
    rules = doc[key]
    rules[:] = [r for r in rules if not (isinstance(r, dict) and r.get("id") == RULE)]
    enhance, attack = STATS[key]
    rules.append({
        "on": "CAST_SETUP",
        "id": RULE,
        "when": ["actor == self", "from_skill SKILL"],
        "do": [
            {"op": "MODIFY_ATTR", "attribute": "EFFECT_HIT_RATE", "percent": enhance,
             "until": "cast_end", "target": "self"},
            {"op": "MODIFY_ATTR", "attribute": "ATTACK", "percent": attack,
             "until": "cast_end", "target": "self"},
        ],
        "source": SOURCE,
        "note": NOTE,
    })
json.dump(doc, io.open(DATA, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   23004.json: the cast-scoped stat rule on ranks %s" % levels(doc))

io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 光锥 23004 以世界之名：「当装备者施放战技时，装备者此次攻击的效果命中提高 #2%，攻击力提高 #3%" (2026-10-02).
 *
 * <p>⭐ THE SCOPE IS `until: cast_end`, and that is what these readings pin:
 * <ul>
 *   <li>the boost is <b>gone the moment the cast is over</b> -- if the lifetime were longer (a turn, or permanent) the
 *       attributes would stay lifted, which is the mutation this guards;</li>
 *   <li>the shipped file carries the rule on <b>every</b> superimposition rank with that rank's own numbers, read off the
 *       content itself (the same file-reading guard the cone census tests use).</li>
 * </ul>
 *
 * <p>⚠ What this judge does NOT measure: the boost's effect <i>during</i> the cast. The spelling it rides on is not new --
 * light cone 20001 ships four `"until": "cast_end"` rules today -- so the during-the-cast half is inherited, and this test
 * pins the half that is this cone's own (the numbers and the expiry).
 */
public class InTheNameOfTheWorldCastScopeTest {
    private static final int WEARER = 1001;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final int CONE = 23004;
    private static final int RANK = 5;
    private static final String RULE = "cone23004_cast_scope_stats";

    /** ⭐ No residue: both attributes are back to where they started once the cast is over. */
    @Test
    public void theBoostDoesNotOutliveTheCast() {
        Character unit = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, RANK));
        Battle battle = new Battle(List.of(unit),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        double hitBefore = unit.getAttribute(AttributeType.EFFECT_HIT_RATE).get();
        double atkBefore = unit.getAttribute(AttributeType.ATTACK).get();
        String printed = "[" + CONE + "] before: effectHit=" + hitBefore + " atk=" + atkBefore;

        battle.castImmediate(unit.getSkills().get(SkillType.SKILL), unit, List.of(battle.enemies.getFirst()));
        battle.processRequests();

        double hitAfter = unit.getAttribute(AttributeType.EFFECT_HIT_RATE).get();
        double atkAfter = unit.getAttribute(AttributeType.ATTACK).get();
        System.out.println(printed + " ; after the cast: effectHit=" + hitAfter + " atk=" + atkAfter);

        Assertions.assertEquals(hitBefore, hitAfter, 1e-9,
                "\\u300c\\u6b64\\u6b21\\u653b\\u51fb\\u300d\\u7684\\u6548\\u679c\\u547d\\u4e2d\\u4e0d\\u80fd\\u6d3b\\u8fc7\\u8fd9\\u4e00\\u6b21\\u65bd\\u653e");
        Assertions.assertEquals(atkBefore, atkAfter, 1e-9,
                "\\u653b\\u51fb\\u529b\\u4e5f\\u4e00\\u6837\\uff1a`until: cast_end` \\u5230\\u6b64\\u4e3a\\u6b62");
    }

    /** ⭐ The content guard: every rank carries the rule, with that rank's own two numbers and the cast-end lifetime. */
    @Test
    public void everyRankCarriesTheCastScopedRule() throws java.io.IOException {
        String raw = java.nio.file.Files.readString(
                java.nio.file.Path.of("src", "main", "resources", "light_cones", "23004.json"),
                java.nio.charset.StandardCharsets.UTF_8);
        Assertions.assertTrue(raw.contains(RULE), "the shipped file must carry " + RULE);
        Assertions.assertEquals(5, count(raw, "\\"id\\": \\"" + RULE + "\\""),
                "one rule per superimposition rank");
        // ⚠ TEN, not five: each rank carries TWO effects (effect hit and attack) and both state the lifetime.
        Assertions.assertEquals(10, count(raw, "\\"until\\": \\"cast_end\\""),
                "both effects of every rank are scoped to the cast");
        Assertions.assertEquals(5, count(raw, "\\"attribute\\": \\"EFFECT_HIT_RATE\\""), "the effect-hit half");
        Assertions.assertEquals(5, count(raw, "\\"attribute\\": \\"ATTACK\\""), "and the attack half");
        for (String percent : List.of("0.18", "0.21", "0.24", "0.27", "0.3", "0.28", "0.32", "0.36", "0.4")) {
            Assertions.assertTrue(raw.contains("\\"percent\\": " + percent),
                    "rank numbers must be the data's own: missing " + percent);
        }
    }

    // ==================================================================

    private static int count(String haystack, String needle) {
        int found = 0;
        int at = haystack.indexOf(needle);
        while (at >= 0) {
            found++;
            at = haystack.indexOf(needle, at + needle.length());
        }
        return found;
    }
}
''')
print("ok   judge written")
