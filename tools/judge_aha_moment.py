"""Judge the Aha Moment: the state goes on, and the engine reports its end (2026-10-02).

Content already in the tree (1513.json): the moment is applied on an elation cast, the pending counter is incremented,
decremented on each finished elation attack, and at zero the state is REMOVEd -- which makes the engine report
STATE_ENDED("阿哈时刻"), which this file's reader answers by granting 【好活当赏】.

The judge drives that path itself: it builds an elation Skill (`attack_type` "ElationDamage", which is the data string
`SkillCategory.ELATION_DAMAGE` carries) and executes it, so `from_category ElationDamage` can be satisfied at all.
Readings: the moment's state after the cast, then the reward's state after the counter reaches zero.
ASCII only.
"""
import io

io.open("src/test/java/com/laosun/aluminium/test/AhaMomentTest.java",
        "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.Skill;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * \u300c\u963f\u54c8\u65f6\u523b\u6301\u7eed\u81f3\u672c\u6b21\u6700\u540e\u4e00\u4e2a\u6b22\u6986\u6280\u65bd\u653e\u7ed3\u675f\u300d\u21d2 \u7ed3\u675f\u65f6\u53d1\u3010\u597d\u6d3b\u5f53\u8d4f\u3011 (1513, 2026-10-02).
 *
 * <p>\u2b50 FILE-DRIVEN, and the whole four-step path is exercised: an elation cast applies the moment, its end decrements the
 * pending counter, the counter reaching zero removes the state, and the engine's own report of that removal is what the
 * reward rule answers.
 */
public class AhaMomentTest {
    private static final int OWNER = 1513;
    private static final int MONSTER = 1002011;

    /** \u2b50 The moment goes on with the cast, and the reward lands when it ends. */
    @Test
    public void theMomentEndsAndTheRewardLands() {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        Assertions.assertFalse(owner.getState().contains("\u963f\u54c8\u65f6\u523b"),
                "precondition: the moment is not on before any cast");

        Skill elation = new Skill("ElationDamage", 1, null, List.of(), null, 0, null,
                new Skill.StanceList(30, 0, 0), DamageElement.IMAGINARY, 0.0, 0.0);
        SkillExecutor.execute(battle, elation, owner, List.of(battle.enemies.get(0)));
        battle.processRequests();

        Assertions.assertTrue(owner.getState().contains("\u963f\u54c8\u65f6\u523b"),
                "the cast must put the moment on her (" + owner.getState() + ")");

        // one finished elation attack is what the document counts as "the moment's last skill"
        battle.fireTriggers(TriggerEvent.ATTACK_FINISHED, owner, owner, 0, 0);
        battle.processRequests();

        Assertions.assertFalse(owner.getState().contains("\u963f\u54c8\u65f6\u523b"),
                "and at zero the state must be gone (" + owner.getState() + ")");
        Assertions.assertTrue(owner.getState().contains("\u597d\u6d3b\u5f53\u8d4f"),
                "which is what the reader answers: the reward lands (" + owner.getState() + ")");
    }
}
''')
print("ok   judge written")
