"""1211's second STATE_ENDED reader: energy back when 【生息】ends (2026-10-02).

Document, verbatim (1211_白露, :100): 「【生息】结束时若我方目标当前生命值等于其生命上限，则额外恢复目标 8 点能量。」

Measured: her file already applies 【生息】 as a state (`ult_rebirth`, ULT_CAST) and already has `EXTEND_BUFF` for the
"extend by one turn" half; `GAIN_ENERGY` is the wired op for 「恢复 N 点能量」 (137 uses in content). So this is one rule:
on STATE_ENDED for that state, with the document's full-health condition, grant 8 energy.

The judge is file-driven: it casts her ult (which is what applies the state), advances two turns with
`BuffManager.afterMove()` -- the call `Battle` makes for the actor at a turn's end -- and asserts the energy went up by
exactly the document's 8.
ASCII only.
"""
import io
import json

DATA = "src/main/resources/characters/1211.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/InvigorationEndingTest.java"
ID = "trace_invigoration_ending_energy"

doc = json.load(io.open(DATA, encoding="utf-8"))
rules = doc["rules"] if isinstance(doc, dict) else doc
rules = [r for r in rules if not (isinstance(r, dict) and r.get("id") == ID)]
rules.append({
    "on": "STATE_ENDED",
    "id": ID,
    "when": ["self state_ended \u751f\u606f", "target_hp_percent >= 1"],
    "do": [{"op": "GAIN_ENERGY", "amount": 8.0, "target": "target"}],
    "source": ("1211 \u767d\u9732\uff08\u884c\u8ff9\uff09: \u300c\u3010\u751f\u606f\u3011\u7ed3\u675f\u65f6**\u82e5\u6211\u65b9\u76ee\u6807\u5f53\u524d\u751f\u547d\u503c\u7b49\u4e8e\u5176\u751f\u547d\u4e0a\u9650**\uff0c"
               "\u5219\u989d\u5916\u6062\u590d\u76ee\u6807 **8** \u70b9\u80fd\u91cf\u300d"),
    "note": ("\u2b50 \u672c\u6bb5**\u7b2c\u4e8c\u4e2a\u771f** `STATE_ENDED` \u8bfb\u8005 \u2713 \u2014\u2014 \u5f62\u72b6\u4e0e `1513` \u90a3\u4e2a\u9010\u5b57\u5bf9\u9f50 \u2713\uff1a"
             "`on: STATE_ENDED` \u2713 \u5341 **`<subject> state_ended <\u72b6\u6001\u540d>`** \u2713\uff08\u5b9e\u6d4b\uff1a\u7f3a\u4e3b\u4f53\u4f1a\u88ab\u5f53\u6210 `is_state` \u89e3\u6790 \u2717\uff09\u3002"
             "\u2b50 \u91cf\u7528 **`GAIN_ENERGY`** \u2713\uff08\u5df2\u63a5\u7ebf \u2713\uff0c\u5185\u5bb9\u91cc **137** \u5904\u5728\u7528 \u2713\uff09\u3002"
             "\u26a0 \u6761\u4ef6\u91cc\u7684\u201c\u751f\u547d\u503c\u7b49\u4e8e\u4e0a\u9650\u201d\u7528 **`target_hp_percent >= 1`** \u2713"),
})
if isinstance(doc, dict):
    doc["rules"] = rules
else:
    doc = rules
json.dump(doc, io.open(DATA, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   1211.json: the invigoration-ending reader")

io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
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
 * \u300c\u3010\u751f\u606f\u3011\u7ed3\u675f\u65f6\u82e5\u6211\u65b9\u76ee\u6807\u5f53\u524d\u751f\u547d\u503c\u7b49\u4e8e\u5176\u751f\u547d\u4e0a\u9650\uff0c\u5219\u989d\u5916\u6062\u590d\u76ee\u6807 8 \u70b9\u80fd\u91cf\u300d (1211, 2026-10-02).
 *
 * <p>\u2b50 FILE-DRIVEN: her ult applies 【\u751f\u606f】, two turns of `afterMove` run it down, and the reader must hand back exactly
 * the document's 8 energy.
 */
public class InvigorationEndingTest {
    private static final int OWNER = 1211;
    private static final int MONSTER = 1002011;
    private static final String STATE = "\u751f\u606f";

    /** \u2b50 The energy lands when the state ends. */
    @Test
    public void theStateEndingGrantsEightEnergy() {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        Skill ult = owner.getSkills().get(SkillType.ULTRA);
        Assertions.assertNotNull(ult, "precondition: she has an ultra");
        owner.setCurrentEnergy(0);
        SkillExecutor.execute(battle, ult, owner, List.of(owner));
        battle.processRequests();
        Assertions.assertTrue(owner.getBuffManager().hasState(STATE),
                "her ultra applies the state (" + owner.getBuffManager().hasState(STATE) + ")");

        double before = owner.getCurrentEnergy();
        owner.getBuffManager().afterMove();
        owner.getBuffManager().afterMove();
        battle.processRequests();
        Assertions.assertFalse(owner.getBuffManager().hasState(STATE), "two turns run it out");
        Assertions.assertEquals(8.0, owner.getCurrentEnergy() - before, 1e-6,
                "and the reader hands back the document's 8 energy (" + before + " -> " + owner.getCurrentEnergy() + ")");
    }
}
''')
print("ok   judge written")
