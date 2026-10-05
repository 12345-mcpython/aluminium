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

⚠ REGISTERED, not shipped, from the same sentence: the 960%-ATK finisher 「由敌方全体均分」. No shipped file spells "split this
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
COUNTER = "额外回合计数"
STATE = "变身"
TURNS = 8

doc = json.load(io.open(DATA, encoding="utf-8"))
rules = doc["rules"] if isinstance(doc, dict) else doc
rules = [r for r in rules if not (isinstance(r, dict) and r.get("id") in (COUNT_RULE, END_RULE))]

# ⚠ The counter rule goes FIRST: both rules ride the same event, and the check `>= 8` must see the eighth increment.
rules.append({
    "on": "COUNTDOWN_TURN",
    "id": COUNT_RULE,
    "when": ["actor == countdown"],
    "do": [{"op": "GAIN_RESOURCE", "resource": COUNTER, "amount": 1}],
    "source": ("1408 白厄（文档，卡厄斯兰那的额外回合）：「卡厄斯兰那拥有 **8** 个"
               "卡厄斯兰那的额外回合」✓"),
    "note": ("⭐ 2026-10-02：⭐ **倒计时**自己没有剩余回合计数器** ✓"
             "（读了 `Countdown` 全文 ✓ 与 `Battle` 的倒计时回合处 ✓）⇒ “钟走完了”**不是引擎时刻** ✗"
             "，必须由**内容自己数** ✓（用一个**资源** ✓ —— 它可在条件里读 ✓）。"),
})
rules.append({
    "on": "COUNTDOWN_TURN",
    "id": END_RULE,
    "when": ["actor == countdown", "self_resource:" + COUNTER + " >= " + str(TURNS)],
    "do": [{"op": "REMOVE_STATE", "buff": STATE, "target": "self"}],
    "source": ("1408 白厄（文档）：「最后 1 个倒计时回合…**结束变身**」✓（同句的"
               "“960% 由敌方全体**均分**”已登记 ✗）"),
    "note": ("⭐ 2026-10-02：用 **`REMOVE_STATE`** ✓ —— 它是**显式移除**那条路 ✓，"
             "⭐ **会公告 `STATE_ENDED`** ✓（第 1619 轮实测 ✓）⇒ 所以目标 ① 的“**1408 变身结束**”读者到这里**真正接上** ✓。"
             "⚠ 本条规则**必须在计数规则之后** ✓（同一事件上按文件顺序 ✓），否则第 8 次读不到 8 ✗。"),
})

resources = doc.get("resources")
if not isinstance(resources, list):
    raise SystemExit("1408.json declares no resources")
resources = [r for r in resources if not (isinstance(r, dict) and r.get("id") == COUNTER)]
resources.append({
    "id": COUNTER,
    "max": TURNS,
    "source": "1408 白厄（文档）：「卡厄斯兰那拥有 **8** 个卡厄斯兰那的额外回合」",
    "note": ("⭐ 2026-10-02：这是**计数器**（不是游戏里的资源 ✓）—— "
             "因为引擎里**没有倒计时剩余回合计数器** ✗，而「最后 1 个倒计时回合」需要数到 8 ✓。"),
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
 * 1408：「最后 1 个倒计时回合…结束变身」 (2026-10-02).
 *
 * <p>⭐ TWO-WAY, and the countdown's turns are driven by the SHIPPED pattern (`CountdownTest`): point `currentMove` at the clock's
 * `Signal` on the action bar and call `beforeMove()`. `stepForward()` does not execute a turn at all -- the tree says so in
 * `ContentWeaknessClausesTest` and this judge does not rely on it.
 */
public class TransformationEndsOnLastCountdownTest {
    private static final int OWNER = 1408;
    private static final int MONSTER = 1002011;
    private static final String STATE = "变身";
    private static final int TURNS = 8;

    /** ⭐ After the eighth countdown turn the transformation is gone. */
    @Test
    public void theEighthCountdownTurnEndsIt() {
        Assertions.assertFalse(transformedAfterCountdownTurns(TURNS),
                "「最后 1 个倒计时回合…结束变身」");
    }

    /** ⚠ One turn earlier the transformation must still be on. */
    @Test
    public void oneTurnEarlierItIsStillOn() {
        Assertions.assertTrue(transformedAfterCountdownTurns(TURNS - 1),
                "「最后 1 个」-- the seventh turn is not the last one");
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
