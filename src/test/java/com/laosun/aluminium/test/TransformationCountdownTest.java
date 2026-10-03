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
