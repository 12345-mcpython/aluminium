"""Slot 23 for real -- and the judge stops deleting the rule it is testing (2026-10-02).

Six single-variable runs narrowed this to nothing: the filter, `target == self`, the attribute's readability, `all_allies`, a literal share and a DERIVED share all work. What
was left was the judge itself: it put the mark on 刻律德菈 with

    cerydra.setTriggerTable(new TriggerTable(CERYDRA, List.of(markRule)))

which REPLACES her whole table -- including the very rule under test. ⚠ That is the table-replacement trap again, this time silently deleting the subject.

So the mark now comes from HER OWN kit: her skill's rule is what grants 【军功】 (1412's own note says so), so the judge casts her skill and lets the game do it. Nothing is
replaced.
"""
import io
import json
import sys

CERYDRA = "src/main/resources/characters/1412.json"
SE = "src/main/resources/data/skill_effects.json"
SLOT = 23
MARK = "军功"

doc = json.load(io.open(CERYDRA, encoding="utf-8"))
rules = doc if isinstance(doc, list) else doc.get("rules", [])
rules = [r for r in rules if not str(r.get("id", "")).startswith("probe_")]
RULE_ID = "memosprite_ode_of_law_raises_the_meritorious"
if any(r.get("id") == RULE_ID for r in rules):
    sys.exit("REFUSING: %s is already there" % RULE_ID)

rules.append({
    "id": RULE_ID,
    "on": "CAST_SETUP",
    "when": ["target == self", "actor is_summon", "from_skill_id == " + str(SLOT)],
    "do": [{
        "op": "MODIFY_ATTR",
        "attribute": "CRIT_ATTACK",
        "percent_from_cast_param": 0,
        "permanent": True,
        "target": "all_allies",
        "target_when": ["target has_state " + MARK],
    }],
    "source": ("1415 昔涟 忆灵技能 17 「献予「律法」之诗」（数据槽位 23，SkillID 1141523）："
               "「整场生效，对刻律德菈施放后，**持有【" + MARK + "】的角色暴击伤害提高 #1%**。」"),
    "note": ("⭐ 六轮单变量实验后，形状与引擎行为都已验证：`all_allies` 好、`target_when` 在已出货词汇里、"
             "字面量与**派生**份额都能落地（实测：字面量 +0.25、派生 +0.4199999999999591）。"
             "⭐ 占比是**施放技能的第 0 参数**（随等级变：0.15 → 0.42）。"),
})
if isinstance(doc, list):
    out = rules
else:
    doc["rules"] = rules
    out = doc
io.open(CERYDRA, "w", encoding="utf-8", newline="\n").write(json.dumps(out, ensure_ascii=False, indent=2) + "\n")
print("ok   1412 carries the real rule, probes gone (%d rules)" % len(rules))

effects = json.load(io.open(SE, encoding="utf-8"))
if str(SLOT) not in effects.get("11415", {}):
    effects.setdefault("11415", {})[str(SLOT)] = {
        "effect": "Rules",
        "source": "1415 昔涟 忆灵技能 17 「献予「律法」之诗」（数据槽位 23）：工作在规则侧。",
        "note": "⭐ 没有条目就不可交付。",
    }
    io.open(SE, "w", encoding="utf-8", newline="\n").write(json.dumps(effects, ensure_ascii=False, indent=2) + "\n")
    print("ok   skill_effects.json: 11415/%d = Rules" % SLOT)

JUDGE = '''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1415's memosprite skill 23 「献予「律法」之诗」: 「持有【军功】的角色暴击伤害提高 #1%」 (2026-10-02).
 *
 * <p>⭐ Nothing is replaced here. The mark 【军功】 is granted by 1412's OWN kit, so the judge casts her skill and lets the game do it -- an earlier version
 * replaced her whole trigger table with a hand-built mark rule, which silently deleted the very rule under test (the table-replacement trap, again).
 *
 * <p>⭐ The reading is TWO-SIDED: the ally carrying the mark gains the crit damage, the ally without it gains nothing.
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

        // ⭐ HER OWN skill is what grants 【军功】 -- cast it at herself, and no table is ever replaced
        var herSkill = cerydra.getSkills().get(SkillType.SKILL);
        Assertions.assertNotNull(herSkill, "precondition: 1412 has a SKILL");
        SkillExecutor.execute(battle, herSkill, cerydra, List.of(cerydra));
        battle.processRequests();
        Assertions.assertTrue(cerydra.getBuffManager().hasState(MARK),
                "precondition: her own kit put the mark on her");
        Assertions.assertFalse(plain.getBuffManager().hasState(MARK), "precondition: the other one is unmarked");

        double markedBefore = cerydra.getAttribute(AttributeType.CRIT_ATTACK).get();
        double plainBefore = plain.getAttribute(AttributeType.CRIT_ATTACK).get();

        var demiurge = battle.summonServant(cyrene);
        var ode = demiurge.skillAt(23);
        Assertions.assertNotNull(ode, "precondition: the memosprite carries slot 23");
        var row = ode.getData().getSkills().get(demiurge.skillLevel(ode) - 1);
        SkillExecutor.execute(battle, ode, demiurge, List.of(cerydra));
        battle.processRequests();

        double markedGain = cerydra.getAttribute(AttributeType.CRIT_ATTACK).get() - markedBefore;
        double plainGain = plain.getAttribute(AttributeType.CRIT_ATTACK).get() - plainBefore;
        System.out.println("[law] marked CRIT_ATTACK gain = " + markedGain + " (=" + row.get(0) + ")"
                + " ; unmarked gain = " + plainGain);

        Assertions.assertEquals(row.get(0), markedGain, Math.abs(row.get(0)) * 1e-6,
                "\\u300c\\u6301\\u6709\\u3010\\u519b\\u529f\\u3011\\u7684\\u89d2\\u8272\\u66b4\\u51fb\\u4f24\\u5bb3\\u63d0\\u9ad8 #1%\\u300d-- and #1 runs with level");
        Assertions.assertEquals(0.0, plainGain, 1e-9,
                "and an ally WITHOUT it gains nothing -- \\u300c\\u6301\\u6709\\u3010\\u519b\\u529f\\u3011\\u7684\\u89d2\\u8272\\u300d names a subset");
    }
}
'''
io.open("src/test/java/com/laosun/aluminium/test/LawOdeCritDamageTest.java", "w", encoding="utf-8", newline="\n").write(JUDGE)
print("ok   judge written (nothing replaced)")
