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
MARK = "\u732e\u4e88\u300c\u5929\u7a7a\u300d\u4e4b\u8bd7"
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
    "source": ("1415 \u6614\u6d9f \u5fc6\u7075\u6280\u80fd 19 \u300c\u732e\u4e88\u300c\u5929\u7a7a\u300d\u4e4b\u8bd7\u300d\uff08\u6570\u636e\u69fd\u4f4d 19\uff0cSkillID 1141519\uff09\uff1a"
               "\u300c**\u5fb7\u8c2c\u6b4c\u65bd\u653e\u5fc6\u7075\u6280\u65f6\uff0c\u4f7f\u98ce\u5807\u83b7\u5f972\u5c42\u3010" + MARK + "\u3011**\u3002\u300d"),
    "note": ("\u2b50 \u80fd\u529b\u6570\u636e\u81ea\u5df1\u5c31\u662f\u8fd9\u4e48\u5199\u7684\uff08**\u5b9e\u6d4b**\uff09\uff1a"
             "`AddModifier` **`MServant_CyreneServant_00_AmazingBuff_Hyacine`** + `LayerAddWhenStack: 2`\uff0c"
             "\u800c\u524d\u7f6e\u662f **`ByCompareCharacterID` = 1409** \u2014\u2014 \u6e38\u620f\u5c31\u662f\u201c\u6309 cid \u70b9\u540d\u201d\uff0c"
             "\u4e0e `ally_cid:` \u662f\u540c\u4e00\u4ef6\u4e8b\u3002"
             "\u2b50 **\u4e0a\u9650\u5199 99999 \u662f\u6e38\u620f\u81ea\u5df1\u7684\u60ef\u4f8b**\uff0c\u4e0d\u662f\u6211\u7f16\u7684\uff1a"
             "\u63d0\u5230\u8be5\u4fee\u9970\u7684\u56db\u4e2a\u6587\u4ef6\u91cc\uff0c\u5b83\u65c1\u8fb9**\u6ca1\u6709** `MaxLayer`\uff08`AvatarStatusConfig` \u7684\u884c\u751a\u81f3\u6ca1\u6709\u8ba1\u6570\u5217\uff09\uff0c"
             "\u800c\u540c\u4e00\u4e2a\u80fd\u529b\u6587\u4ef6\u91cc\u201c\u4e0d\u9650\u201d\u5199\u7684\u5c31\u662f `MaxLayer: 99999`\uff08\u4e24\u5904\uff09\u21d2 \u7f3a\u7701\u5c31\u662f\u201c\u4e0d\u9650\u201d\u3002"
             "\u26a0 \u4e0d\u5199\u5b83\u7684\u8bdd\uff0c\u5f15\u64ce\u7684 `StackBuff`\uff08`Math.max(1, maxStacks)`\uff09\u4f1a\u53ea\u7ed9 **1** \u5c42\u3002"),
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
    effects["11415"][str(SLOT)]["note"] = ("\u2b50 2026-10-02\uff1a\u540c\u4e00\u6761\u6280\u80fd\u7684**\u80fd\u91cf\u90a3\u534a**\u4e0e**\u5c42\u6570\u90a3\u534a**\u5747\u5df2\u6210\u53e5\u3002")
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
 * 1415's memosprite skill 19, first sentence: \u300c\u5fb7\u8c2c\u6b4c\u65bd\u653e\u5fc6\u7075\u6280\u65f6\uff0c\u4f7f\u98ce\u5807\u83b7\u5f972\u5c42\u3010\u732e\u4e88\u300c\u5929\u7a7a\u300d\u4e4b\u8bd7\u3011\u300d (2026-10-02).
 *
 * <p>\u2b50 TWO-SIDED in one battle: the character the game NAMES BY CID (1409, measured in the ability data) gets the 2 layers the data states, and a different ally present gets
 * none. \u26a0 A cap had to be stated -- the data puts no `MaxLayer` beside this modifier, and our `StackBuff` clamps to 1 without one; 99999 is how this kit spells "no limit".
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
