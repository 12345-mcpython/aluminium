"""Slot 03's fourth sentence, on the event that actually fires (2026-10-02, final round).

Probe, measured: a memosprite's cast raises the master's DEFENCE through a `CAST_SETUP` rule and raises her ATTACK through a `SKILL_CAST` rule by **0.0** -- so `SKILL_CAST` is
NOT fired for a memosprite's skill use, and the shortening rule (which hung on it) never ran. That is why two rounds of judging saw no change: the rule was not reached.

⭐ `CAST_SETUP` is the event a memosprite's cast does announce. The sentence says 「施放技能**后**」, and the ordering is UNOBSERVABLE here: the effect only shortens the
remaining turns of effects that already exist, and nothing in the cast's own resolution reads those durations. So hanging it on the setup event changes no observable.

The reading puts a 2-turn mark on BOTH units (an ally's rule, `all_allies`): the master's is shortened to 1 and one tick ends it, while the memosprite's own is untouched and
survives. One scene, two halves.
"""
import io
import json
import sys

MEMOSPRITE = "src/main/resources/characters/1415.json"
SE = "src/main/resources/data/skill_effects.json"
SLOT = 3
MARK = "测试持续效果"

doc = json.load(io.open(MEMOSPRITE, encoding="utf-8"))
rules = doc if isinstance(doc, list) else doc.get("rules", [])
rules = [r for r in rules if not str(r.get("id", "")).startswith("probe_")]
RULE_ID = "after_it_casts_every_ongoing_effect_shortens_by_one"
if any(r.get("id") == RULE_ID for r in rules):
    sys.exit("REFUSING: %s is already there" % RULE_ID)

rules.append({
    "id": RULE_ID,
    "on": "CAST_SETUP",
    "when": ["actor is_summon"],
    "do": [{"op": "EXTEND_BUFF", "kind": "all", "turns": -1, "target": "self"}],
    "source": ("1415 昔涟 忆灵技能 03 「等待，在所有的过去」（数据槽位 03，SkillID 1141503）："
               "「**德谬歌施放技能后使自身所有持续效果持续回合数减 1**。」"),
    "note": ("⭐ **所有**→ `EXTEND_BUFF` + `\"kind\": \"all\"`；**减 1** → `turns: -1`（`requireSignedTurns` 允许负数）；"
             "`self` 在本文件里是**主人**（**实测**）。"
             "⭐ **挂 `CAST_SETUP` 而不是 `SKILL_CAST`**：**实测**（探针）—— 忆灵的施放会发 `CAST_SETUP`（主人 DEFENCE +291.06）"
             "但**不发** `SKILL_CAST`（主人 ATTACK +0.0）。⚠ 而“施放**后**”这个顺序**不可观测**："
             "效果只改已有持续效果的剩余回合，而施放本身的结算不读它们。"),
})
if isinstance(doc, list):
    out = rules
else:
    doc["rules"] = rules
    out = doc
io.open(MEMOSPRITE, "w", encoding="utf-8", newline="\n").write(json.dumps(out, ensure_ascii=False, indent=2) + "\n")
print("ok   1415 carries the rule on CAST_SETUP (%d rules)" % len(rules))

effects = json.load(io.open(SE, encoding="utf-8"))
if str(SLOT) not in effects.get("11415", {}):
    effects.setdefault("11415", {})[str(SLOT)] = {
        "effect": "Rules",
        "source": "1415 昔涟 忆灵技能 03 「等待，在所有的过去」（数据槽位 03）：工作在规则侧。",
        "note": "⭐ 本条技能的第四句已成句。",
    }
    io.open(SE, "w", encoding="utf-8", newline="\n").write(json.dumps(effects, ensure_ascii=False, indent=2) + "\n")
    print("ok   skill_effects.json: 11415/%d = Rules" % SLOT)

JUDGE = '''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Summon;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1415's memosprite skill 03, fourth sentence: 「德谬歌施放技能后使自身所有持续效果持续回合数减 1」 (2026-10-02).
 *
 * <p>⭐ One scene, two halves (`self` in this file is the MASTER): an ally's rule puts a 2-turn mark on `all_allies`, the memosprite uses its skill, and one tick follows.
 * The master's mark went 2 -> 1 and that tick ends it; the memosprite's own is untouched and survives.
 *
 * <p>⚠ A one-turn mark cannot discriminate: `extendDuration` is `Math.max(0, remaining + turns)` and a 1-turn buff ticks away on its own either way.
 */
public class CastShortensOwnEffectsTest {
    private static final int LEVEL = 80;
    private static final int CYRENE = 1415;
    private static final int ALLY = 1002;
    private static final int MONSTER = 1002011;
    private static final String MARK = "\\u6d4b\\u8bd5\\u6301\\u7eed\\u6548\\u679c";

    @Test
    public void theMastersEffectShortensButTheMemospriteOwnDoesNot() {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Battle battle = new Battle(List.of(cyrene, ally),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        cyrene = battle.characters.get(0);
        ally = battle.characters.get(1);

        Summon demiurge = battle.summonServant(cyrene);
        battle.processRequests();
        Assertions.assertNotNull(demiurge, "precondition: the memosprite is out");

        EffectSpec mark = new EffectSpec();
        TriggerSpecs.set(mark, "op", "APPLY_BUFF");
        TriggerSpecs.set(mark, "buff", MARK);
        TriggerSpecs.set(mark, "turns", 2);
        TriggerSpecs.set(mark, "target", "all_allies");
        ally.setTriggerTable(new TriggerTable(ALLY, List.of(
                TriggerSpecs.rule("TURN_START", List.of(), mark))));
        battle.fireTriggers(TriggerEvent.TURN_START);
        Assertions.assertTrue(cyrene.getBuffManager().hasState(MARK), "precondition: the master wears the 2-turn mark");
        Assertions.assertTrue(demiurge.getBuffManager().hasState(MARK), "precondition: so does the memosprite");

        Assertions.assertNotNull(demiurge.skillAt(1), "precondition: slot 1 exists");
        SkillExecutor.execute(battle, demiurge.skillAt(1), demiurge, List.of(battle.enemies.getFirst()));
        battle.processRequests();

        cyrene.afterMove(battle);
        cyrene.getBuffManager().afterMove();
        demiurge.afterMove(battle);
        demiurge.getBuffManager().afterMove();
        boolean masterStill = cyrene.getBuffManager().hasState(MARK);
        boolean spriteStill = demiurge.getBuffManager().hasState(MARK);
        System.out.println("[shortens] after its skill and one tick: master still has the mark = " + masterStill
                + " ; the memosprite still has it = " + spriteStill);

        Assertions.assertFalse(masterStill,
                "\\u300c\\u5fb7\\u8c2c\\u6b4c\\u65bd\\u653e\\u6280\\u80fd\\u540e\\u4f7f\\u81ea\\u8eab\\u6240\\u6709\\u6301\\u7eed\\u6548\\u679c\\u6301\\u7eed\\u56de\\u5408\\u6570\\u51cf 1\\u300d-- 2 became 1, so one tick ends it");
        Assertions.assertTrue(spriteStill,
                "and the MEMOSPRITE's own mark is untouched -- `self` in this file is the master");
    }
}
'''
io.open("src/test/java/com/laosun/aluminium/test/CastShortensOwnEffectsTest.java", "w", encoding="utf-8", newline="\n").write(JUDGE)
print("ok   judge written")
