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
 * 1408：「【火种】达到 12 点时可激活终结技，**达到上限后还可最多溢出 3 点**」 (2026-10-02).
 *
 * <p>⭐ BOTH ENDS ARE PINNED: a value of 14 proves the pool really goes past its max of 12, and a ceiling of 15 proves the
 * overflow allowance is exactly three (「最多溢出 3 点」). Her skill grants two points per cast, so the casts are the dial.
 */
public class CoreflameOverflowTest {
    private static final int OWNER = 1408;
    private static final int MONSTER = 1002011;
    private static final String COREFLAME = "火种";

    /** ⭐ Past the maximum: seven casts reach fourteen, which a cap of twelve could never allow. */
    @Test
    public void thePoolGoesPastItsMaximum() {
        Assertions.assertEquals(14.0, afterCasts(7), 1e-9,
                "「达到上限后还可溢出」-- 14 is above the declared max of 12");
    }

    /** ⭐ The allowance is exactly three: ten casts stop at fifteen. */
    @Test
    public void theAllowanceIsExactlyThree() {
        Assertions.assertEquals(15.0, afterCasts(10), 1e-9,
                "「最多溢出 3 点」-- 12 + 3 is the ceiling");
    }

    private static double afterCasts(int casts) {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        Skill skill = owner.getSkills().get(SkillType.SKILL);
        Assertions.assertNotNull(skill, "precondition: she has a skill");
        for (int i = 0; i < casts; i++) {
            // ⚠ AIMED AT AN ENEMY. The first version of this judge aimed her skill at HERSELF, and that single choice made the
            // resource read a constant 3 for every cast count: the first self-aimed cast landed (+2 for the skill, +1 for item 38's
            // "being targeted" rule) and the later ones did nothing at all. Measured step by step, aimed at an enemy, the pool is
            // 2, 4, 6, 8, 10, 12 -- exactly two per cast, up to its declared cap.
            SkillExecutor.execute(battle, skill, owner, List.of(battle.enemies.get(0)));
            battle.processRequests();
        }
        // ⚠ `owner.getResources().value(...)`, and NOT `battle.partyResource(...)`: Coreflame is not a party resource, so the
        // party view is null. The reader itself is innocent -- measured against 1412's Charge in the same probe, which accumulates
        // exactly (+1 per cast).
        return owner.getResources().value(COREFLAME);
    }
}
