"""1208: 「【穷观阵】开启时，若我方目标受到致命伤害…并立即回复等同于其自身生命上限 70% 的生命值」 (2026-10-02, item 51).

Document, verbatim (1208 符玄 星魂 2 「柔兆 / Optimus Felix」, read as UTF-8 via a file):
  「【穷观阵】开启时，若我方目标受到致命伤害，则**本次行动中所有**受到致命伤害的我方目标都不会陷入无法战斗状态，并立即回复等同于**其自身**
   生命上限 **#1**% 的生命值。**该效果单场战斗中可以触发 1 次**。」
PARAM (measured, `eidolons.json` -> 1208/2/`param`): `[0.7]` -> #1 = **0.7**.

⭐ SIXTH reader of `LETHAL_DAMAGE`, and the FIRST with a per-battle trigger limit -- 「该效果单场战斗中可以触发 1 次」 is exactly the
shipped `once_per_battle` (1105 / 1111 use it), so it is not a paraphrase.

⭐ ON 「本次行动中所有」: this is the sentence's WORDING for the outcome, not a second mechanic. `LETHAL_DAMAGE` is announced once per unit
that would die, and this rule saves each of them at its own Max HP share -- which is what 「所有…都…回复等同于**其自身**生命上限」 describes. No
batching concept is needed, and none is invented.

⚠ ON THE GUARD: 「【穷观阵】开启时」. The zone itself is NOT a state in the tree; the shipped skill implements it as `SKILL_CAST` over
`all_allies` granting 【鉴知】 for the zone's three turns, so "the zone is open" and "【鉴知】 is on" hold at the same times. The guard is
therefore written as `self has_state 鉴知` -- the same interval, spelled with the state the engine actually has. (Stated, not hidden.)

⚠ REGISTERED, and a correction to item 47: `1217`'s E2 is 「该效果单场战斗中可以触发 **#2** 次」 with #2 = **2**, which `once_per_battle` cannot
say; and `1008`'s E4 is 「**触发 1 次后或持续 #2 回合后自动解除**」 with #2 = 2, i.e. a trigger limit OR a two-turn expiry. Both need a limit
richer than a boolean, so both shipped rules are recorded as missing their limit rather than being silently left unlimited.
ASCII only.
"""
import io
import json

DATA = "src/main/resources/characters/1208.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/FuxuanEidolonTwoTest.java"
RULE = "e2_saves_an_ally_while_the_zone_is_open"
ZONE = "鉴知"

doc = json.load(io.open(DATA, encoding="utf-8"))
isObject = isinstance(doc, dict)
rules = doc["rules"] if isObject else doc
rules = [r for r in rules if not (isinstance(r, dict) and r.get("id") == RULE)]

if len(rules) != 4:
    raise SystemExit("expected the four shipped 1208 rules, found " + str(len(rules)))

rules.append({
    "on": "LETHAL_DAMAGE",
    "id": RULE,
    "min_eidolon": 2,
    "once_per_battle": True,
    "when": ["actor is_ally", "self has_state " + ZONE],
    "do": [{"op": "HEAL", "scale": "target_max_hp", "percent": 0.70, "target": "target"}],
    "source": ("1208 符玄 星魂 2 「柔兆 / Optimus Felix」：「【穷观阵】开启时，若我方目标"
               "受到致命伤害，则本次行动中所有受到致命伤害的我方目标都不会陷入无法战斗状态，"
               "并立即回复等同于其自身生命上限 **70%** 的生命值。**该效果单场战斗中可以触发 1 次**」"),
    "note": ("⭐ 2026-10-02：⭐ **`LETHAL_DAMAGE` 的第六位读者** ✓；⭐ **「我方目标」的第二位** ✓（与 1217 同："
             "**不**需 `!= self` ✓）。❗ **首位带“单场一次”限制的读者** ✓（`once_per_battle` ✓）。"
             "⚠ **守卫写成 `self has_state 鉴知`** ✓：【穷观阵】本身**不是状态** ✗，而已出货战技用 `鉴知` 实现它 ✓（同一时间段 ✓）。"),
})

if isObject:
    doc["rules"] = rules
else:
    doc = rules
json.dump(doc, io.open(DATA, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   1208.json: eidolon two saves an ally while the zone is open")

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
 * 1208：「【穷观阵】开启时，若我方目标受到致命伤害…立即回复等同于其自身生命上限 70% 的生命值。
 * 该效果单场战斗中可以触发 1 次」 (2026-10-02).
 *
 * <p>⭐ TWO READINGS IN ONE SCENE: the first lethal blow is answered (the ally stands at 70% of its OWN Max HP), and the second is not --
 * which is exactly 「单场战斗中可以触发 1 次」. The zone is opened by her own skill, so no test-only shortcut is used.
 */
public class FuxuanEidolonTwoTest {
    private static final int FUXUAN = 1208;
    private static final int ALLY = 1002;
    private static final int MONSTER = 1002011;
    private static final String ZONE = "鉴知";

    /** ⭐ The first blow is answered; the second kills, because the sentence allows one. */
    @Test
    public void theFirstBlowIsAnsweredAndTheSecondIsNot() {
        Character her = CharacterFactory.create(FUXUAN, 80, false, null, null, 2);
        Character ally = CharacterFactory.create(ALLY, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(her, ally),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        Skill skill = her.getSkills().get(SkillType.SKILL);
        Assertions.assertNotNull(skill, "precondition: she has a skill");
        SkillExecutor.execute(battle, skill, her, List.of(battle.enemies.getFirst()));
        battle.processRequests();
        Assertions.assertTrue(her.getBuffManager().hasState(ZONE), "precondition: the zone is open (her state)");

        battle.applyTrueDamage(battle.enemies.getFirst(), ally, DamageElement.ICE, ally.getCurrentHp() * 2.0);
        battle.processRequests();
        Assertions.assertFalse(ally.isDeath(), "「不会陷入无法战斗状态」");
        Assertions.assertEquals(ally.getMaxHp() * 0.70, ally.getCurrentHp(), ally.getMaxHp() * 0.01,
                "「回复等同于**其自身**生命上限 70% 的生命值」");

        battle.applyTrueDamage(battle.enemies.getFirst(), ally, DamageElement.ICE, ally.getCurrentHp() * 2.0);
        battle.processRequests();
        Assertions.assertTrue(ally.isDeath(),
                "「该效果单场战斗中可以触发 1 次」-- the second one is not answered");
    }
}
''')
print("ok   judge written")
