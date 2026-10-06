package com.laosun.aluminium.test.content.lightcones;

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
 * Light cone 24006: casting a Skill or Ultimate ON AN ALLY raises THAT ALLY's elation damage by 12% for 2 turns.
 *
 * <p>Two things carry the clause: the cast events already mean "after the cast", and their target is the unit that was
 * aimed at -- so `target is_ally` picks the recipient, and the modifier goes on `target`, not on the wearer. The judge
 * therefore reads THREE units: the aimed ally (raised), the wearer (NOT raised) and an enemy-targeted cast (nothing).
 */
public class ElationBrimmingWithBlessingsTest {
    private static final int CONE = 24006;
    private static final int WEARER = 1205;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

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
    public void aSkillOnAnAllyRaisesThatAllyOnly() {
        Battle battle = battle(true);
        double allyBase = ally.getAttribute(AttributeType.ELATION_DAMAGE_BOOST).get();
        double wearerBase = wearer.getAttribute(AttributeType.ELATION_DAMAGE_BOOST).get();
        battle.fireTriggers(TriggerEvent.SKILL_CAST, wearer, ally, 1, 0);
        double allyDelta = ally.getAttribute(AttributeType.ELATION_DAMAGE_BOOST).get() - allyBase;
        double wearerDelta = wearer.getAttribute(AttributeType.ELATION_DAMAGE_BOOST).get() - wearerBase;
        System.out.println("[24006] skill on ally: ally +" + allyDelta + " wearer +" + wearerDelta);
        Assertions.assertEquals(0.12, allyDelta, 1e-9, "the RECIPIENT gains 12%");
        Assertions.assertEquals(0.0, wearerDelta, 1e-9, "the wearer does not -- the effect is on the target");
    }

    @Test
    public void theUltimateDoesTheSameAndAnEnemyTargetDoesNothing() {
        Battle battle = battle(true);
        double allyBase = ally.getAttribute(AttributeType.ELATION_DAMAGE_BOOST).get();
        battle.fireTriggers(TriggerEvent.ULT_CAST, wearer, enemy, 1, 0);
        double afterEnemy = ally.getAttribute(AttributeType.ELATION_DAMAGE_BOOST).get() - allyBase;
        battle.fireTriggers(TriggerEvent.ULT_CAST, wearer, ally, 1, 0);
        double afterAlly = ally.getAttribute(AttributeType.ELATION_DAMAGE_BOOST).get() - allyBase;
        System.out.println("[24006] ult on enemy: +" + afterEnemy + " ; ult on ally: +" + afterAlly);
        Assertions.assertEquals(0.0, afterEnemy, 1e-9, "an enemy is not a single ally (我方单体角色) -- the false case");
        Assertions.assertEquals(0.12, afterAlly, 1e-9, "an ally is, and the Ultimate counts too");
    }

    @Test
    public void theSpecPinsTheNumbersTheTargetAndTheDuration() {
        Battle battle = battle(true);
        int pinned = 0;
        for (var event : List.of(TriggerEvent.SKILL_CAST, TriggerEvent.ULT_CAST)) {
            for (var rule : wearer.getTriggerTable().matching(event,
                    new TriggerTable.TriggerContext(wearer, wearer, ally, 0, 0, null, battle, null))) {
                for (var effect : rule.effects()) {
                    if (!"MODIFY_ATTR".equals(effect.getOp())) {
                        continue;
                    }
                    pinned++;
                    System.out.println("[24006] spec " + event + " percent=" + effect.getPercent()
                            + " turns=" + effect.getTurns() + " target=" + effect.getTarget()
                            + " attribute=" + effect.getAttribute());
                    Assertions.assertEquals(0.12, effect.getPercent(), 1e-9, "12% at rank 1");
                    Assertions.assertEquals(2, effect.getTurns(), "for 2 turns");
                    Assertions.assertEquals("target", effect.getTarget(), "on the aimed ally");
                }
            }
        }
        Assertions.assertEquals(2, pinned, "one rule per cast event (the clause names both slots)");
    }

    @Test
    public void withoutTheConeNothingMoves() {
        Battle battle = battle(false);
        double base = ally.getAttribute(AttributeType.ELATION_DAMAGE_BOOST).get();
        battle.fireTriggers(TriggerEvent.SKILL_CAST, wearer, ally, 1, 0);
        battle.fireTriggers(TriggerEvent.ULT_CAST, wearer, ally, 1, 0);
        Assertions.assertEquals(base, ally.getAttribute(AttributeType.ELATION_DAMAGE_BOOST).get(), 1e-9,
                "no cone, no change (false case)");
    }
}
