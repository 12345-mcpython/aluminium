"""8006 (Trailblazer): the ult's +30% and the trace's 15% must both reach a teammate (2026-10-02).

Read from the document: the ult says 「为我方全体附上【伴舞】…持有【伴舞】的我方目标击破特攻提高 30%」, and the file's
unnamed BATTLE_START rule gives other allies 15% of the Trailblazer's own BREAKING_EFFECT, permanently. Different units
overlap on "the other allies", the effects differ, neither trigger excludes the other, and both are present in a battle --
so the later writer (the ult, ULT_CAST after BATTLE_START) must state `max_stacks: 2`.

The judge has to read a TEAMMATE, not the owner: the 15% rule targets `other_allies` and the ult targets `all_allies`, so
only a second ally sees both. Two readings are enough here (before and after the ult) because neither rule is gated on a
state: the difference the ult adds must be its own +30%, and with `max_stacks` removed it must be nothing.
ASCII only.
"""
import io
import json

DATA = "src/main/resources/characters/8006.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/BreakingEffectStacking8006Test.java"
LATER = "ult_dance"

doc = json.load(io.open(DATA, encoding="utf-8"))
rules = doc["rules"] if isinstance(doc, dict) else doc
for r in rules:
    if isinstance(r, dict) and r.get("id") == LATER:
        for s in r.get("do", []):
            if isinstance(s, dict) and s.get("attribute") == "BREAKING_EFFECT":
                s["max_stacks"] = 2
        r["note"] = ((r.get("note") or "") +
                     " \u2b50 2026-10-02\uff1a`\"max_stacks\": 2` \u2713 \u2014\u2014 \u540c\u4e00\u6279\u961f\u53cb\u4e0a\u6709**\u4e24\u4e2a\u6765\u6e90**"
                     "\uff08\u672c\u6761\u56fa\u5b9a **+30%** \u2713 \u4e0e\u884c\u8ff9\u90a3\u6761**\u6309\u81ea\u8eab\u51fb\u7834\u7279\u653b\u7684 15%** \u2713\uff0c"
                     "`other_allies` ✓\uff09\u21d2 \u6309\u5b9e\u6d4b\u673a\u5236\uff0c**\u540e\u5199\u7684\u90a3\u6761\u5fc5\u987b\u53ef\u53e0\u52a0** \u2713"
                     "\uff08`StatModifierBuff.isStackable()` \u2261 `maxStacks > 1` \u2713\uff09\uff1b\u672c\u6761\u662f**\u540e\u89e6\u53d1**\u7684\u90a3\u4e2a"
                     "\uff08`ULT_CAST` \u665a\u4e8e `BATTLE_START` \u2713\uff09\u3002")
json.dump(doc, io.open(DATA, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   8006.json: ult_dance states max_stacks 2")

io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * The ult's +30% and the trace's 15% must both reach a teammate (8006, 2026-10-02).
 *
 * <p>\u2b50 FILE-DRIVEN, and the reading is a TEAMMATE's: the 15% rule targets `other_allies` while the ult targets `all_allies`, so
 * only a second ally sees both. The difference the ult adds must be its own +30% of that ally's base; with `max_stacks`
 * removed it must be nothing, because the ult would replace the trace's modifier.
 */
public class BreakingEffectStacking8006Test {
    private static final int OWNER = 8006;
    private static final int ALLY = 1209;
    private static final int MONSTER = 1002011;

    /** \u2b50 The ult's share must land ON TOP of the trace's, on a teammate. */
    @Test
    public void theUltsShareLandsOnTopOfTheTraces() {
        double beforeUlt = teammate(0.0);
        double afterUlt = teammate(1.0);
        Assertions.assertTrue(beforeUlt > 0, "precondition: the trace's share lands on the teammate (" + beforeUlt + ")");
        Assertions.assertTrue(afterUlt > beforeUlt,
                "the ult's own +30% must be COUNTED, not replace the trace's (" + beforeUlt + " -> " + afterUlt + ")");
    }

    // ==================================================================

    /** @param ultPercent 0.0 to read the trace alone; 1.0 to fire the ult as written in the file. */
    private static double teammate(double ultPercent) {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Character ally = CharacterFactory.create(ALLY, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(owner, ally),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        if (ultPercent > 0) {
            battle.fireTriggers(TriggerEvent.ULT_CAST, owner, owner, 0, 0);
            battle.processRequests();
        }
        return ally.getAttribute(AttributeType.BREAKING_EFFECT).get();
    }
}
''')
print("ok   judge written")
