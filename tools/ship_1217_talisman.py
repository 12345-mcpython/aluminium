"""1217: 「当藿藿拥有【禳命】时，若我方目标受到致命攻击，不会陷入无法战斗状态，并立即回复等同于其自身生命上限 50% 的生命值」 -- item 47.

Document, verbatim (1217 藿藿 星魂 2 「判官书符，镇尾锁灵」): 「当**藿藿拥有【禳命】时**，若**我方目标**受到致命攻击，不会陷入**无法战斗状态**，并立即回复等同于
**其自身**生命上限 **50%** 的生命值，**使【禳命】的持续回合数减 1**。」

⭐ FOURTH reader of `LETHAL_DAMAGE`, and the FIRST one that heals by the VICTIM's Max HP rather than the healer's: 「其自身」 is the
unit that would have fallen, which is `scale: "target_max_hp"` (a shipped spelling -- `1001.json` uses it). 1211 (item 46) was the
mirror image: 「等同于**白露**…」, i.e. the healer's.

⚠ 「我方目标」 includes 藿藿 herself, so unlike 1211 there is no `!= self` here.
⚠ REGISTERED, not shipped, from the same sentence: 「使【禳命】的持续回合数**减 1**」. `EXTEND_BUFF` requires POSITIVE turns
(`requirePositiveTurns`), so "take a turn off" has no spelling today -- and 【禳命】's own rule says its clock is 「藿藿每回合开始时…减 1」
(ticks_on self, 2 turns), so the reduction would have to go through that same duration bookkeeping.
ASCII only.
"""
import io
import json

DATA = "src/main/resources/characters/1217.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/TalismanSavesAnAllyTest.java"
RULE = "e2_saves_an_ally_with_the_talisman"
STATE = "禳命"

doc = json.load(io.open(DATA, encoding="utf-8"))
# Note: 121.json is a BARE LIST (like 1104 / 1209 / 1211); the file's own shape is preserved on the way out.
isObject = isinstance(doc, dict)
rules = doc["rules"] if isObject else doc
rules = [r for r in rules if not (isinstance(r, dict) and r.get("id") == RULE)]

if len(rules) != 6:
    raise SystemExit("expected the six shipped 1217 rules, found " + str(len(rules)))

rules.append({
    "on": "LETHAL_DAMAGE",
    "id": RULE,
    "min_eidolon": 2,
    "when": ["actor is_ally", "self has_state " + STATE],
    "do": [{"op": "HEAL", "scale": "target_max_hp", "percent": 0.50, "target": "target"}],
    "source": ("1217 藿藿 星魂 2 「判官书符，镇尾锁灵」：「当**藿藿拥有【禳命】时**，"
               "若**我方目标**受到致命攻击，不会陷入**无法战斗状态**，并立即回复等同于"
               "**其自身**生命上限 **50%** 的生命值」"),
    "note": ("⭐ 2026-10-02：⭐ **`LETHAL_DAMAGE` 的第四位读者** ✓，而且是**第一位按被害者生命上限治疗的** ✓"
             "（「**其自身**」⇒ `scale: \"target_max_hp\"` ✓ —— 已出货拼法，`1001.json` 用它 ✓）；"
             "⾋第 46 件（1211）**正好相反** ✓（那里是「等同于**白露**」⇒ 治疗者的上限 ✓）。"
             "⚠ **“我方目标”包括藿藿自己** ✓ ⇒ **没有** `!= self` ✓（与 1211 不同 ✓）。"
             "⚠ **仍登记**：“使【禳命】的持续回合数**减 1**” ✗（`EXTEND_BUFF` **只收正数** ✗）。"),
})

if isObject:
    doc["rules"] = rules
else:
    doc = rules
json.dump(doc, io.open(DATA, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   1217.json: the talisman saves an ally who takes a lethal blow")

io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageElement;
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

/**
 * 1217：「当藿藿拥有【禳命】时，若我方目标受到致命攻击…立即回复等同于其自身生命上限 50% 的生命值」 (2026-10-02).
 *
 * <p>⭐ ONE VARIABLE: the eidolon rank. Same party, same skill (which is what puts 【禳命】 on her), same lethal blow.
 */
public class TalismanSavesAnAllyTest {
    private static final int HUOHUO = 1217;
    private static final int ALLY = 1002;
    private static final int MONSTER = 1002011;
    private static final String STATE = "禳命";

    /** ⭐ At E2 the ally survives, at HALF OF ITS OWN Max HP. */
    @Test
    public void atEidolonTwoTheAllySurvives() {
        double[] result = afterLethalBlow(2);
        Assertions.assertTrue(result[0] > 0, "「不会陷入无法战斗状态」 (hp " + result[0] + ")");
        Assertions.assertEquals(result[1] * 0.50, result[0], result[1] * 0.01,
                "「回复等同于**其自身**生命上限 50%」-- the VICTIM’s own, not the healer’s");
    }

    /** ⚠ Below E2 the ally falls. */
    @Test
    public void belowEidolonTwoTheAllyFalls() {
        Assertions.assertTrue(afterLethalBlow(0)[0] <= 0, "星魂 2 才有这一条");
    }

    // ==================================================================

    /** { the ally's HP after the blow, the ally's Max HP }. */
    private static double[] afterLethalBlow(int eidolon) {
        Character her = CharacterFactory.create(HUOHUO, 80, false, null, null, eidolon);
        Character ally = CharacterFactory.create(ALLY, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(her, ally),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        // Her skill is what puts 【禳命】 on her (2 turns, ticking on her own turns).
        Skill skill = her.getSkills().get(SkillType.SKILL);
        Assertions.assertNotNull(skill, "precondition: she has a skill");
        SkillExecutor.execute(battle, skill, her, List.of(battle.enemies.getFirst()));
        battle.processRequests();
        Assertions.assertTrue(her.getBuffManager().hasState(STATE), "precondition: 【禳命】 is on her");

        Battle dummy = battle;
        dummy.applyTrueDamage(battle.enemies.getFirst(), ally, DamageElement.ICE, ally.getCurrentHp() * 2.0);
        battle.processRequests();
        return new double[]{ally.isDeath() ? 0 : ally.getCurrentHp(), ally.getMaxHp()};
    }
}
''')
print("ok   judge written")
