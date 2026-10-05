package com.laosun.aluminium.test;

import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.skill.Skill;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Elation slice 1a (2026-09-30): the data files the Elation damage skills under slots 20/21, and until they were added to
 * {@code Constant.SKILL_SLOT} AND to {@code SkillType#isIntrinsic()} the kit builder skipped them -- measured on 1501, whose kit
 * held "0 Elation skill(s) and 4 ordinary".
 *
 * <p>⭐ Read on a SHIPPED character: its own loaded kit, its own skills, and the type the engine would give each of them.
 */
public class ElationSlotsTest {
    private static final int WEARER = 1501;
    private static final int LEVEL = 80;

    @Test
    public void theElationSlotIsInTheKitAndMapsToElation() {
        Character character = CharacterFactory.create(WEARER, LEVEL);
        int elation = 0;
        int ordinary = 0;
        for (var entry : character.getSkills().entrySet()) {
            Skill skill = entry.getValue();
            if (skill == null || skill.getData() == null) {
                continue;
            }
            DamageType type = SkillExecutor.damageTypeOf(skill.getData());
            if (skill.getData().getCategory() == SkillCategory.ELATION_DAMAGE) {
                elation++;
                System.out.println("[slots] " + entry.getKey() + " is ElationDamage (category "
                        + skill.getData().getCategory() + ") -> " + type);
                Assertions.assertEquals(DamageType.ELATION, type, entry.getKey() + " settles as ELATION");
                Assertions.assertEquals(SkillType.ELATION_SKILL, entry.getKey(),
                        "and it is the slot the data files it under");
            } else {
                ordinary++;
                Assertions.assertEquals(DamageType.NORMAL, type, entry.getKey() + " stays NORMAL");
            }
        }
        System.out.println("[slots] " + character.getName() + ": " + elation + " Elation skill(s), " + ordinary
                + " ordinary");
        Assertions.assertEquals(1, elation, "the kit now carries exactly one Elation damage skill");
        Assertions.assertTrue(ordinary >= 3, "beside the ordinary ones (the false side of the mapping)");
    }
}
