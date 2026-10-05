"""1408's transformation runs on a countdown (2026-10-02).

Document, verbatim (1408_白厄.html):
  * :77 「变身为卡厄斯兰那…卡厄斯兰那不会进入自己的回合，但拥有 **8** 个卡厄斯兰那的额外回合，**速度固定为卡厄斯兰那基础速度的 60%**。」
  * :120 「卡厄斯兰那的额外回合：…倒计时回合开始时卡厄斯兰那获得 1 个额外回合，最后 1 个倒计时回合开始时改为发动最后一击并结束变身。」

Shape, copied from two in-tree precedents (never guessed):
    1309.json  { "op": "START_COUNTDOWN", "buff": "协奏倒计时", "speed": 90 }
    1507.json  { "op": "START_COUNTDOWN", "buff": "结界倒计时", "speed": 70 }
So the keys are `buff` (the countdown's name) and `speed` (an absolute number). The number is measured, not assumed: her
panel reads SPEED = 99.0 (a one-off probe test reported it), and 60% of that is 59.4.

The countdown rides the transformation rule that already shipped (item 21), so the transformation state and the countdown
are born together -- and the explicit removal that ends the transformation is what now announces STATE_ENDED (item 20).

Registered, not guessed: the overflow counter the end reward needs, and the 【毁伤】 ceiling.
ASCII only.
"""
import io
import json

DATA = "src/main/resources/characters/1408.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/TransformationCountdownTest.java"
RULE = "ult_transformation"
COUNTDOWN = "卡厄斯兰那的额外回合"

doc = json.load(io.open(DATA, encoding="utf-8"))
rules = doc["rules"] if isinstance(doc, dict) else doc

patched = 0
for rule in rules:
    if isinstance(rule, dict) and rule.get("id") == RULE:
        effects = [e for e in (rule.get("do") or [])
                   if not (isinstance(e, dict) and e.get("op") == "START_COUNTDOWN")]
        effects.append({"op": "START_COUNTDOWN", "buff": COUNTDOWN, "speed": 59.4})
        rule["do"] = effects
        rule["note"] = ((rule.get("note") or "") +
                        "\n⭐ 2026-10-02：加上**倒计时** ✓ —— 文档 `:77` 「拥有 **8** 个额外回合，"
                        "**速度固定为卡厄斯兰那基础速度的 60%**」✓。"
                        "写法照拄**在树的两例** ✓（`1309` 的 `speed: 90` ✓／`1507` 的 `speed: 70` ✓）；"
                        "**数是量出来的** ✓：她面板 **SPEED = 99.0** ✓（一次性探针判据报的 ✓）⇒ 60% = **59.4** ✓。"
                        "⚠ 仍缺（已登记）：**结束时的溢出奖励** ✗。")
        patched += 1
if patched != 1:
    raise SystemExit("expected exactly one transformation rule, patched " + str(patched))

if not isinstance(doc, dict):
    raise SystemExit("1408.json must be an object")
doc["rules"] = rules
json.dump(doc, io.open(DATA, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   1408.json: the transformation starts its countdown")

io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
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
 * 1408：「拥有 8 个卡厄斯兰那的额外回合，速度固定为基础速度的 60%」 (2026-10-02).
 *
 * <p>⭐ FILE-DRIVEN: her ultimate starts the transformation, and the countdown that spends the eight extra turns rides the
 * same rule -- so the two are born together.
 */
public class TransformationCountdownTest {
    private static final int OWNER = 1408;
    private static final int MONSTER = 1002011;
    private static final String COUNTDOWN = "卡厄斯兰那的额外回合";

    /** ⭐ The ultimate starts the countdown as well as the transformation. */
    @Test
    public void theUltimateStartsTheCountdown() {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        Assertions.assertFalse(owner.getBuffManager().hasState(COUNTDOWN), "precondition: no countdown yet");
        Skill ult = owner.getSkills().get(SkillType.ULTRA);
        Assertions.assertNotNull(ult, "precondition: she has an ultimate");
        SkillExecutor.execute(battle, ult, owner, List.of(owner));
        battle.processRequests();

        Assertions.assertTrue(battle.countdownUnits().stream()
                        .anyMatch(c -> COUNTDOWN.equals(c.getName())),
                "the countdown for the eight extra turns is running");
    }
}
''')
print("ok   judge written")
