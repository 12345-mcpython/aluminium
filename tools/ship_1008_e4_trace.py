"""1008: 「该效果在触发 1 次后或持续 2 回合后自动解除」 -- the survival trace and both of its expiries (2026-10-02, item 53).

Document, verbatim (`eidolons.json` -> 1008/4):
  「进入战斗后，受到致命攻击时阿兰不会陷入无法战斗状态，并立即回复至自身生命上限的 **#1**。
    **该效果在触发 1 次后或持续 #2 回合后自动解除**。」
PARAM (measured): `[0.25, 2]` -> #1 = **25%**, #2 = **2 turns**.

⭐ THE READING THAT MAKES BOTH HALVES WRITABLE: the sentence talks about 「该效果」 -- the TRACE ITSELF -- not about the heal.
So the trace is a state, and it has exactly two ways to end:
  (a) 触发 1 次后 -- the save removes it as part of its own effects (`REMOVE_STATE`, the explicit-removal route that announces
      `STATE_ENDED` -- measured in round 1619);
  (b) 持续 2 回合后 -- its own two-turn duration runs out, which the engine already does for any timed buff.
Both spellings are shipped: `APPLY_BUFF`/`REMOVE_STATE` are 1408's transformation pair, and `has_state` reads the state.

⚠ Naming: the document calls it only 「该效果」. The state is named after the eidolon itself (绝处反击 / Turn the Tables) and
that choice is written down here rather than left implicit.

⚠ Duration owner: a timed buff ticks on ITS WEARER's turns, so 「持续 2 回合」 is read as two of Arlan's turns. Stated, not assumed.
"""
import io
import json

DATA = "src/main/resources/characters/1008.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/ArlanEidolonFourTest.java"
SAVE = "e4_survives_a_lethal_blow_at_a_quarter"
TRACE = "e4_trace"
STATE = "绝处反击"

doc = json.load(io.open(DATA, encoding="utf-8"))
isObject = isinstance(doc, dict)
rules = doc["rules"] if isObject else doc
rules = [r for r in rules if not (isinstance(r, dict) and r.get("id") in (SAVE, TRACE))]
if len(rules) != 3:
    raise SystemExit("expected the three other shipped 1008 rules, found " + str(len(rules)))

source_trace = ("1008 阿兰 星魂 4 「绝处反击 / Turn the Tables」：「**进入战斗后**，"
                "受到致命攻击时阿兰不会陷入**无法战斗状态**，"
                "并立即**回复至**自身生命上限的 **25%**。"
                "**该效果在触发 1 次后或持续 2 回合后自动解除**」"
                "（`param = [0.25, 2]` ⇒ #1 = 25% ✓，#2 = **2 回合** ✓）")

rules.append({
    "on": "BATTLE_START",
    "id": TRACE,
    "min_eidolon": 4,
    "do": [{"op": "APPLY_BUFF", "buff": STATE, "turns": 2, "target": "self"}],
    "source": source_trace,
    "note": ("⭐ 2026-10-02（第 53 件）：⭐ **把「该效果」建成一个状态** ✓ —— "
             "原句说的是「该**效果**…自动解除」✓，而**不是**那个治疗 ✗"
             "⇒ 所以它是一个**状态**，并且它有**两条**结束路："
             "①「触发 1 次后」（救人的那条规则自己 `REMOVE_STATE` ✓）；"
             "②「持续 2 回合后」（它自己的**时长**到点 ✓）。"
             "⭐ 拼法全是现成的 ✓（`APPLY_BUFF`／`REMOVE_STATE` 就是 1408 变身那对 ✓）"
             "—— **无需引擎改动** ✓。"
             "⚠ 名字：原文只叫「该效果」✗ ⇒ 状态用星魂自己的名字命名 ✓（写在这里 ✓）。"
             "⚠ 时长归属：定时 buff 在**其持有者**的回合边界滑走 ✓ ⇒ 「持续 2 回合」"
             "读作**阿兰自己的 2 个回合** ✓（写出来，不假设 ✓）。"),
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
    "note": ("⭐ 2026-10-02：⭐ **`LETHAL_DAMAGE` 的第五位读者** ✓，而且是**第一位说的是“回复**至**”**"
             "（上限）而不是“回复”（数量）的 ✓。❗ **而在这条路上两者**恰好重合**** ✓："
             "`LETHAL_DAMAGE` 发出时目标**已经在 0 血** ✓ ⇒ 被治疗 25% 后结果**就是 25% 上限** ✓ ⇒ "
             "**不涉及近似** ✓。"
             "⭐ 本条现在写全了同一句里的**两条结束路** ✓：① `has_state` 守卫 ✓（「持续 2 回合」"
             "到点后它就不在了 ✓）；② `REMOVE_STATE` ✓（「触发 1 次后」自解 ✓，"
             "且走**会公告 `STATE_ENDED`** 的那条路 ✓）。"),
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
 * 1008：「进入战斗后，受到致命攻击时阿兰不会陷入无法战斗状态，并立即回复至自身生命上限的 25%。
 * 该效果在触发 1 次后或持续 2 回合后自动解除」 (2026-10-02).
 *
 * <p>⭐ THREE READINGS, ONE VARIABLE EACH: the eidolon rank, the NUMBER of lethal blows, and the NUMBER of his own turns that
 * elapse before the blow. The trace is a state, so it can be asked about directly -- which is what makes 「自动解除」 testable.
 */
public class ArlanEidolonFourTest {
    private static final int ARLAN = 1008;
    private static final int MONSTER = 1002011;
    private static final String STATE = "绝处反击";

    /** ⭐ At E4 the first lethal blow restores him to a quarter of his Max HP. */
    @Test
    public void atEidolonFourHeSurvivesAtAQuarter() {
        double[] result = afterLethalBlows(4, 1, 0);
        Assertions.assertTrue(result[0] > 0, "「不会陷入无法战斗状态」");
        Assertions.assertEquals(result[1] * 0.25, result[0], result[1] * 0.01,
                "「回复至自身生命上限的 25%」");
    }

    /** ⚠ Below E4 he falls. */
    @Test
    public void belowEidolonFourHeFalls() {
        Assertions.assertTrue(afterLethalBlows(0, 1, 0)[0] <= 0, "星魂 4 才有这一条");
    }

    /** ⭐⭐ 「该效果在触发 1 次后…自动解除」: the second blow in the same battle is NOT answered. */
    @Test
    public void theFirstBlowConsumesTheTrace() {
        double[] one = afterLethalBlows(4, 1, 0);
        double[] two = afterLethalBlows(4, 2, 0);
        Assertions.assertTrue(one[0] > 0, "第一次被救");
        Assertions.assertTrue(two[0] <= 0, "第二次**不再**被救（「触发 1 次后自动解除」）");
    }

    /** ⭐⭐ 「或持续 2 回合后自动解除」: after two of HIS turns the trace is gone, even though it never triggered. */
    @Test
    public void twoTurnsEndTheTraceUntriggered() {
        double[] fresh = afterLethalBlows(4, 1, 0);
        double[] late = afterLethalBlows(4, 1, 2);
        Assertions.assertTrue(fresh[0] > 0, "没过回合时仍然被救");
        Assertions.assertTrue(late[0] <= 0, "过了两个回合后**不再**被救（「持续 2 回合后自动解除」）");
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

        // ⚠ A timed buff ticks on ITS WEARER's turns, in TWO halves: `beforeMove()` is the early tick and `afterMove()` the late
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
