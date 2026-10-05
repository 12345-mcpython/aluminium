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
MOMENT = "阿哈时刻"
REWARD = "好活当赏"
IDS = ("elation_moment_start", "elation_moment_step", "elation_moment_close", "elation_moment_reward", "elation_moment_reward_rule")

doc = json.load(io.open(DATA, encoding="utf-8"))
rules = [r for r in doc["rules"] if not (isinstance(r, dict) and r.get("id") in IDS)]

rules.append({
    "on": "CAST_SETUP", "id": "elation_moment_start", "when": ["from_category ElationDamage"],
    "do": [{"op": "APPLY_BUFF", "buff": MOMENT, "turns": 1, "target": "self"}],
    "source": ("文档：「阿哈时刻持续至本次最后一个欢榆技施放结束」。"),
    "note": ("⭐ ⭐ 2026-10-02：**挂 1 回合** ✓ —— 实测：`到期` 才会让引擎发 `STATE_ENDED` ✓"
             "（`BuffManager` 的 tick 里 `duration() <= 0` 才公告 ✓）；而**显式 `REMOVE_STATE` 不公告** ✗。"
             "⚠ 时长用文档对同族状态的口径（如【好活当赏】“持续 2 回合” ✓）。"),
})
rules.append({
    "on": "STATE_ENDED", "id": "elation_moment_reward", "when": ["self state_ended " + MOMENT],
    "do": [{"op": "APPLY_BUFF", "buff": REWARD, "turns": 2, "target": "self"}],
    "source": "文档：「阿哈时刻结束时，使参演的角色获得本次计入笑点的【好活当赏】状态，持续2回合。」",
    "note": ("⭐ 本段第一个**真** `STATE_ENDED` 读者 ✓ —— 而且这是**本项目第一次**"
             "用“状态自然到期”去驱动 `STATE_ENDED` ✓（同一形状可复用给其余四个读者 ✓）。"),
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
 * 「阿哈时刻持续至本次最后一个欢榆技施放结束」⇒ 结束时发【好活当赏】 (1513, 2026-10-02).
 *
 * <p>⭐ FILE-DRIVEN: a real elation cast applies the moment (that is what gives the event its category), and the clock is
 * advanced with `BuffManager.tickForeign` -- the same call the turn loop makes -- because the engine's announcement only
 * happens when a state EXPIRES (measured in BuffManager).
 */
public class AhaMomentTest {
    private static final int OWNER = 1513;
    private static final int MONSTER = 1002011;
    private static final String MOMENT = "阿哈时刻";
    private static final String REWARD = "好活当赏";

    /** ⭐ The cast applies it, its expiry announces it, and the reader answers. */
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

        owner.getBuffManager().afterMove();                    // one turn passes: Battle calls this for the actor at turn end
        battle.processRequests();
        Assertions.assertFalse(owner.getBuffManager().hasState(MOMENT),
                "one turn later the moment has expired");
        Assertions.assertTrue(owner.getBuffManager().hasState(REWARD),
                "and the engine's report of that expiry is what the reader answers");
    }
}
''')
print("ok   judge written")
