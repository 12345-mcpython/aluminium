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
COUNTDOWN = "\u5361\u5384\u65af\u5170\u90a3\u7684\u989d\u5916\u56de\u5408"

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
                        "\n\u2b50 2026-10-02\uff1a\u52a0\u4e0a**\u5012\u8ba1\u65f6** \u2713 \u2014\u2014 \u6587\u6863 `:77` \u300c\u62e5\u6709 **8** \u4e2a\u989d\u5916\u56de\u5408\uff0c"
                        "**\u901f\u5ea6\u56fa\u5b9a\u4e3a\u5361\u5384\u65af\u5170\u90a3\u57fa\u7840\u901f\u5ea6\u7684 60%**\u300d\u2713\u3002"
                        "\u5199\u6cd5\u7167\u62c4**\u5728\u6811\u7684\u4e24\u4f8b** \u2713\uff08`1309` \u7684 `speed: 90` \u2713\uff0f`1507` \u7684 `speed: 70` \u2713\uff09\uff1b"
                        "**\u6570\u662f\u91cf\u51fa\u6765\u7684** \u2713\uff1a\u5979\u9762\u677f **SPEED = 99.0** \u2713\uff08\u4e00\u6b21\u6027\u63a2\u9488\u5224\u636e\u62a5\u7684 \u2713\uff09\u21d2 60% = **59.4** \u2713\u3002"
                        "\u26a0 \u4ecd\u7f3a\uff08\u5df2\u767b\u8bb0\uff09\uff1a**\u7ed3\u675f\u65f6\u7684\u6ea2\u51fa\u5956\u52b1** \u2717\u3002")
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
 * 1408\uff1a\u300c\u62e5\u6709 8 \u4e2a\u5361\u5384\u65af\u5170\u90a3\u7684\u989d\u5916\u56de\u5408\uff0c\u901f\u5ea6\u56fa\u5b9a\u4e3a\u57fa\u7840\u901f\u5ea6\u7684 60%\u300d (2026-10-02).
 *
 * <p>\u2b50 FILE-DRIVEN: her ultimate starts the transformation, and the countdown that spends the eight extra turns rides the
 * same rule -- so the two are born together.
 */
public class TransformationCountdownTest {
    private static final int OWNER = 1408;
    private static final int MONSTER = 1002011;
    private static final String COUNTDOWN = "\u5361\u5384\u65af\u5170\u90a3\u7684\u989d\u5916\u56de\u5408";

    /** \u2b50 The ultimate starts the countdown as well as the transformation. */
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
