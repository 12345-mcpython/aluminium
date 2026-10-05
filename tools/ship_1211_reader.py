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
    "when": ["self state_ended 生息", "target_hp_percent >= 1"],
    "do": [{"op": "GAIN_ENERGY", "amount": 8.0, "target": "target"}],
    "source": ("1211 白露（行迹）: 「【生息】结束时**若我方目标当前生命值等于其生命上限**，"
               "则额外恢复目标 **8** 点能量」"),
    "note": ("⭐ 本段**第二个真** `STATE_ENDED` 读者 ✓ —— 形状与 `1513` 那个逐字对齐 ✓："
             "`on: STATE_ENDED` ✓ 十 **`<subject> state_ended <状态名>`** ✓（实测：缺主体会被当成 `is_state` 解析 ✗）。"
             "⭐ 量用 **`GAIN_ENERGY`** ✓（已接线 ✓，内容里 **137** 处在用 ✓）。"
             "⚠ 条件里的“生命值等于上限”用 **`target_hp_percent >= 1`** ✓"),
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
 * 「【生息】结束时若我方目标当前生命值等于其生命上限，则额外恢复目标 8 点能量」 (1211, 2026-10-02).
 *
 * <p>⭐ FILE-DRIVEN: her ult applies 【生息】, two turns of `afterMove` run it down, and the reader must hand back exactly
 * the document's 8 energy.
 */
public class InvigorationEndingTest {
    private static final int OWNER = 1211;
    private static final int MONSTER = 1002011;
    private static final String STATE = "生息";

    /** ⭐ The energy lands when the state ends. */
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
