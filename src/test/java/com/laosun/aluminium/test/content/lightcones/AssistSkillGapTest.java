package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Gap witness for light cone 23060's assist-skill half: the vocabulary can <b>name</b> the category, but the engine has
 * no assist slot and therefore never casts one.
 *
 * <p>What the cone still lacks: 「施放助战技时，恢复 6 点能量并获得【启航】（2 回合，最多 3 层）；每层使助战技伤害 +20%；
 * 满 3 层时每层使终结技伤害 +20%」. Writing those rules today would be a rule that <b>never runs</b>, which this project
 * treats as worse than an absent clause - so the gap is registered, and this test pins today's shape: the day the engine
 * gains an assist slot (or an assist cast entry point), the first assertion below turns red.
 *
 * <p>The positive half is pinned too: {@code from_category Assist} <b>loads</b> (the word exists), so the missing piece is
 * the slot / cast path, not the condition vocabulary.
 */
public class AssistSkillGapTest {

    private static final int LEVEL = 80;
    private static final int ASSIST_CARRIER = 1510;   // the only character whose data carries Assist skills
    private static final Set<Integer> ASSIST_SLOTS = Set.of(22, 25, 26);

    /**
     * No SkillType maps to an assist slot, and 1510 is never equipped with one.
     */
    @Test
    public void theEngineHasNoAssistSlot() {
        for (SkillType type : SkillType.values()) {
            Integer slot = Constant.SKILL_SLOT.get(type);
            if (slot != null) {
                Assertions.assertFalse(ASSIST_SLOTS.contains(slot),
                        "SkillType " + type + " now maps to an assist slot (" + slot
                                + ") - the assist half of light cone 23060 can be written");
            }
        }

        Character carrier = CharacterFactory.create(ASSIST_CARRIER, LEVEL);
        Battle battle = new Battle(new ArrayList<>(List.of(carrier)),
                new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.seeded());
        battle.startBattle();
        for (var skill : carrier.getSkills().values()) {
            System.out.println("[23060] installed slot " + skill.getSkillSlot());
            Assertions.assertFalse(ASSIST_SLOTS.contains(skill.getSkillSlot()),
                    "1510 is now equipped with an assist skill - the entry point exists");
        }
    }

    /**
     * The condition vocabulary already accepts the category, so only the cast path is missing.
     */
    @Test
    public void theCategoryNameItselfIsAccepted() {
        Assertions.assertNotNull(SkillCategory.fromString("Assist"), "the data spelling is known");
        com.laosun.aluminium.beans.EffectSpec effect = new com.laosun.aluminium.beans.EffectSpec();
        TriggerSpecs.set(effect, "op", "APPLY_BUFF");
        TriggerSpecs.set(effect, "buff", "见证");
        TriggerSpecs.set(effect, "permanent", Boolean.TRUE);
        TriggerSpecs.set(effect, "target", "self");
        TriggerSpec rule = TriggerSpecs.rule("CAST_SETUP", List.of("actor == self", "from_category Assist"), effect);
        TriggerSpecs.set(rule, "id", "witness_assist");
        Assertions.assertDoesNotThrow(() -> new com.laosun.aluminium.trigger.TriggerTable(1510, List.of(rule)),
                "a rule may name the category; what is missing is a cast that raises it");
    }
}

