"""1415's memosprite skill 23 「献予「律法」之诗」 -- the crit damage its meritorious allies get (2026-10-02).

Verbatim (1141523, params [0.15, 1]): 「整场生效，对刻律德菈施放后，<b>持有【军功】的角色暴击伤害提高 #1%</b>，奇袭结束后，使刻律德菈获得 #2 点充能。」

Measured before writing:
  * #1 runs with the level (0.15 -> 0.42), so it goes through `percent_from_cast_param: 0`; #2 is 1 at every level;
  * 刻律德菈 is cid 1412 in our own data ("Cerydra"), and her file already carries 【军功】 -- this rule reads HER own mark rather than spelling it again;
  * 「持有【军功】的角色」 is `target: "all_allies"` with `target_when: ["target has_state <the mark>"]` -- the shipped vocabulary (1103 uses exactly that shape for 「所有触电状态下的敌方目标」).

\u26d4 Registered rather than written: 「奇袭结束后，使刻律德菈获得 #2 点充能」 -- 「奇袭」 is a state/event our content does not model.
"""
import io
import json
import sys

CERYDRA = "src/main/resources/characters/1412.json"
SE = "src/main/resources/data/skill_effects.json"
SKILLS = "src/main/resources/data/skills.json"
SLOT = 23

# \u2b50 the mark's own name, taken from the file that APPLIES it -- no second spelling
doc = json.load(io.open(CERYDRA, encoding="utf-8"))
rules = doc if isinstance(doc, list) else doc.get("rules", [])
marks = []
for r in rules:
    for e in (r.get("do") or []):
        if e.get("op") == "APPLY_BUFF" and e.get("buff"):
            marks.append(e["buff"])
if not marks:
    sys.exit("REFUSING: 1412 applies no named mark to read")
# \u2b50 The sentence names its mark: \u300c\u6301\u6709\u3010\u519b\u529f\u3011\u7684\u89d2\u8272\u300d -- so the mark is looked up BY THAT NAME rather than guessed from a list.
WANTED = "\u519b\u529f"
print("1412's named marks: %d ; the sentence's own:\u300c%s\u300d" % (len(set(marks)), WANTED))
if WANTED not in marks:
    sys.exit("REFUSING: 1412 never applies \u300c%s\u300d -- the sentence would be naming a state that does not exist" % WANTED)
MARK = WANTED

table = json.load(io.open(SKILLS, encoding="utf-8"))
rows = table["11415"][str(SLOT)].get("param_list") or []
if not rows or rows[0][0] == rows[-1][0]:
    sys.exit("REFUSING: #1 does not run with level")
if len({r[1] for r in rows}) != 1:
    sys.exit("REFUSING: #2 is not constant")
print("ok   #1 runs %s -> %s ; #2 is %s at every level" % (rows[0][0], rows[-1][0], rows[0][1]))

RULE_ID = "memosprite_ode_of_law_raises_the_meritorious"
if any(r.get("id") == RULE_ID for r in rules):
    sys.exit("REFUSING: %s is already there" % RULE_ID)

rules.append({
    "id": RULE_ID,
    "on": "CAST_SETUP",
    "when": ["target == self", "actor is_summon", "from_skill_id == " + str(SLOT)],
    "do": [{
        "op": "MODIFY_ATTR",
        "attribute": "CRIT_DAMAGE",
        "percent_from_cast_param": 0,
        "permanent": True,
        "target": "all_allies",
        "target_when": ["target has_state " + MARK],
    }],
    "source": ("1415 \u6614\u6d9f \u5fc6\u7075\u6280\u80fd 17 \u300c\u732e\u4e88\u300c\u5f8b\u6cd5\u300d\u4e4b\u8bd7\u300d\uff08\u6570\u636e\u69fd\u4f4d 23\uff0cSkillID 1141523\uff09\uff1a"
               "\u300c\u6574\u573a\u751f\u6548\uff0c\u5bf9\u523b\u5f8b\u5fb7\u83c8\u65bd\u653e\u540e\uff0c**\u6301\u6709\u3010" + MARK + "\u3011\u7684\u89d2\u8272\u66b4\u51fb\u4f24\u5bb3\u63d0\u9ad8 #1%**\u3002\u300d"),
    "note": ("\u2b50 \u300c\u6301\u6709\u3010" + MARK + "\u3011\u7684\u89d2\u8272\u300d\u2192 `target: \"all_allies\"` \u52a0 `target_when: [\"target has_state "
             + MARK + "\"]`\uff08\u5df2\u51fa\u8d27\u8bcd\u6c47\uff1a1103 \u7528\u540c\u4e00\u5f62\u72b6\u6311\u300c\u6240\u6709\u89e6\u7535\u72b6\u6001\u4e0b\u7684\u654c\u65b9\u76ee\u6807\u300d\uff09\u3002"
             "\u2b50 `#1` \u968f\u7b49\u7ea7\u53d8\uff08**\u5b9e\u6d4b**\uff09\u2192 `percent_from_cast_param: 0`\u3002\u2b50 \u5370\u8bb0\u540d\u5b57**\u53d6\u81ea 1412 \u81ea\u5df1\u7684\u53e5\u5b50**\uff08\u800c\u4e0d\u662f\u91cd\u5199\u4e00\u904d\uff09\u3002"),
})

if isinstance(doc, list):
    out = rules
else:
    doc["rules"] = rules
    out = doc
io.open(CERYDRA, "w", encoding="utf-8", newline="\n").write(json.dumps(out, ensure_ascii=False, indent=2) + "\n")
print("ok   1412 now carries %s (%d rules)" % (RULE_ID, len(rules)))

effects = json.load(io.open(SE, encoding="utf-8"))
if str(SLOT) not in effects.get("11415", {}):
    effects.setdefault("11415", {})[str(SLOT)] = {
        "effect": "Rules",
        "source": ("1415 \u6614\u6d9f \u5fc6\u7075\u6280\u80fd 17 \u300c\u732e\u4e88\u300c\u5f8b\u6cd5\u300d\u4e4b\u8bd7\u300d\uff08\u6570\u636e\u69fd\u4f4d 23\uff09\uff1a"
                   "\u5de5\u4f5c\u5728**\u89c4\u5219\u4fa7**\uff0c\u6240\u4ee5\u662f `Rules` \u5f62\u72b6\u3002"),
        "note": "\u2b50 \u6ca1\u6709\u6761\u76ee\u5c31\u4e0d\u53ef\u4ea4\u4ed8\u3002",
    }
    io.open(SE, "w", encoding="utf-8", newline="\n").write(json.dumps(effects, ensure_ascii=False, indent=2) + "\n")
    print("ok   skill_effects.json: 11415/%d = Rules" % SLOT)

JUDGE = '''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1415's memosprite skill 23 \u300c\u732e\u4e88\u300c\u5f8b\u6cd5\u300d\u4e4b\u8bd7\u300d: \u300c\u6301\u6709\u3010\u519b\u529f\u3011\u7684\u89d2\u8272\u66b4\u51fb\u4f24\u5bb3\u63d0\u9ad8 #1%\u300d (2026-10-02).
 *
 * <p>\u2b50 The reading is TWO-SIDED, because the sentence names a SUBSET: an ally carrying the mark must gain the crit damage, and an ally without it must not. A
 * rule that simply boosted `all_allies` would pass the first half while being wrong about the sentence.
 */
public class LawOdeCritDamageTest {
    private static final int LEVEL = 80;
    private static final int CYRENE = 1415;
    private static final int CERYDRA = 1412;
    private static final int PLAIN = 1002;
    private static final int MONSTER = 1002011;
    private static final String MARK = "\\u519b\\u529f";

    @Test
    public void onlyTheMarkedAllyGainsTheCritDamage() {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character cerydra = CharacterFactory.create(CERYDRA, LEVEL);
        Character plain = CharacterFactory.create(PLAIN, LEVEL);
        Battle battle = new Battle(List.of(cyrene, cerydra, plain),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        cyrene = battle.characters.get(0);
        cerydra = battle.characters.get(1);
        plain = battle.characters.get(2);

        // put the mark on ONE of them, by a rule on that one's own table
        EffectSpec mark = new EffectSpec();
        TriggerSpecs.set(mark, "op", "APPLY_BUFF");
        TriggerSpecs.set(mark, "buff", MARK);
        TriggerSpecs.set(mark, "permanent", Boolean.TRUE);
        TriggerSpecs.set(mark, "target", "self");
        final Character marked = cerydra;
        marked.setTriggerTable(new TriggerTable(CERYDRA, List.of(
                TriggerSpecs.rule("BATTLE_START", List.of(), mark))));
        battle.fireTriggers(com.laosun.aluminium.enums.TriggerEvent.BATTLE_START);
        Assertions.assertTrue(marked.getBuffManager().hasState(MARK), "precondition: the mark landed");
        Assertions.assertFalse(plain.getBuffManager().hasState(MARK), "precondition: the other one is unmarked");

        double markedBefore = marked.getAttribute(AttributeType.CRIT_DAMAGE).get();
        double plainBefore = plain.getAttribute(AttributeType.CRIT_DAMAGE).get();

        var demiurge = battle.summonServant(cyrene);
        var ode = demiurge.skillAt(23);
        Assertions.assertNotNull(ode, "precondition: the memosprite carries slot 23");
        var row = ode.getData().getSkills().get(demiurge.skillLevel(ode) - 1);
        SkillExecutor.execute(battle, ode, demiurge, List.of(marked));
        battle.processRequests();

        double markedGain = marked.getAttribute(AttributeType.CRIT_DAMAGE).get() - markedBefore;
        double plainGain = plain.getAttribute(AttributeType.CRIT_DAMAGE).get() - plainBefore;
        System.out.println("[law] the marked ally gained " + markedGain + " (=" + row.get(0) + ")"
                + " ; the unmarked one gained " + plainGain);

        Assertions.assertEquals(row.get(0), markedGain, Math.abs(row.get(0)) * 1e-6,
                "\\u300c\\u6301\\u6709\\u3010\\u519b\\u529f\\u3011\\u7684\\u89d2\\u8272\\u66b4\\u51fb\\u4f24\\u5bb3\\u63d0\\u9ad8 #1%\\u300d-- and #1 runs with level");
        Assertions.assertEquals(0.0, plainGain, 1e-9,
                "and the ally WITHOUT the mark gains nothing -- \\u300c\\u6301\\u6709\\u3010\\u519b\\u529f\\u3011\\u7684\\u89d2\\u8272\\u300d names a subset");
    }
}
'''
io.open("src/test/java/com/laosun/aluminium/test/LawOdeCritDamageTest.java", "w", encoding="utf-8", newline="\n").write(JUDGE)
print("ok   judge written")
