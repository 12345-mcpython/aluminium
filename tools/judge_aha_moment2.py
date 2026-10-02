"""The Aha Moment judge, with the two names that were missing (2026-10-02).

Measured since the last attempt:
  * `models/skill/Skill` is abstract; its concrete subclass in that package is `DefaultSkill`;
  * `BuffManager.hasState(String state)` (:721) is how a judge asks "is this state on?".

So the judge drives her OWN skill (`owner.getSkills()`) through `SkillExecutor.execute(...)`, which is what gives the cast
its elation category (`from_category ElationDamage`), and reads states through `getBuffManager().hasState(...)`.

Four readings along the documented path: the moment is off, the elation cast puts it on, one finished elation attack
decrements the pending counter to zero, and the removal makes the engine report STATE_ENDED -- which this file's reader
answers with 【好活当赏】.
ASCII only.
"""
import io

io.open("src/test/java/com/laosun/aluminium/test/AhaMomentTest.java",
        "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
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
 * \u300c\u963f\u54c8\u65f6\u523b\u6301\u7eed\u81f3\u672c\u6b21\u6700\u540e\u4e00\u4e2a\u6b22\u6986\u6280\u65bd\u653e\u7ed3\u675f\u300d\u21d2 \u63d0\u524d\u7ed3\u675f\u65f6\u53d1\u3010\u597d\u6d3b\u5f53\u8d4f\u3011 (1513, 2026-10-02).
 *
 * <p>\u2b50 FILE-DRIVEN. The moment goes on with her elation cast, the pending counter is decremented when an elation attack
 * finishes, and at zero the state is removed -- which is the engine's STATE_ENDED, answered here by the reward rule.
 */
public class AhaMomentTest {
    private static final int OWNER = 1513;
    private static final int MONSTER = 1002011;
    private static final String MOMENT = "\u963f\u54c8\u65f6\u523b";
    private static final String REWARD = "\u597d\u6d3b\u5f53\u8d4f";

    /** \u2b50 On with the cast, and the reward lands when the counter closes the moment. */
    @Test
    public void theMomentEndsAndTheRewardLands() {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        Assertions.assertFalse(owner.getBuffManager().hasState(MOMENT),
                "precondition: the moment is not on before any cast");

        Skill elation = owner.getSkills().get(SkillType.ELATION_SKILL);
        Assertions.assertNotNull(elation, "precondition: she has an elation skill in her kit");
        SkillExecutor.execute(battle, elation, owner, List.of(battle.enemies.get(0)));
        battle.processRequests();

        Assertions.assertTrue(owner.getBuffManager().hasState(MOMENT),
                "the elation cast must put the moment on her");

        // one finished elation attack is what the document counts: "until the last elation skill has finished"
        battle.fireTriggers(TriggerEvent.ATTACK_FINISHED, owner, owner, 0, 0);
        battle.processRequests();

        Assertions.assertFalse(owner.getBuffManager().hasState(MOMENT),
                "at zero the state must be gone");
        Assertions.assertTrue(owner.getBuffManager().hasState(REWARD),
                "and the reader answers the engine's report: the reward lands");
    }
}
''')
print("ok   judge written with hasState and ELATION_SKILL")
