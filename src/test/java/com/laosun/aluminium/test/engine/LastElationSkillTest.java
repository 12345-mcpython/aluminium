package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code last_elation_skill_id}: the slot of the last Elation-damage skill this unit <b>finished</b> casting, so
 * 「与上一次施放的欢愉技不同」 is the ordinary two-variable comparison {@code from_skill_id != last_elation_skill_id}.
 *
 * <p>Pinned because the ORDER is the whole trick: the value is written after the cast's events, so a rule firing on
 * {@code CAST_SETUP} still reads the <b>previous</b> cast. Writing it earlier would make every comparison "equal to
 * itself".
 */
public class LastElationSkillTest {
    private static final int WEARER = 1506;   // carries BOTH Elation slots (20 and 21)
    private static final int LEVEL = 80;

    @Test
    public void theComparisonSeesThePreviousCast() {
        Character wearer = CharacterFactory.create(WEARER, LEVEL);
        EffectSpec mark = new EffectSpec();
        TriggerSpecs.set(mark, "op", "ADD_STACK");
        TriggerSpecs.set(mark, "buff", "标记");
        TriggerSpecs.set(mark, "amount", 1.0);
        TriggerSpecs.set(mark, "maxStacks", 10);
        TriggerSpecs.set(mark, "permanent", Boolean.TRUE);
        TriggerSpecs.set(mark, "target", "self");
        TriggerSpec rule = TriggerSpecs.rule("CAST_SETUP",
                List.of("from_category ElationDamage", "from_skill_id != last_elation_skill_id"), mark);
        TriggerSpecs.set(rule, "id", "probe_different_elation_skill");
        wearer.setTriggerTable(wearer.getTriggerTable().plus(new TriggerTable(WEARER, List.of(rule))));

        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(wearer, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(enemy)), BattleFixture.seeded());
        battle.startBattle();

        battle.castImmediate(wearer.getSkills().get(SkillType.ELATION_SKILL), wearer, List.of(enemy));
        Assertions.assertEquals(1, wearer.getBuffManager().stacksOf("标记"),
                "first cast: 0 != slot, so it counts as different");
        battle.castImmediate(wearer.getSkills().get(SkillType.ELATION_SKILL), wearer, List.of(enemy));
        Assertions.assertEquals(1, wearer.getBuffManager().stacksOf("标记"),
                "the same Elation skill again: equal to the previous one, so no extra layer");
        battle.castImmediate(wearer.getSkills().get(SkillType.ELATION_EXTRA), wearer, List.of(enemy));
        Assertions.assertEquals(2, wearer.getBuffManager().stacksOf("标记"),
                "a different Elation skill: different again");
    }
}

