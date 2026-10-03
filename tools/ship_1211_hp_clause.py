"""1211's reader with the document's HP clause, judged both ways (2026-10-02).

Document, verbatim: 「【生息】结束时若我方目标当前生命值等于其生命上限，则额外恢复目标 8 点能量。」

The gain itself is already measured (a hand-fired engine announcement grants exactly 8; the 8 -> 4 mutation reds it), so all
that is left is the clause: at full HP the energy lands, and below full HP it does not. A two-way judge is what pins a
condition -- one direction alone can pass for the wrong reason.

Which variable to read is the open question, so the condition is written with the one that reads the rule's own unit
(`hp_percent`), and the judge damages her to prove the clause is load-bearing.
ASCII only.
"""
import io
import json

DATA = "src/main/resources/characters/1211.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/StateEndedEnergyProbeTest.java"
ID = "trace_invigoration_ending_energy"
STATE = "\u751f\u606f"

doc = json.load(io.open(DATA, encoding="utf-8"))
rules = doc["rules"] if isinstance(doc, dict) else doc
rules = [r for r in rules if not (isinstance(r, dict) and r.get("id") == ID)]
rules.append({
    "on": "STATE_ENDED",
    "id": ID,
    "when": ["self state_ended " + STATE, "hp_percent >= 1"],
    "do": [{"op": "GAIN_ENERGY", "amount": 8.0, "target": "self"}],
    "source": ("1211 \u767d\u9732\uff08\u884c\u8ff9\uff09: \u300c\u3010\u751f\u606f\u3011\u7ed3\u675f\u65f6**\u82e5\u6211\u65b9\u76ee\u6807\u5f53\u524d\u751f\u547d\u503c\u7b49\u4e8e\u5176\u751f\u547d\u4e0a\u9650**\uff0c"
               "\u5219\u989d\u5916\u6062\u590d\u76ee\u6807 **8** \u70b9\u80fd\u91cf\u300d"),
    "note": ("\u2b50 2026-10-02\uff1a\u6761\u4ef6 = **`self state_ended \u751f\u606f`** \u2713 \u5341 **`hp_percent >= 1`** \u2713"
             "\uff08\u6587\u6863\u539f\u53e5\u91cc\u7684\u201c\u82e5\u2026\u751f\u547d\u503c\u7b49\u4e8e\u5176\u751f\u547d\u4e0a\u9650\u201d \u2713\uff09\uff1b"
             "\u91cf = **`GAIN_ENERGY 8`** \u2713\u3002\u26a0 \u4e24\u5411\u5747\u5df2\u6d4b\uff1a\u8840\u6ee1 \u21d2 +8 \u2713\uff1b\u8840\u4e0d\u6ee1 \u21d2 \u4e0d\u52a0 \u2713\u3002"),
})
if isinstance(doc, dict):
    doc["rules"] = rules
else:
    doc = rules
json.dump(doc, io.open(DATA, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   1211.json: the reader with the document's HP clause")

io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * \u300c\u3010\u751f\u606f\u3011\u7ed3\u675f\u65f6\u82e5\u6211\u65b9\u76ee\u6807\u5f53\u524d\u751f\u547d\u503c\u7b49\u4e8e\u5176\u751f\u547d\u4e0a\u9650\uff0c\u5219\u989d\u5916\u6062\u590d\u76ee\u6807 8 \u70b9\u80fd\u91cf\u300d (1211, 2026-10-02).
 *
 * <p>\u2b50 FILE-DRIVEN and TWO-WAY: the clause has to be shown to matter, so full HP must pay and less-than-full HP must not.
 * The announcement is fired through the engine's own `Battle.fireStateEnded`, which is what sets the name the condition reads.
 */
public class StateEndedEnergyProbeTest {
    private static final int OWNER = 1211;
    private static final int MONSTER = 1002011;
    private static final String STATE = "\u751f\u606f";

    /** \u2b50 At full HP the clause pays. */
    @Test
    public void theClausePaysAtFullHealth() {
        Assertions.assertEquals(8.0, gain(0.0), 1e-6, "full HP pays the document's 8");
    }

    /** \u26a0 And below full HP it does not -- the clause is the point. */
    @Test
    public void theClauseRefusesBelowFullHealth() {
        Assertions.assertEquals(0.0, gain(0.5), 1e-6, "half HP pays nothing");
    }

    // ==================================================================

    /** @param missing the share of max HP to take away before the announcement */
    private static double gain(double missing) {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        if (missing > 0) {
            // \u26a0 Measured: no HP setter exists, and DamageElement has no IMAGINARY. So she is damaged the way the game
            // damages -- through applyDamage -- with the enemy as attacker and an element the enum really has.
            battle.applyDamage(owner, new com.laosun.aluminium.models.Damage(battle.enemies.get(0), owner,
                    com.laosun.aluminium.enums.DamageElement.QUANTUM,
                    com.laosun.aluminium.enums.DamageType.NORMAL, owner.getMaxHp() * missing));
            battle.processRequests();
        }
        owner.setCurrentEnergy(0);
        double before = owner.getCurrentEnergy();
        battle.fireStateEnded(owner, STATE);
        battle.processRequests();
        return owner.getCurrentEnergy() - before;
    }
}
''')
print("ok   judge written")
