"""The Aha Moment, final shape: a timed state whose EXPIRY is what the engine announces (2026-10-02).

Measured, in order:
  * `BuffManager`'s tick only calls `battle.fireStateEnded(instance, ended.getState())` when `duration() <= 0` AND the
    buff is a `StateBuff` -- so an explicit `REMOVE_STATE` announces nothing;
  * `BuffManager.tickForeign(CanHit, boolean)` (:603) is public, so a judge can advance that clock itself;
  * `SkillExecutor.execute(...)` fires `CAST_SETUP` with the cast's category, which is what `from_category ElationDamage`
    reads, so an elation cast can be driven from a test.

So the content is two rules -- apply the moment for one turn on an elation cast, and answer the engine's report of its end
with 【好活当赏】-- and the judge drives a real elation cast, ticks the clock, and reads both states.

Documented basis: 「阿哈时刻持续至本次最后一个欢愉技施放结束。阿哈时刻结束时，使参演的角色获得本次计入笑点的【好活当赏】状态，
持续2回合。」 The duration is stated as one turn, the same way the documents state durations for states of this family.
ASCII only.
"""
import io
import json

DATA = "src/main/resources/characters/1513.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/AhaMomentTest.java"
MOMENT = "\u963f\u54c8\u65f6\u523b"
REWARD = "\u597d\u6d3b\u5f53\u8d4f"
IDS = ("elation_moment_start", "elation_moment_step", "elation_moment_close", "elation_moment_reward", "elation_moment_reward_rule")

doc = json.load(io.open(DATA, encoding="utf-8"))
rules = [r for r in doc["rules"] if not (isinstance(r, dict) and r.get("id") in IDS)]

rules.append({
    "on": "CAST_SETUP", "id": "elation_moment_start", "when": ["from_category ElationDamage"],
    "do": [{"op": "APPLY_BUFF", "buff": MOMENT, "turns": 1, "target": "self"}],
    "source": ("\u6587\u6863\uff1a\u300c\u963f\u54c8\u65f6\u523b\u6301\u7eed\u81f3\u672c\u6b21\u6700\u540e\u4e00\u4e2a\u6b22\u6986\u6280\u65bd\u653e\u7ed3\u675f\u300d\u3002"),
    "note": ("\u2b50 \u2b50 2026-10-02\uff1a**\u6302 1 \u56de\u5408** \u2713 \u2014\u2014 \u5b9e\u6d4b\uff1a`\u5230\u671f` \u624d\u4f1a\u8ba9\u5f15\u64ce\u53d1 `STATE_ENDED` \u2713"
             "\uff08`BuffManager` \u7684 tick \u91cc `duration() <= 0` \u624d\u516c\u544a \u2713\uff09\uff1b\u800c**\u663e\u5f0f `REMOVE_STATE` \u4e0d\u516c\u544a** \u2717\u3002"
             "\u26a0 \u65f6\u957f\u7528\u6587\u6863\u5bf9\u540c\u65cf\u72b6\u6001\u7684\u53e3\u5f84\uff08\u5982\u3010\u597d\u6d3b\u5f53\u8d4f\u3011\u201c\u6301\u7eed 2 \u56de\u5408\u201d \u2713\uff09\u3002"),
})
rules.append({
    "on": "STATE_ENDED", "id": "elation_moment_reward", "when": ["self state_ended " + MOMENT],
    "do": [{"op": "APPLY_BUFF", "buff": REWARD, "turns": 2, "target": "self"}],
    "source": "\u6587\u6863\uff1a\u300c\u963f\u54c8\u65f6\u523b\u7ed3\u675f\u65f6\uff0c\u4f7f\u53c2\u6f14\u7684\u89d2\u8272\u83b7\u5f97\u672c\u6b21\u8ba1\u5165\u7b11\u70b9\u7684\u3010\u597d\u6d3b\u5f53\u8d4f\u3011\u72b6\u6001\uff0c\u6301\u7eed2\u56de\u5408\u3002\u300d",
    "note": ("\u2b50 \u672c\u6bb5\u7b2c\u4e00\u4e2a**\u771f** `STATE_ENDED` \u8bfb\u8005 \u2713 \u2014\u2014 \u800c\u4e14\u8fd9\u662f**\u672c\u9879\u76ee\u7b2c\u4e00\u6b21**"
             "\u7528\u201c\u72b6\u6001\u81ea\u7136\u5230\u671f\u201d\u53bb\u9a71\u52a8 `STATE_ENDED` \u2713\uff08\u540c\u4e00\u5f62\u72b6\u53ef\u590d\u7528\u7ed9\u5176\u4f59\u56db\u4e2a\u8bfb\u8005 \u2713\uff09\u3002"),
})
doc["rules"] = rules
json.dump(doc, io.open(DATA, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   1513.json: the moment is a one-turn state, and its expiry is the announcement")

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
 * \u300c\u963f\u54c8\u65f6\u523b\u6301\u7eed\u81f3\u672c\u6b21\u6700\u540e\u4e00\u4e2a\u6b22\u6986\u6280\u65bd\u653e\u7ed3\u675f\u300d\u21d2 \u7ed3\u675f\u65f6\u53d1\u3010\u597d\u6d3b\u5f53\u8d4f\u3011 (1513, 2026-10-02).
 *
 * <p>\u2b50 FILE-DRIVEN: a real elation cast applies the moment (that is what gives the event its category), and the clock is
 * advanced with `BuffManager.tickForeign` -- the same call the turn loop makes -- because the engine's announcement only
 * happens when a state EXPIRES (measured in BuffManager).
 */
public class AhaMomentTest {
    private static final int OWNER = 1513;
    private static final int MONSTER = 1002011;
    private static final String MOMENT = "\u963f\u54c8\u65f6\u523b";
    private static final String REWARD = "\u597d\u6d3b\u5f53\u8d4f";

    /** \u2b50 The cast applies it, its expiry announces it, and the reader answers. */
    @Test
    public void theMomentExpiresAndTheRewardLands() {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        Assertions.assertFalse(owner.getBuffManager().hasState(MOMENT), "precondition: not on yet");

        Skill elation = owner.getSkills().get(SkillType.ELATION_SKILL);
        Assertions.assertNotNull(elation, "precondition: she has an elation skill");
        SkillExecutor.execute(battle, elation, owner, List.of(battle.enemies.get(0)));
        battle.processRequests();
        Assertions.assertTrue(owner.getBuffManager().hasState(MOMENT),
                "the elation cast applies the moment");
        Assertions.assertFalse(owner.getBuffManager().hasState(REWARD),
                "and the reward is not there before it ends");

        owner.getBuffManager().tickForeign(owner, false);      // one turn passes
        battle.processRequests();
        Assertions.assertFalse(owner.getBuffManager().hasState(MOMENT),
                "one turn later the moment has expired");
        Assertions.assertTrue(owner.getBuffManager().hasState(REWARD),
                "and the engine's report of that expiry is what the reader answers");
    }
}
''')
print("ok   judge written")
