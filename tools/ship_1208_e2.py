"""1208: 「【穷观阵】开启时，若我方目标受到致命伤害…并立即回复等同于其自身生命上限 70% 的生命值」 (2026-10-02, item 51).

Document, verbatim (1208 符玄 星魂 2 「柔兆 / Optimus Felix」, read as UTF-8 via a file):
  「【穷观阵】开启时，若我方目标受到致命伤害，则**本次行动中所有**受到致命伤害的我方目标都不会陷入无法战斗状态，并立即回复等同于**其自身**
   生命上限 **#1**% 的生命值。**该效果单场战斗中可以触发 1 次**。」
PARAM (measured, `eidolons.json` -> 1208/2/`param`): `[0.7]` -> #1 = **0.7**.

\u2b50 SIXTH reader of `LETHAL_DAMAGE`, and the FIRST with a per-battle trigger limit -- 「该效果单场战斗中可以触发 1 次」 is exactly the
shipped `once_per_battle` (1105 / 1111 use it), so it is not a paraphrase.

\u2b50 ON 「本次行动中所有」: this is the sentence's WORDING for the outcome, not a second mechanic. `LETHAL_DAMAGE` is announced once per unit
that would die, and this rule saves each of them at its own Max HP share -- which is what 「所有…都…回复等同于**其自身**生命上限」 describes. No
batching concept is needed, and none is invented.

\u26a0 ON THE GUARD: 「【穷观阵】开启时」. The zone itself is NOT a state in the tree; the shipped skill implements it as `SKILL_CAST` over
`all_allies` granting 【鉴知】 for the zone's three turns, so "the zone is open" and "【鉴知】 is on" hold at the same times. The guard is
therefore written as `self has_state 鉴知` -- the same interval, spelled with the state the engine actually has. (Stated, not hidden.)

\u26a0 REGISTERED, and a correction to item 47: `1217`'s E2 is 「该效果单场战斗中可以触发 **#2** 次」 with #2 = **2**, which `once_per_battle` cannot
say; and `1008`'s E4 is 「**触发 1 次后或持续 #2 回合后自动解除**」 with #2 = 2, i.e. a trigger limit OR a two-turn expiry. Both need a limit
richer than a boolean, so both shipped rules are recorded as missing their limit rather than being silently left unlimited.
ASCII only.
"""
import io
import json

DATA = "src/main/resources/characters/1208.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/FuxuanEidolonTwoTest.java"
RULE = "e2_saves_an_ally_while_the_zone_is_open"
ZONE = "\u9274\u77e5"

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
    "source": ("1208 \u7b26\u7384 \u661f\u9b42 2 \u300c\u67d4\u5146 / Optimus Felix\u300d\uff1a\u300c\u3010\u7a77\u89c2\u9635\u3011\u5f00\u542f\u65f6\uff0c\u82e5\u6211\u65b9\u76ee\u6807"
               "\u53d7\u5230\u81f4\u547d\u4f24\u5bb3\uff0c\u5219\u672c\u6b21\u884c\u52a8\u4e2d\u6240\u6709\u53d7\u5230\u81f4\u547d\u4f24\u5bb3\u7684\u6211\u65b9\u76ee\u6807\u90fd\u4e0d\u4f1a\u9677\u5165\u65e0\u6cd5\u6218\u6597\u72b6\u6001\uff0c"
               "\u5e76\u7acb\u5373\u56de\u590d\u7b49\u540c\u4e8e\u5176\u81ea\u8eab\u751f\u547d\u4e0a\u9650 **70%** \u7684\u751f\u547d\u503c\u3002**\u8be5\u6548\u679c\u5355\u573a\u6218\u6597\u4e2d\u53ef\u4ee5\u89e6\u53d1 1 \u6b21**\u300d"),
    "note": ("\u2b50 2026-10-02\uff1a\u2b50 **`LETHAL_DAMAGE` \u7684\u7b2c\u516d\u4f4d\u8bfb\u8005** \u2713\uff1b\u2b50 **"**\u6211\u65b9\u76ee\u6807**"\u7684\u7b2c\u4e8c\u4f4d** \u2713\uff08\u4e0e 1217 \u540c\uff1a"
             "**\u4e0d**\u9700 `!= self` \u2713\uff09\u3002\u2757 **\u9996\u4f4d\u5e26\u201c\u5355\u573a\u4e00\u6b21\u201d\u9650\u5236\u7684\u8bfb\u8005** \u2713\uff08`once_per_battle` \u2713\uff09\u3002"
             "\u26a0 **\u5b88\u536b\u5199\u6210 `self has_state \u9274\u77e5`** \u2713\uff1a\u3010\u7a77\u89c2\u9635\u3011\u672c\u8eab**\u4e0d\u662f\u72b6\u6001** \u2717\uff0c\u800c\u5df2\u51fa\u8d27\u6218\u6280\u7528 `\u9274\u77e5` \u5b9e\u73b0\u5b83 \u2713\uff08\u540c\u4e00\u65f6\u95f4\u6bb5 \u2713\uff09\u3002"),
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
 * 1208\uff1a\u300c\u3010\u7a77\u89c2\u9635\u3011\u5f00\u542f\u65f6\uff0c\u82e5\u6211\u65b9\u76ee\u6807\u53d7\u5230\u81f4\u547d\u4f24\u5bb3\u2026\u7acb\u5373\u56de\u590d\u7b49\u540c\u4e8e\u5176\u81ea\u8eab\u751f\u547d\u4e0a\u9650 70% \u7684\u751f\u547d\u503c\u3002
 * \u8be5\u6548\u679c\u5355\u573a\u6218\u6597\u4e2d\u53ef\u4ee5\u89e6\u53d1 1 \u6b21\u300d (2026-10-02).
 *
 * <p>\u2b50 TWO READINGS IN ONE SCENE: the first lethal blow is answered (the ally stands at 70% of its OWN Max HP), and the second is not --
 * which is exactly \u300c\u5355\u573a\u6218\u6597\u4e2d\u53ef\u4ee5\u89e6\u53d1 1 \u6b21\u300d. The zone is opened by her own skill, so no test-only shortcut is used.
 */
public class FuxuanEidolonTwoTest {
    private static final int FUXUAN = 1208;
    private static final int ALLY = 1002;
    private static final int MONSTER = 1002011;
    private static final String ZONE = "\u9274\u77e5";

    /** \u2b50 The first blow is answered; the second kills, because the sentence allows one. */
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
        Assertions.assertFalse(ally.isDeath(), "\u300c\u4e0d\u4f1a\u9677\u5165\u65e0\u6cd5\u6218\u6597\u72b6\u6001\u300d");
        Assertions.assertEquals(ally.getMaxHp() * 0.70, ally.getCurrentHp(), ally.getMaxHp() * 0.01,
                "\u300c\u56de\u590d\u7b49\u540c\u4e8e**\u5176\u81ea\u8eab**\u751f\u547d\u4e0a\u9650 70% \u7684\u751f\u547d\u503c\u300d");

        battle.applyTrueDamage(battle.enemies.getFirst(), ally, DamageElement.ICE, ally.getCurrentHp() * 2.0);
        battle.processRequests();
        Assertions.assertTrue(ally.isDeath(),
                "\u300c\u8be5\u6548\u679c\u5355\u573a\u6218\u6597\u4e2d\u53ef\u4ee5\u89e6\u53d1 1 \u6b21\u300d-- the second one is not answered");
    }
}
''')
print("ok   judge written")
