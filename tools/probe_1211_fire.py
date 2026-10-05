"""The legal way to fire the event: `Battle.fireStateEnded` (2026-10-02).

Nineteen hypotheses were eliminated by measurement, and the survivors are these two: either the energy op does not land on
this target, or the expiry tick never reaches the announcement. The earlier hand-fired test used plain `fireTriggers`, which
carries no state name (the name rides in `Battle.lastStateEndedName`, set only by `fireStateEnded`) -- so it proved nothing.

This fires the event the way the engine does, and asks the one question: does `GAIN_ENERGY` land?
  * energy up  -> the whole chain (event, rule, op) works, and the missing link is the expiry tick;
  * energy flat -> the op's resolution on this target is what is wrong.
ASCII only.
"""
import io
import json

DATA = "src/main/resources/characters/1211.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/StateEndedEnergyProbeTest.java"
ID = "trace_invigoration_ending_energy"
STATE = "生息"

doc = json.load(io.open(DATA, encoding="utf-8"))
rules = doc["rules"] if isinstance(doc, dict) else doc
rules = [r for r in rules if not (isinstance(r, dict) and r.get("id") == ID)]
rules.append({
    "on": "STATE_ENDED",
    "id": ID,
    "when": ["self state_ended " + STATE],
    "do": [{"op": "GAIN_ENERGY", "amount": 8.0, "target": "self"}],
    "source": "1211 白露：【生息】结束时恢复 8 点能量。",
    "note": "⭐ 用 `Battle.fireStateEnded` 测“能量能不能到位”。",
})
if isinstance(doc, dict):
    doc["rules"] = rules
else:
    doc = rules
json.dump(doc, io.open(DATA, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   1211.json: the reader rule is present")

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
 * Does `GAIN_ENERGY` land when the engine's own announcement is fired? (1211, 2026-10-02).
 *
 * <p>⭐ FILE-DRIVEN, and the event is fired through `Battle.fireStateEnded` -- the only entry that sets the state name the
 * condition reads (`lastStateEndedName`).
 */
public class StateEndedEnergyProbeTest {
    private static final int OWNER = 1211;
    private static final int MONSTER = 1002011;
    private static final String STATE = "生息";

    /** ⭐ The reader must hand back 8 energy for this event. */
    @Test
    public void theReaderLandsEnergyForTheAnnouncedState() {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        owner.setCurrentEnergy(0);
        double before = owner.getCurrentEnergy();
        battle.fireStateEnded(owner, STATE);
        battle.processRequests();

        Assertions.assertEquals(8.0, owner.getCurrentEnergy() - before, 1e-6,
                "the reader must grant the document's 8 energy when the engine announces this state's end ("
                        + before + " -> " + owner.getCurrentEnergy() + ")");
    }
}
''')
print("ok   judge written")
