"""Discriminator (round 1687): the same effect in a CONTENT file, fired by a NON-nested event.

Two variables were left after the four-variant comparison: the content/Gson path, and the fact that 1513's rule fires inside
`STATE_ENDED` dispatch. This separates them with a temporary probe rule on TURN_START (removed again before any commit): if a
content rule on a plain event repeats the state, the nesting is the culprit; if it does not, the loaded effect is.

The probe is deliberately on 1513 (whose file already declares 笑点 and owns the reward rule) and uses a literal count, so the
party scale cannot be blamed.
"""
import io
import json
import sys

CHAR = "src/main/resources/characters/1513.json"
PROBE_ID = "probe_turn_start_stacks"
JUDGE = "src/test/java/com/laosun/aluminium/test/ProbeContentStacksTest.java"

with io.open(CHAR, encoding="utf-8") as handle:
    doc = json.load(handle)
if any(isinstance(rule, dict) and rule.get("id") == PROBE_ID for rule in doc["rules"]):
    sys.exit("REFUSING: the probe is already there")
doc["rules"].append({
    "on": "TURN_START",
    "id": PROBE_ID,
    "do": [{
        "op": "APPLY_BUFF",
        "buff": "好活当赏",
        "turns": 2,
        "target": "self",
        "stackable": True,
        "maxStacks": 99,
        "amount": 4,
    }],
    "source": "PROBE (round 1687): is a CONTENT-loaded stackable effect repeated at all?",
    "note": "PROBE ONLY -- removed before the next commit.",
})
with io.open(CHAR, "w", encoding="utf-8", newline="\n") as handle:
    json.dump(doc, handle, ensure_ascii=False, indent=2)
    handle.write("\n")
print("ok   the probe rule is in 1513's file")

io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/** Probe: does a CONTENT-loaded stackable effect repeat on a plain event? */
public class ProbeContentStacksTest {
    private static final String GIFT = "\\u597d\\u6d3b\\u5f53\\u8d4f";

    @Test
    public void theProbeReports() {
        Character sparkle = CharacterFactory.create(1513, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(sparkle),
                List.of(EnemyFactory.create(1002011, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        Signal signal = battle.queue.snapshot().stream()
                .filter(candidate -> candidate.getCanHit() == battle.allies.getFirst()).findFirst()
                .orElseThrow(() -> new AssertionError("precondition: a unit is in the queue"));
        battle.currentMove = signal;
        battle.beforeMove();
        battle.afterMove();
        battle.processRequests();
        int stacks = sparkle.getBuffManager().stacksOf(GIFT);
        System.out.println("[probe-content] stacks after a plain event=" + stacks);
        Assertions.assertEquals(4, stacks, "a content rule on a plain event repeats the state");
    }
}
''')
print("ok   probe judge written")
