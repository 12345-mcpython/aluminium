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
 * 1408 白厄：「**变身为卡厄斯兰那**，变身期间展开境界【时墟铁墓】」 (2026-10-02).
 *
 * <p>⭐ FILE-DRIVEN, and only the half the document states with no number in it: her ultimate puts the transformation STATE
 * on. It is permanent because its end is the last countdown turn (文档 :120) -- an explicit removal, which this arc made
 * announce `STATE_ENDED`, so an end-reader now has something to hang on.
 */
public class TransformationStartsTest {
    private static final int OWNER = 1408;
    private static final int MONSTER = 1002011;
    private static final String STATE = "变身";

    /** ⭐ The ultimate starts the transformation. */
    @Test
    public void theUltimateStartsTheTransformation() {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        Assertions.assertFalse(owner.getBuffManager().hasState(STATE), "precondition: not transformed yet");
        Skill ult = owner.getSkills().get(SkillType.ULTRA);
        Assertions.assertNotNull(ult, "precondition: she has an ultimate");
        SkillExecutor.execute(battle, ult, owner, List.of(owner));
        battle.processRequests();

        Assertions.assertTrue(owner.getBuffManager().hasState(STATE),
                "the transformation is on (got " + owner.getBuffManager().hasState(STATE) + ")");
    }
}
