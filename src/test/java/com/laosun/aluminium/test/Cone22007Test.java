package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Light cone 2200: after the wearer's Ultimate, the WHOLE party gains 8% elation damage for 1 turn.
 *
 * <p>"我方全体" includes the wearer, so the judge reads two allies and expects both to move -- a modifier
 * aimed only at the wearer would show up here.
 */
public class Cone22007Test {
    private static final int CONE = 22007;
    private static final int WEARER = 1205;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final double SHARE = 0.08;

    private Character wearer;
    private Character ally;
    private Enemy enemy;

    private Battle battle(boolean withCone) {
        wearer = withCone
                ? CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1))
                : CharacterFactory.create(WEARER, LEVEL);
        ally = CharacterFactory.create(ALLY, LEVEL);
        enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(wearer, ally), List.of(enemy), new Random(0));
        battle.startBattle();
        return battle;
    }

    @Test
    public void anUltimateLiftsTheWholeParty() {
        Battle battle = battle(true);
        double wearerBase = wearer.getAttribute(AttributeType.ELATION_DAMAGE_BOOST).get();
        double allyBase = ally.getAttribute(AttributeType.ELATION_DAMAGE_BOOST).get();
        battle.fireTriggers(TriggerEvent.ULT_CAST, wearer, enemy, 1, 0);
        double wearerDelta = wearer.getAttribute(AttributeType.ELATION_DAMAGE_BOOST).get() - wearerBase;
        double allyDelta = ally.getAttribute(AttributeType.ELATION_DAMAGE_BOOST).get() - allyBase;
        System.out.println("[22007] after the Ultimate: wearer +" + wearerDelta + " ally +" + allyDelta);
        Assertions.assertEquals(SHARE, wearerDelta, 1e-9, "the wearer is part of 我方全体");
        Assertions.assertEquals(SHARE, allyDelta, 1e-9, "and so is the ally");
    }

    @Test
    public void aSkillCastDoesNothing() {
        Battle battle = battle(true);
        double base = ally.getAttribute(AttributeType.ELATION_DAMAGE_BOOST).get();
        battle.fireTriggers(TriggerEvent.SKILL_CAST, wearer, ally, 1, 0);
        System.out.println("[22007] after a Skill: ally +"
                + (ally.getAttribute(AttributeType.ELATION_DAMAGE_BOOST).get() - base));
        Assertions.assertEquals(0.0, ally.getAttribute(AttributeType.ELATION_DAMAGE_BOOST).get() - base, 1e-9,
                "the clause names an Ultimate (false case)");
    }

    @Test
    public void aNonWearerUltimateDoesNothing() {
        Battle battle = battle(true);
        double base = wearer.getAttribute(AttributeType.ELATION_DAMAGE_BOOST).get();
        battle.fireTriggers(TriggerEvent.ULT_CAST, ally, enemy, 1, 0);
        System.out.println("[22007] after the ALLY's Ultimate: wearer +"
                + (wearer.getAttribute(AttributeType.ELATION_DAMAGE_BOOST).get() - base));
        Assertions.assertEquals(0.0, wearer.getAttribute(AttributeType.ELATION_DAMAGE_BOOST).get() - base, 1e-9,
                "the clause names the WEARER's Ultimate (false case, and it needs a non-wearer to show)");
    }

    @Test
    public void theSpecPinsTheShareTheDurationAndTheTargets() {
        Battle battle = battle(true);
        int pinned = 0;
        for (var rule : wearer.getTriggerTable().matching(TriggerEvent.ULT_CAST,
                new TriggerTable.TriggerContext(wearer, wearer, enemy, 0, 0, null, battle, null))) {
            if (!rule.id().startsWith("cone22007_")) {
                continue;
            }
            for (var effect : rule.effects()) {
                if (!"MODIFY_ATTR".equals(effect.getOp())) {
                    continue;
                }
                pinned++;
                System.out.println("[22007] spec attribute=" + effect.getAttribute() + " percent=" + effect.getPercent()
                        + " turns=" + effect.getTurns() + " target=" + effect.getTarget());
                Assertions.assertEquals(SHARE, effect.getPercent(), 1e-9, "8% at rank 1");
                Assertions.assertEquals(1, effect.getTurns(), "for 1 turn");
                Assertions.assertEquals("all_allies", effect.getTarget(), "on the whole party");
            }
        }
        Assertions.assertEquals(1, pinned, "one rule from this cone");
    }

    @Test
    public void withoutTheConeNothingMoves() {
        Battle battle = battle(false);
        double base = ally.getAttribute(AttributeType.ELATION_DAMAGE_BOOST).get();
        battle.fireTriggers(TriggerEvent.ULT_CAST, wearer, enemy, 1, 0);
        Assertions.assertEquals(base, ally.getAttribute(AttributeType.ELATION_DAMAGE_BOOST).get(), 1e-9,
                "no cone, no change (false case)");
    }
}
