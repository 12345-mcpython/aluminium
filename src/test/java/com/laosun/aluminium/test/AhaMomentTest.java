package com.laosun.aluminium.test;

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
 * "阿哈时刻持续至本次最后一个欢榆技施放结束" so 结束时发[好活当赏] (1513, 2026-10-02).
 *
 * <p>FILE-DRIVEN: a real elation cast applies the moment (that is what gives the event its category), and the clock is
 * advanced with `BuffManager.tickForeign` -- the same call the turn loop makes -- because the engine's announcement only
 * happens when a state EXPIRES (measured in BuffManager).
 */
public class AhaMomentTest {
    private static final int OWNER = 1513;
    private static final int MONSTER = 1002011;
    private static final String MOMENT = "阿哈时刻";
    private static final String REWARD = "好活当赏";

    /** The cast applies it, its expiry announces it, and the reader answers. */
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
