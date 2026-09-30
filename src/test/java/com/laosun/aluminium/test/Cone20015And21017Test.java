package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Light cone 20015: 「当装备者施放普攻后，使下一次行动提前#1[i]%」 -- ADVANCE, the spelling shipped relics 110 and 308 already use.
 * Light cone 21017: 「使装备者普攻和战技造成的伤害提高#1[i]%」 -- two attributes, so the two rules cannot collide.
 */
public class Cone20015And21017Test {
    private static final int WEARER = 1001;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    @Test
    public void theBasicAttackAdvancesTheWearersNextAction() {
        Character unit = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(20015, LEVEL, false, 5));
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(unit), List.of(enemy), new Random(0));
        battle.startBattle();
        battle.currentMove = new Signal(unit);
        battle.beforeMove();
        double before = timeRemaining(battle, unit);
        battle.fireTriggers(TriggerEvent.BASIC_ATTACK, unit, enemy, 0, 0);
        double after = timeRemaining(battle, unit);
        System.out.println("[20015] avBefore=" + before + " avAfter=" + after + " ratio=" + (after / before));
        Assertions.assertFalse(Double.isNaN(before), "precondition: the wearer is in the queue");
        // ⭐ Measured semantics: ADVANCE takes a SHARE OF THE CURRENT action value (148.51 -> 118.81 is exactly 1 - 0.20).
        Assertions.assertEquals(1 - 0.2, after / before, 1e-6,
                "the advance is a share of the current action value: " + before + " -> " + after);
    }

    @Test
    public void theConeRaisesBothBasicAndSkillDamage() {
        Character unit = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(21017, LEVEL, false, 5));
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(unit), List.of(enemy), new Random(0));
        double basicBefore = unit.getAttribute(AttributeType.BASIC_ATTACK_DAMAGE_BOOST).get();
        double skillBefore = unit.getAttribute(AttributeType.SKILL_DAMAGE_BOOST).get();
        battle.startBattle();
        double basic = unit.getAttribute(AttributeType.BASIC_ATTACK_DAMAGE_BOOST).get() - basicBefore;
        double skill = unit.getAttribute(AttributeType.SKILL_DAMAGE_BOOST).get() - skillBefore;
        System.out.println("[21017] basicBoost=" + basic + " skillBoost=" + skill);
        Assertions.assertEquals(0.48, basic, 1e-6, "rank 5 states 48% for the basic attack");
        Assertions.assertEquals(0.48, skill, 1e-6, "and the same for the skill");
    }

    /** Copied verbatim from CastTargetTest: how much action value the unit still has. */
    private static double timeRemaining(Battle battle, CanHit target) {
        for (Signal signal : battle.queue.snapshot()) {
            if (signal.getCanHit() == target) {
                return battle.queue.getTimeRemaining(signal);
            }
        }
        return Double.NaN;
    }
}
