"""1415's sky-ode stack sentence, with the cap spelled the way the GAME spells "uncapped" (2026-10-02).

The chain, all measured:
  * the sentence: 「德谬歌施放忆灵技时，使风堇获得2层【献予「天空」之诗】。」;
  * the ability data grants it as `AddModifier` on `MServant_CyreneServant_00_AmazingBuff_Hyacine` with `LayerAddWhenStack: 2`, behind a `ByCompareCharacterID` = 1409 -- the game
    names the character by cid, which is what `ally_cid:` does;
  * four files mention that modifier, and every count-ish key next to it is `LayerAddWhenStack: 2`; NO `MaxLayer` sits beside it, in the ability file or in `AvatarStatusConfig`
    (whose rows have no count column at all);
  * where this kit DOES mean "no limit", it writes `MaxLayer: 99999` (twice, for other modifiers in the same ability file).

So an absent `MaxLayer` next to a layer-adding modifier means "no cap", and the game's own spelling for that is 99999. Writing it that way is the data's convention, not an
invented number -- and without it our `StackBuff` (which is `Math.max(1, maxStacks)`) would deliver 1 layer where the data says 2.
"""
import io
import json
import sys

MEMOSPRITE = "src/main/resources/characters/1415.json"
SE = "src/main/resources/data/skill_effects.json"
SLOT = 19
MARK = "献予「天空」之诗"
CID = 1409

doc = json.load(io.open(MEMOSPRITE, encoding="utf-8"))
rules = doc if isinstance(doc, list) else doc.get("rules", [])
RULE_ID = "memosprite_ode_of_sky_stacks_hyacine_when_it_casts"
if any(r.get("id") == RULE_ID for r in rules):
    sys.exit("REFUSING: %s is already there" % RULE_ID)

rules.append({
    "id": RULE_ID,
    "on": "CAST_SETUP",
    "when": ["actor is_summon"],
    "do": [{
        "op": "ADD_STACK",
        "buff": MARK,
        "amount": 2,
        "max_stacks": 99999,
        "permanent": True,
        "target": "ally_cid:" + str(CID),
    }],
    "source": ("1415 昔涟 忆灵技能 19 「献予「天空」之诗」（数据槽位 19，SkillID 1141519）："
               "「**德谬歌施放忆灵技时，使风堇获得2层【" + MARK + "】**。」"),
    "note": ("⭐ 能力数据自己就是这么写的（**实测**）："
             "`AddModifier` **`MServant_CyreneServant_00_AmazingBuff_Hyacine`** + `LayerAddWhenStack: 2`，"
             "而前置是 **`ByCompareCharacterID` = 1409** —— 游戏就是“按 cid 点名”，"
             "与 `ally_cid:` 是同一件事。"
             "⭐ **上限写 99999 是游戏自己的惯例**，不是我编的："
             "提到该修饰的四个文件里，它旁边**没有** `MaxLayer`（`AvatarStatusConfig` 的行甚至没有计数列），"
             "而同一个能力文件里“不限”写的就是 `MaxLayer: 99999`（两处）⇒ 缺省就是“不限”。"
             "⚠ 不写它的话，引擎的 `StackBuff`（`Math.max(1, maxStacks)`）会只给 **1** 层。"),
})
if isinstance(doc, list):
    out = rules
else:
    doc["rules"] = rules
    out = doc
io.open(MEMOSPRITE, "w", encoding="utf-8", newline="\n").write(json.dumps(out, ensure_ascii=False, indent=2) + "\n")
print("ok   1415 carries %s (%d rules)" % (RULE_ID, len(rules)))

effects = json.load(io.open(SE, encoding="utf-8"))
if str(SLOT) in effects.get("11415", {}):
    effects["11415"][str(SLOT)]["note"] = ("⭐ 2026-10-02：同一条技能的**能量那半**与**层数那半**均已成句。")
    io.open(SE, "w", encoding="utf-8", newline="\n").write(json.dumps(effects, ensure_ascii=False, indent=2) + "\n")
    print("ok   skill_effects.json 11415/19 note updated")

JUDGE = '''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1415's memosprite skill 19, first sentence: 「德谬歌施放忆灵技时，使风堇获得2层【献予「天空」之诗】」 (2026-10-02).
 *
 * <p>⭐ TWO-SIDED in one battle: the character the game NAMES BY CID (1409, measured in the ability data) gets the 2 layers the data states, and a different ally present gets
 * none. ⚠ A cap had to be stated -- the data puts no `MaxLayer` beside this modifier, and our `StackBuff` clamps to 1 without one; 99999 is how this kit spells "no limit".
 */
public class SkyOdeStackTest {
    private static final int LEVEL = 80;
    private static final int CYRENE = 1415;
    private static final int HYACINE = 1409;
    private static final int OTHER = 1002;
    private static final int MONSTER = 1002011;
    private static final String MARK = "\\u732e\\u4e88\\u300c\\u5929\\u7a7a\\u300d\\u4e4b\\u8bd7";

    @Test
    public void theNamedCharacterGetsTwoLayersAndNobodyElseDoes() {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character hyacine = CharacterFactory.create(HYACINE, LEVEL);
        Character other = CharacterFactory.create(OTHER, LEVEL);
        Battle battle = new Battle(List.of(cyrene, hyacine, other),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        cyrene = battle.characters.get(0);
        hyacine = battle.characters.get(1);
        other = battle.characters.get(2);

        var demiurge = battle.summonServant(cyrene);
        battle.processRequests();
        Assertions.assertNotNull(demiurge, "precondition: the memosprite is out");
        Assertions.assertNotNull(demiurge.skillAt(1), "precondition: slot 1 exists");

        SkillExecutor.execute(battle, demiurge.skillAt(1), demiurge, List.of(battle.enemies.getFirst()));
        battle.processRequests();

        int named = hyacine.getBuffManager().stacksOf(MARK);
        int bystander = other.getBuffManager().stacksOf(MARK);
        System.out.println("[sky_stacks] the named character has " + named + " ; the other ally has " + bystander);

        Assertions.assertEquals(2, named,
                "\\u300c\\u4f7f\\u98ce\\u5807\\u83b7\\u5f97 2 \\u5c42\\u300d-- the data states LayerAddWhenStack: 2, and the sentence agrees");
        Assertions.assertEquals(0, bystander, "and nobody else -- the game names the cid, and so does `ally_cid:`");
    }
}
'''
io.open("src/test/java/com/laosun/aluminium/test/SkyOdeStackTest.java", "w", encoding="utf-8", newline="\n").write(JUDGE)
print("ok   judge written")
