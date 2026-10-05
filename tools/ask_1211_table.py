"""Ask the last question: is the reader rule even IN her table? (2026-10-02)

Seventeen rounds of measurement eliminated every engine-side explanation: the state's name matches byte for byte, `self` is
a legal state subject, `<subject> state_ended <name>` is the accepted shape, `tickBuff`'s three guards all pass for her
state, `afterMove` ticks it, `StateBuff` ticks late by design and `tickEffect` is `decreaseDuration()`. So
`battle.fireStateEnded(her, "生息")` should be called -- and yet a reader rule with NO condition at all stays silent.

That leaves one possibility: the rule is not in her table. This adds the rule and asks the table directly.
ASCII only.
"""
import io
import json

DATA = "src/main/resources/characters/1211.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/ReaderInTableTest.java"
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
    "note": "⭐ 本条用来回答“它到底进没进表”。",
})
if isinstance(doc, dict):
    doc["rules"] = rules
else:
    doc = rules
json.dump(doc, io.open(DATA, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   1211.json: the reader rule is present")

io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * One question (1211, 2026-10-02): is the STATE_ENDED reader in her table at all?
 *
 * <p>Seventeen rounds of measurement cleared the engine of every other explanation, so this asks the table directly instead
 * of asking an effect of it.
 */
public class ReaderInTableTest {
    private static final int OWNER = 1211;

    /** ⭐ The rule the document asks for must be loaded and selectable. */
    @Test
    public void theReaderIsInHerTable() {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        int count = owner.getTriggerTable().rulesFor(TriggerEvent.STATE_ENDED).size();
        Assertions.assertEquals(1, count,
                "the STATE_ENDED reader must be in her table (found " + count + ")");
    }
}
''')
print("ok   judge written")
