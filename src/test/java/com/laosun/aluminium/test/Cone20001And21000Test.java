package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Cones 20001 and 21000: "施放战技和终结技时 / 施放终结技时，治疗量提高" -- the window is THE CAST.
 *
 * <p>Four readings around the engine's own cast window: absent, raised by the cast's own pre-cast event, and gone once
 * the cast's events are done. A `turns: 1` spelling would pass the first three and fail the last -- which is exactly the
 * difference the upstream callback pair states.
 */
public class Cone20001And21000Test {
    private static final int WEARER = 1205;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    private Character wearer;
    private Character ally;
    private Enemy enemy;

    private Battle battle(int cone) {
        wearer = cone == 0
                ? CharacterFactory.create(WEARER, LEVEL)
                : CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(cone, LEVEL, false, 1));
        ally = CharacterFactory.create(ALLY, LEVEL);
        enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(wearer, ally), List.of(enemy), new Random(0));
        battle.startBattle();
        return battle;
    }

    /** One cast, measured at three moments: base, inside the window, and after the delivery is over. */
    private double[] castAndMeasure(Battle battle, SkillCategory category) {
        double base = wearer.getAttribute(AttributeType.OUTGOING_HEALING_BOOST).get();
        var skill = wearer.getSkills().values().iterator().next();
        var token = battle.beginCast(skill, wearer);
        battle.fireTriggers(TriggerEvent.CAST_SETUP, wearer, ally, 0, 0, category);
        double inside = wearer.getAttribute(AttributeType.OUTGOING_HEALING_BOOST).get();
        battle.endCastOutcome();
        double after = wearer.getAttribute(AttributeType.OUTGOING_HEALING_BOOST).get();
        battle.endCast(token);
        return new double[]{base, inside, after};
    }

    @Test
    public void theSkillAndTheUltimateEachRaiseHealingForTheirOwnCastOnly() {
        Battle battle = battle(20001);
        double[] skill = castAndMeasure(battle, SkillCategory.BPSKILL);
        double[] ult = castAndMeasure(battle, SkillCategory.ULTRA);
        System.out.println("[20001] skill base=" + skill[0] + " inside=" + skill[1] + " after=" + skill[2]
                + " | ult base=" + ult[0] + " inside=" + ult[1] + " after=" + ult[2]);
        Assertions.assertEquals(skill[0] + 0.12, skill[1], 1e-9, "a Skill raises it while its cast is delivered");
        Assertions.assertEquals(skill[0], skill[2], 1e-9, "and the cast's end takes it away again");
        Assertions.assertEquals(ult[0] + 0.12, ult[1], 1e-9, "the same for an Ultimate");
        Assertions.assertEquals(ult[0], ult[2], 1e-9, "removed at the end of that cast too");
    }

    @Test
    public void theUltimateOnlyConeIgnoresASkillCast() {
        Battle battle = battle(21000);
        double[] skill = castAndMeasure(battle, SkillCategory.BPSKILL);
        double[] ult = castAndMeasure(battle, SkillCategory.ULTRA);
        System.out.println("[21000] skill inside=" + skill[1] + " (base " + skill[0] + ") | ult inside=" + ult[1]);
        Assertions.assertEquals(skill[0], skill[1], 1e-9, "21000 names the Ultimate only (false case for a Skill)");
        Assertions.assertEquals(ult[0] + 0.12, ult[1], 1e-9, "and 12% during the Ultimate's cast");
    }

    @Test
    public void withoutTheConesNothingMoves() {
        Battle battle = battle(0);
        double[] ult = castAndMeasure(battle, SkillCategory.ULTRA);
        System.out.println("[20001/21000] without the cones: base=" + ult[0] + " inside=" + ult[1]);
        Assertions.assertEquals(ult[0], ult[1], 1e-9, "no cone, no boost, ever (false case)");
    }
}
