"""1008: 「该效果在触发 1 次后或持续 2 回合后自动解除」 -- the survival trace and both of its expiries (2026-10-02, item 53).

Document, verbatim (`eidolons.json` -> 1008/4):
  「进入战斗后，受到致命攻击时阿兰不会陷入无法战斗状态，并立即回复至自身生命上限的 **#1**。
    **该效果在触发 1 次后或持续 #2 回合后自动解除**。」
PARAM (measured): `[0.25, 2]` -> #1 = **25%**, #2 = **2 turns**.

\u2b50 THE READING THAT MAKES BOTH HALVES WRITABLE: the sentence talks about 「该效果」 -- the TRACE ITSELF -- not about the heal.
So the trace is a state, and it has exactly two ways to end:
  (a) 触发 1 次后 -- the save removes it as part of its own effects (`REMOVE_STATE`, the explicit-removal route that announces
      `STATE_ENDED` -- measured in round 1619);
  (b) 持续 2 回合后 -- its own two-turn duration runs out, which the engine already does for any timed buff.
Both spellings are shipped: `APPLY_BUFF`/`REMOVE_STATE` are 1408's transformation pair, and `has_state` reads the state.

\u26a0 Naming: the document calls it only 「该效果」. The state is named after the eidolon itself (绝处反击 / Turn the Tables) and
that choice is written down here rather than left implicit.

\u26a0 Duration owner: a timed buff ticks on ITS WEARER's turns, so 「持续 2 回合」 is read as two of Arlan's turns. Stated, not assumed.
"""
import io
import json

DATA = "src/main/resources/characters/1008.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/ArlanEidolonFourTest.java"
SAVE = "e4_survives_a_lethal_blow_at_a_quarter"
TRACE = "e4_trace"
STATE = "\u7edd\u5904\u53cd\u51fb"

doc = json.load(io.open(DATA, encoding="utf-8"))
isObject = isinstance(doc, dict)
rules = doc["rules"] if isObject else doc
rules = [r for r in rules if not (isinstance(r, dict) and r.get("id") in (SAVE, TRACE))]
if len(rules) != 3:
    raise SystemExit("expected the three other shipped 1008 rules, found " + str(len(rules)))

source_trace = ("1008 \u963f\u5170 \u661f\u9b42 4 \u300c\u7edd\u5904\u53cd\u51fb / Turn the Tables\u300d\uff1a\u300c**\u8fdb\u5165\u6218\u6597\u540e**\uff0c"
                "\u53d7\u5230\u81f4\u547d\u653b\u51fb\u65f6\u963f\u5170\u4e0d\u4f1a\u9677\u5165**\u65e0\u6cd5\u6218\u6597\u72b6\u6001**\uff0c"
                "\u5e76\u7acb\u5373**\u56de\u590d\u81f3**\u81ea\u8eab\u751f\u547d\u4e0a\u9650\u7684 **25%**\u3002"
                "**\u8be5\u6548\u679c\u5728\u89e6\u53d1 1 \u6b21\u540e\u6216\u6301\u7eed 2 \u56de\u5408\u540e\u81ea\u52a8\u89e3\u9664**\u300d"
                "\uff08`param = [0.25, 2]` \u21d2 #1 = 25% \u2713\uff0c#2 = **2 \u56de\u5408** \u2713\uff09")

rules.append({
    "on": "BATTLE_START",
    "id": TRACE,
    "min_eidolon": 4,
    "do": [{"op": "APPLY_BUFF", "buff": STATE, "turns": 2, "target": "self"}],
    "source": source_trace,
    "note": ("\u2b50 2026-10-02\uff08\u7b2c 53 \u4ef6\uff09\uff1a\u2b50 **\u628a\u300c\u8be5\u6548\u679c\u300d\u5efa\u6210\u4e00\u4e2a\u72b6\u6001** \u2713 \u2014\u2014 "
             "\u539f\u53e5\u8bf4\u7684\u662f\u300c\u8be5**\u6548\u679c**\u2026\u81ea\u52a8\u89e3\u9664\u300d\u2713\uff0c\u800c**\u4e0d\u662f**\u90a3\u4e2a\u6cbb\u7597 \u2717"
             "\u21d2 \u6240\u4ee5\u5b83\u662f\u4e00\u4e2a**\u72b6\u6001**\uff0c\u5e76\u4e14\u5b83\u6709**\u4e24\u6761**\u7ed3\u675f\u8def\uff1a"
             "\u2460\u300c\u89e6\u53d1 1 \u6b21\u540e\u300d\uff08\u6551\u4eba\u7684\u90a3\u6761\u89c4\u5219\u81ea\u5df1 `REMOVE_STATE` \u2713\uff09\uff1b"
             "\u2461\u300c\u6301\u7eed 2 \u56de\u5408\u540e\u300d\uff08\u5b83\u81ea\u5df1\u7684**\u65f6\u957f**\u5230\u70b9 \u2713\uff09\u3002"
             "\u2b50 \u62fc\u6cd5\u5168\u662f\u73b0\u6210\u7684 \u2713\uff08`APPLY_BUFF`\uff0f`REMOVE_STATE` \u5c31\u662f 1408 \u53d8\u8eab\u90a3\u5bf9 \u2713\uff09"
             "\u2014\u2014 **\u65e0\u9700\u5f15\u64ce\u6539\u52a8** \u2713\u3002"
             "\u26a0 \u540d\u5b57\uff1a\u539f\u6587\u53ea\u53eb\u300c\u8be5\u6548\u679c\u300d\u2717 \u21d2 \u72b6\u6001\u7528\u661f\u9b42\u81ea\u5df1\u7684\u540d\u5b57\u547d\u540d \u2713\uff08\u5199\u5728\u8fd9\u91cc \u2713\uff09\u3002"
             "\u26a0 \u65f6\u957f\u5f52\u5c5e\uff1a\u5b9a\u65f6 buff \u5728**\u5176\u6301\u6709\u8005**\u7684\u56de\u5408\u8fb9\u754c\u6ed1\u8d70 \u2713 \u21d2 \u300c\u6301\u7eed 2 \u56de\u5408\u300d"
             "\u8bfb\u4f5c**\u963f\u5170\u81ea\u5df1\u7684 2 \u4e2a\u56de\u5408** \u2713\uff08\u5199\u51fa\u6765\uff0c\u4e0d\u5047\u8bbe \u2713\uff09\u3002"),
})

rules.append({
    "on": "LETHAL_DAMAGE",
    "id": SAVE,
    "min_eidolon": 4,
    "when": ["actor == self", "self has_state " + STATE],
    "do": [
        {"op": "HEAL", "scale": "owner_max_hp", "percent": 0.25, "target": "self"},
        {"op": "REMOVE_STATE", "buff": STATE, "target": "self"},
    ],
    "source": source_trace,
    "note": ("\u2b50 2026-10-02\uff1a\u2b50 **`LETHAL_DAMAGE` \u7684\u7b2c\u4e94\u4f4d\u8bfb\u8005** \u2713\uff0c\u800c\u4e14\u662f**\u7b2c\u4e00\u4f4d\u8bf4\u7684\u662f\u201c\u56de\u590d**\u81f3**\u201d**"
             "\uff08\u4e0a\u9650\uff09\u800c\u4e0d\u662f\u201c\u56de\u590d\u201d\uff08\u6570\u91cf\uff09\u7684 \u2713\u3002\u2757 **\u800c\u5728\u8fd9\u6761\u8def\u4e0a\u4e24\u8005**\u6070\u597d\u91cd\u5408**** \u2713\uff1a"
             "`LETHAL_DAMAGE` \u53d1\u51fa\u65f6\u76ee\u6807**\u5df2\u7ecf\u5728 0 \u8840** \u2713 \u21d2 \u88ab\u6cbb\u7597 25% \u540e\u7ed3\u679c**\u5c31\u662f 25% \u4e0a\u9650** \u2713 \u21d2 "
             "**\u4e0d\u6d89\u53ca\u8fd1\u4f3c** \u2713\u3002"
             "\u2b50 \u672c\u6761\u73b0\u5728\u5199\u5168\u4e86\u540c\u4e00\u53e5\u91cc\u7684**\u4e24\u6761\u7ed3\u675f\u8def** \u2713\uff1a\u2460 `has_state` \u5b88\u536b \u2713\uff08\u300c\u6301\u7eed 2 \u56de\u5408\u300d"
             "\u5230\u70b9\u540e\u5b83\u5c31\u4e0d\u5728\u4e86 \u2713\uff09\uff1b\u2461 `REMOVE_STATE` \u2713\uff08\u300c\u89e6\u53d1 1 \u6b21\u540e\u300d\u81ea\u89e3 \u2713\uff0c"
             "\u4e14\u8d70**\u4f1a\u516c\u544a `STATE_ENDED`** \u7684\u90a3\u6761\u8def \u2713\uff09\u3002"),
})

if isObject:
    doc["rules"] = rules
else:
    doc = rules
json.dump(doc, io.open(DATA, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   1008.json: the trace is a two-turn state, and both expiries are written")

io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1008\uff1a\u300c\u8fdb\u5165\u6218\u6597\u540e\uff0c\u53d7\u5230\u81f4\u547d\u653b\u51fb\u65f6\u963f\u5170\u4e0d\u4f1a\u9677\u5165\u65e0\u6cd5\u6218\u6597\u72b6\u6001\uff0c\u5e76\u7acb\u5373\u56de\u590d\u81f3\u81ea\u8eab\u751f\u547d\u4e0a\u9650\u7684 25%\u3002
 * \u8be5\u6548\u679c\u5728\u89e6\u53d1 1 \u6b21\u540e\u6216\u6301\u7eed 2 \u56de\u5408\u540e\u81ea\u52a8\u89e3\u9664\u300d (2026-10-02).
 *
 * <p>\u2b50 THREE READINGS, ONE VARIABLE EACH: the eidolon rank, the NUMBER of lethal blows, and the NUMBER of his own turns that
 * elapse before the blow. The trace is a state, so it can be asked about directly -- which is what makes \u300c\u81ea\u52a8\u89e3\u9664\u300d testable.
 */
public class ArlanEidolonFourTest {
    private static final int ARLAN = 1008;
    private static final int MONSTER = 1002011;
    private static final String STATE = "\u7edd\u5904\u53cd\u51fb";

    /** \u2b50 At E4 the first lethal blow restores him to a quarter of his Max HP. */
    @Test
    public void atEidolonFourHeSurvivesAtAQuarter() {
        double[] result = afterLethalBlows(4, 1, 0);
        Assertions.assertTrue(result[0] > 0, "\u300c\u4e0d\u4f1a\u9677\u5165\u65e0\u6cd5\u6218\u6597\u72b6\u6001\u300d");
        Assertions.assertEquals(result[1] * 0.25, result[0], result[1] * 0.01,
                "\u300c\u56de\u590d\u81f3\u81ea\u8eab\u751f\u547d\u4e0a\u9650\u7684 25%\u300d");
    }

    /** \u26a0 Below E4 he falls. */
    @Test
    public void belowEidolonFourHeFalls() {
        Assertions.assertTrue(afterLethalBlows(0, 1, 0)[0] <= 0, "\u661f\u9b42 4 \u624d\u6709\u8fd9\u4e00\u6761");
    }

    /** \u2b50\u2b50 \u300c\u8be5\u6548\u679c\u5728\u89e6\u53d1 1 \u6b21\u540e\u2026\u81ea\u52a8\u89e3\u9664\u300d: the second blow in the same battle is NOT answered. */
    @Test
    public void theFirstBlowConsumesTheTrace() {
        double[] one = afterLethalBlows(4, 1, 0);
        double[] two = afterLethalBlows(4, 2, 0);
        Assertions.assertTrue(one[0] > 0, "\u7b2c\u4e00\u6b21\u88ab\u6551");
        Assertions.assertTrue(two[0] <= 0, "\u7b2c\u4e8c\u6b21**\u4e0d\u518d**\u88ab\u6551\uff08\u300c\u89e6\u53d1 1 \u6b21\u540e\u81ea\u52a8\u89e3\u9664\u300d\uff09");
    }

    /** \u2b50\u2b50 \u300c\u6216\u6301\u7eed 2 \u56de\u5408\u540e\u81ea\u52a8\u89e3\u9664\u300d: after two of HIS turns the trace is gone, even though it never triggered. */
    @Test
    public void twoTurnsEndTheTraceUntriggered() {
        double[] fresh = afterLethalBlows(4, 1, 0);
        double[] late = afterLethalBlows(4, 1, 2);
        Assertions.assertTrue(fresh[0] > 0, "\u6ca1\u8fc7\u56de\u5408\u65f6\u4ecd\u7136\u88ab\u6551");
        Assertions.assertTrue(late[0] <= 0, "\u8fc7\u4e86\u4e24\u4e2a\u56de\u5408\u540e**\u4e0d\u518d**\u88ab\u6551\uff08\u300c\u6301\u7eed 2 \u56de\u5408\u540e\u81ea\u52a8\u89e3\u9664\u300d\uff09");
    }

    // ==================================================================

    /** { his HP after the blows, his Max HP }. {@code turns} of his own turns elapse before the blows. */
    private static double[] afterLethalBlows(int eidolon, int blows, int turns) {
        Character him = CharacterFactory.create(ARLAN, 80, false, null, null, eidolon);
        Battle battle = new Battle(List.of(him),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        if (eidolon >= 4) {
            Assertions.assertTrue(him.getBuffManager().hasState(STATE), "precondition: the trace is on him");
        }

        // \u26a0 A timed buff ticks on ITS WEARER's turns, in TWO halves: `beforeMove()` is the early tick and `afterMove()` the late
        // one (BuffManager.processBuffTick(true/false)), and a state's expiry is announced on the LATE half. Driving only
        // `beforeMove()` is therefore half a turn, which is what the first version of this judge measured by mistake.
        for (int turn = 0; turn < turns; turn++) {
            Signal signal = battle.queue.snapshot().stream()
                    .filter(candidate -> candidate.getCanHit() == him).findFirst()
                    .orElseThrow(() -> new AssertionError("precondition: he is in the queue"));
            battle.currentMove = signal;
            battle.beforeMove();
            battle.afterMove();
            battle.processRequests();
        }

        for (int blow = 0; blow < blows; blow++) {
            if (him.isDeath()) {
                break;
            }
            battle.applyTrueDamage(battle.enemies.getFirst(), him, DamageElement.ICE, him.getCurrentHp() * 2.0);
            battle.processRequests();
        }
        return new double[]{him.isDeath() ? 0 : him.getCurrentHp(), him.getMaxHp()};
    }
}
''')
print("ok   judge rewritten: rank, trigger count, and elapsed turns")
