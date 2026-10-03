"""1408: the transformation ends on the LAST countdown turn (2026-10-02, item 40).

Document: 「…最后的 卡厄斯兰那的额外回合 开始时立即发动最后一击，造成等同于卡厄斯兰那 960% 攻击力的物理属性终结技伤害，由敌方全体均分。」
-- and the countdown's own clause, quoted when the countdown was built (item 23): 「最后 1 个倒计时回合…结束变身」.

WHY THIS HAD TO WAIT, and what unblocked it (all read, not inferred):
  * `Countdown` has NO remaining-turn counter (its whole body is an owner link plus a stat sheet), and `Battle`'s countdown-turn
    site does exactly two things -- `fireTriggersForAlly(COUNTDOWN_TURN, countdown, countdown, 0)` and `actor.beforeMove(this)`.
    So "the clock ran out" is NOT an engine moment: it has to be COUNTED by content.
  * the shipped idiom for a countdown's turn is `on: COUNTDOWN_TURN` + `when: ["actor == countdown"]` (1507's
    `zone_ends_takes_thousand_forgings_off`, which drops a state the same way);
  * the count rides a RESOURCE, because a resource is readable in a condition (`self_resource:X >= 8`) -- the affordance measured
    earlier in this span;
  * and the ending itself is `REMOVE_STATE`, the EXPLICIT path -- which announces `STATE_ENDED` (measured in round 1619: a spent
    duration announces too, but this clause is a removal, so it is the road already proven).

\u26a0 REGISTERED, not shipped, from the same sentence: the 960%-ATK finisher 「由敌方全体均分」. No shipped file spells "split this
damage evenly among all enemies" (searched the whole tree for 均分: only document text matches), so writing `DAMAGE` with
`target: all_enemies` would give EVERY enemy the full 960% -- an approximation this span does not allow.
ASCII only.
"""
import io
import json

DATA = "src/main/resources/characters/1408.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/TransformationEndsOnLastCountdownTest.java"
COUNT_RULE = "countdown_turn_counts"
END_RULE = "last_countdown_turn_ends_the_transformation"
COUNTER = "\u989d\u5916\u56de\u5408\u8ba1\u6570"
STATE = "\u53d8\u8eab"
TURNS = 8

doc = json.load(io.open(DATA, encoding="utf-8"))
rules = doc["rules"] if isinstance(doc, dict) else doc
rules = [r for r in rules if not (isinstance(r, dict) and r.get("id") in (COUNT_RULE, END_RULE))]

# \u26a0 The counter rule goes FIRST: both rules ride the same event, and the check `>= 8` must see the eighth increment.
rules.append({
    "on": "COUNTDOWN_TURN",
    "id": COUNT_RULE,
    "when": ["actor == countdown"],
    "do": [{"op": "GAIN_RESOURCE", "resource": COUNTER, "amount": 1}],
    "source": ("1408 \u767d\u5384\uff08\u6587\u6863\uff0c\u5361\u5384\u65af\u5170\u90a3\u7684\u989d\u5916\u56de\u5408\uff09\uff1a\u300c\u5361\u5384\u65af\u5170\u90a3\u62e5\u6709 **8** \u4e2a"
               "\u5361\u5384\u65af\u5170\u90a3\u7684\u989d\u5916\u56de\u5408\u300d\u2713"),
    "note": ("\u2b50 2026-10-02\uff1a\u2b50 **\u5012\u8ba1\u65f6**\u81ea\u5df1\u6ca1\u6709\u5269\u4f59\u56de\u5408\u8ba1\u6570\u5668** \u2713"
             "\uff08\u8bfb\u4e86 `Countdown` \u5168\u6587 \u2713 \u4e0e `Battle` \u7684\u5012\u8ba1\u65f6\u56de\u5408\u5904 \u2713\uff09\u21d2 \u201c\u949f\u8d70\u5b8c\u4e86\u201d**\u4e0d\u662f\u5f15\u64ce\u65f6\u523b** \u2717"
             "\uff0c\u5fc5\u987b\u7531**\u5185\u5bb9\u81ea\u5df1\u6570** \u2713\uff08\u7528\u4e00\u4e2a**\u8d44\u6e90** \u2713 \u2014\u2014 \u5b83\u53ef\u5728\u6761\u4ef6\u91cc\u8bfb \u2713\uff09\u3002"),
})
rules.append({
    "on": "COUNTDOWN_TURN",
    "id": END_RULE,
    "when": ["actor == countdown", "self_resource:" + COUNTER + " >= " + str(TURNS)],
    "do": [{"op": "REMOVE_STATE", "buff": STATE, "target": "self"}],
    "source": ("1408 \u767d\u5384\uff08\u6587\u6863\uff09\uff1a\u300c\u6700\u540e 1 \u4e2a\u5012\u8ba1\u65f6\u56de\u5408\u2026**\u7ed3\u675f\u53d8\u8eab**\u300d\u2713\uff08\u540c\u53e5\u7684"
               "\u201c960% \u7531\u654c\u65b9\u5168\u4f53**\u5747\u5206**\u201d\u5df2\u767b\u8bb0 \u2717\uff09"),
    "note": ("\u2b50 2026-10-02\uff1a\u7528 **`REMOVE_STATE`** \u2713 \u2014\u2014 \u5b83\u662f**\u663e\u5f0f\u79fb\u9664**\u90a3\u6761\u8def \u2713\uff0c"
             "\u2b50 **\u4f1a\u516c\u544a `STATE_ENDED`** \u2713\uff08\u7b2c 1619 \u8f6e\u5b9e\u6d4b \u2713\uff09\u21d2 \u6240\u4ee5\u76ee\u6807 \u2460 \u7684\u201c**1408 \u53d8\u8eab\u7ed3\u675f**\u201d\u8bfb\u8005\u5230\u8fd9\u91cc**\u771f\u6b63\u63a5\u4e0a** \u2713\u3002"
             "\u26a0 \u672c\u6761\u89c4\u5219**\u5fc5\u987b\u5728\u8ba1\u6570\u89c4\u5219\u4e4b\u540e** \u2713\uff08\u540c\u4e00\u4e8b\u4ef6\u4e0a\u6309\u6587\u4ef6\u987a\u5e8f \u2713\uff09\uff0c\u5426\u5219\u7b2c 8 \u6b21\u8bfb\u4e0d\u5230 8 \u2717\u3002"),
})

resources = doc.get("resources")
if not isinstance(resources, list):
    raise SystemExit("1408.json declares no resources")
resources = [r for r in resources if not (isinstance(r, dict) and r.get("id") == COUNTER)]
resources.append({
    "id": COUNTER,
    "max": TURNS,
    "source": "1408 \u767d\u5384\uff08\u6587\u6863\uff09\uff1a\u300c\u5361\u5384\u65af\u5170\u90a3\u62e5\u6709 **8** \u4e2a\u5361\u5384\u65af\u5170\u90a3\u7684\u989d\u5916\u56de\u5408\u300d",
    "note": ("\u2b50 2026-10-02\uff1a\u8fd9\u662f**\u8ba1\u6570\u5668**\uff08\u4e0d\u662f\u6e38\u620f\u91cc\u7684\u8d44\u6e90 \u2713\uff09\u2014\u2014 "
             "\u56e0\u4e3a\u5f15\u64ce\u91cc**\u6ca1\u6709\u5012\u8ba1\u65f6\u5269\u4f59\u56de\u5408\u8ba1\u6570\u5668** \u2717\uff0c\u800c\u300c\u6700\u540e 1 \u4e2a\u5012\u8ba1\u65f6\u56de\u5408\u300d\u9700\u8981\u6570\u5230 8 \u2713\u3002"),
})
if not isinstance(doc, dict):
    raise SystemExit("1408.json must be an object")
doc["rules"] = rules
doc["resources"] = resources
json.dump(doc, io.open(DATA, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   1408.json: the countdown counts, and its last turn ends the transformation")

io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Countdown;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.Skill;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1408\uff1a\u300c\u6700\u540e 1 \u4e2a\u5012\u8ba1\u65f6\u56de\u5408\u2026\u7ed3\u675f\u53d8\u8eab\u300d (2026-10-02).
 *
 * <p>\u2b50 TWO-WAY, and the countdown's turns are driven by the SHIPPED pattern (`CountdownTest`): point `currentMove` at the clock's
 * `Signal` on the action bar and call `beforeMove()`. `stepForward()` does not execute a turn at all -- the tree says so in
 * `ContentWeaknessClausesTest` and this judge does not rely on it.
 */
public class TransformationEndsOnLastCountdownTest {
    private static final int OWNER = 1408;
    private static final int MONSTER = 1002011;
    private static final String STATE = "\u53d8\u8eab";
    private static final int TURNS = 8;

    /** \u2b50 After the eighth countdown turn the transformation is gone. */
    @Test
    public void theEighthCountdownTurnEndsIt() {
        Assertions.assertFalse(transformedAfterCountdownTurns(TURNS),
                "\u300c\u6700\u540e 1 \u4e2a\u5012\u8ba1\u65f6\u56de\u5408\u2026\u7ed3\u675f\u53d8\u8eab\u300d");
    }

    /** \u26a0 One turn earlier the transformation must still be on. */
    @Test
    public void oneTurnEarlierItIsStillOn() {
        Assertions.assertTrue(transformedAfterCountdownTurns(TURNS - 1),
                "\u300c\u6700\u540e 1 \u4e2a\u300d-- the seventh turn is not the last one");
    }

    // ==================================================================

    private static boolean transformedAfterCountdownTurns(int turns) {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        Skill ult = owner.getSkills().get(SkillType.ULTRA);
        Assertions.assertNotNull(ult, "precondition: she has an ultimate");
        SkillExecutor.execute(battle, ult, owner, List.of(owner));
        battle.processRequests();
        Assertions.assertTrue(owner.getBuffManager().hasState(STATE), "precondition: the transformation is on");
        Assertions.assertEquals(1, battle.countdownUnits().size(), "precondition: her clock exists");

        for (int turn = 0; turn < turns; turn++) {
            Countdown clock = battle.countdownUnits().isEmpty() ? null : battle.countdownUnits().getFirst();
            if (clock == null) {
                break;
            }
            battle.queue.snapshot().stream()
                    .filter(signal -> signal.getCanHit() == clock)
                    .findFirst()
                    .ifPresent(signal -> battle.currentMove = signal);
            battle.beforeMove();
            battle.processRequests();
        }
        return owner.getBuffManager().hasState(STATE);
    }
}
''')
print("ok   judge written")
