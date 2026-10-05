package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.DefaultSkill;
import com.laosun.aluminium.models.skill.Skill;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * END-TO-END judge for the capability behind cone 23048 clause 3 (2026-09-30): it really walks SkillExecutor.
 *
 * <p>Measured facts it rests on: `DefaultSkill(id, slot, level)` derives the category from the SLOT (slot 2 = BPSKILL,
 * probed), and SkillExecutor takes its targets from the CALLER's list, so passing the ally makes the engine hand that
 * ally to CAST_SETUP. Nothing here calls fireTriggers directly -- that is exactly what made the earlier rule-level
 * judge blind to the engine line, and the mutation below is what proves this one is not.
 */
public class EpochEtchedInGoldenBloodEndToEndTest {
    private static final int LEVEL = 80;

    @Test
    public void castingASkillOnAnAllyBuffsThatAllyThroughTheEngine() {
        Character wearer = CharacterFactory.create(1210, LEVEL, true, Weapon.build(23048, LEVEL, false, 1));
        Character ally = CharacterFactory.create(1001, LEVEL);
        Battle battle = new Battle(List.of(wearer, ally), List.of(EnemyFactory.create(1002011, 90, 1)),
                new Random(0));
        battle.startBattle();

        Assertions.assertEquals(0.0, ally.getAttribute(AttributeType.SKILL_DAMAGE_BOOST).get(), 1e-9,
                "nothing yet");
        // Slot 2 => BPSKILL; the caller supplies the target, so this is a skill cast ON THE ALLY.
        Skill skill = new DefaultSkill(1210, 2, 1);
        Assertions.assertEquals("BPSKILL", String.valueOf(skill.getData().getCategory()),
                "the fixture must really be a skill, or from_category BPSKILL can never hold");
        skill.execute(battle, wearer, List.of(ally));

        Assertions.assertEquals(0.54, ally.getAttribute(AttributeType.SKILL_DAMAGE_BOOST).get(), 1e-9,
                "the ALLY the skill was cast on gets +54% skill damage (rank 1's #4)");
        Assertions.assertEquals(0.0, wearer.getAttribute(AttributeType.SKILL_DAMAGE_BOOST).get(), 1e-9,
                "and the wearer does not -- that would be the wrong sentence");
        System.out.println("[23048-e2e] ally=" + ally.getAttribute(AttributeType.SKILL_DAMAGE_BOOST).get()
                + " wearer=" + wearer.getAttribute(AttributeType.SKILL_DAMAGE_BOOST).get());
    }
}
