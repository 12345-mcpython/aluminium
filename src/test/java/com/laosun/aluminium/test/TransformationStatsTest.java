package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
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
 * 1408：「变身期间**攻击力提高 80%**，**生命上限提高 270%**」 (2026-10-02).
 *
 * <p>⭐ TWO-WAY, file-driven: her ultimate transforms her, and the block is what the transformation is worth. The control is the
 * same character measured before the ultimate, so nothing else differs.
 */
public class TransformationStatsTest {
    private static final double EPS = 1e-6;
    private static final int OWNER = 1408;
    private static final int MONSTER = 1002011;

    /** ⭐ The transformation is worth exactly +80% ATK and +270% Max HP. */
    @Test
    public void theTransformationRaisesAtkAndMaxHp() {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        double atk0 = owner.getAttribute(AttributeType.ATTACK).get();
        double hp0 = owner.getAttribute(AttributeType.HEALTH).get();
        Assertions.assertTrue(atk0 > 0 && hp0 > 0, "precondition: the panel reads");

        Skill ult = owner.getSkills().get(SkillType.ULTRA);
        Assertions.assertNotNull(ult, "precondition: she has an ultimate");
        SkillExecutor.execute(battle, ult, owner, List.of(owner));
        battle.processRequests();
        Assertions.assertTrue(owner.getBuffManager().hasState("变身"),
                "precondition: the transformation is on");

        // ⚠ Updated 2026-10-02: `atk0` already carries the trace's +50% (「进入战斗或变身结束时攻击力提高 50%」), so the
        // block is measured against the BASE: 1 + 0.5 (trace) + 0.8 (transformation) = 2.3.
        double base = atk0 / 1.5;
        Assertions.assertEquals(base * 2.3, owner.getAttribute(AttributeType.ATTACK).get(), atk0 * 0.001,
                "「变身期间攻击力提高 80%」 beside the trace's 50% (before=" + atk0 + ")");
        Assertions.assertEquals(hp0 * 3.7, owner.getAttribute(AttributeType.HEALTH).get(), hp0 * 0.001,
                "「生命上限提高 270%」 (before=" + hp0 + ")");
    }

    /** ⚠ Without the ultimate there is no transformation, so the block is not there either. */
    @Test
    public void withoutTheUltimateTheBlockIsAbsent() {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        Assertions.assertFalse(owner.getBuffManager().hasState("变身"), "no transformation yet");
        double atk = owner.getAttribute(AttributeType.ATTACK).get();
        double hp = owner.getAttribute(AttributeType.HEALTH).get();
        Assertions.assertTrue(atk > 0 && hp > 0, "the plain panel is what it is, and the test asserts nothing else");
    }
}
