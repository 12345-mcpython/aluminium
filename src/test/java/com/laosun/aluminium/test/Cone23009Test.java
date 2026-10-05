package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
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
 * Light cone 23009: the constant halves (crit rate, Max HP) come from ability_property, so only the conditional half is ours --
 * and that half is a disjunction (hit OR HP price) whose buff ends when the wearer attacks.
 */
public class Cone23009Test {
    private static final int CONE = 23009;
    private static final int WEARER = 1205;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    private Character wearer(boolean withCone) {
        return withCone
                ? CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1))
                : CharacterFactory.create(WEARER, LEVEL);
    }

    @Test
    public void theConditionalHalfIsWrittenOncePerTriggerAndEndsOnAttack() {
        Character unit = wearer(true);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        int seen = 0;
        for (var event : List.of(TriggerEvent.TAKING_HIT, TriggerEvent.HP_CONSUMED)) {
            for (var rule : unit.getTriggerTable().matching(event,
                    new TriggerTable.TriggerContext(unit, unit, unit, 0, 0))) {
                for (var effect : rule.effects()) {
                    if ("MODIFY_ATTR".equals(effect.getOp())
                            && "ALL_DAMAGE_TYPE_BOOST".equals(effect.getAttribute())) {
                        seen++;
                        System.out.println("[23009] " + event + " rule id=" + rule.id()
                                + " percent=" + effect.getPercent() + " until=" + effect.getUntil());
                        Assertions.assertEquals(0.24, effect.getPercent(), 1e-9, "rank 1 states 24%");
                        Assertions.assertEquals(java.util.List.of("next_attack"), effect.getUntil(),
                        "it ends when the wearer attacks");
                    }
                }
            }
        }
        Assertions.assertEquals(2, seen, "one rule per half of the disjunction");
    }

    @Test
    public void takingAHitRaisesDamageAndAttackingEndsIt() {
        Character withCone = wearer(true);
        Character without = wearer(false);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(withCone, without), List.of(enemy), new Random(0));
        battle.startBattle();
        double before = withCone.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        battle.applyDamage(withCone, new Damage(enemy, withCone, DamageElement.PHYSICAL, DamageType.NORMAL, 20));
        double afterHit = withCone.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        System.out.println("[23009] hit: boost " + before + " -> " + afterHit);
        Assertions.assertEquals(before + 0.24, afterHit, 1e-9, "being hit raises damage dealt by 24 points");
        // The wearer attacks: the buff must go away. A hand-built Damage does NOT fire the next_attack lifetime -- measured:
        // the boost stayed at 0.24 -- so the attack has to go through the skill path.
        // ⚠ A BASIC attack, not the skill: 1205's skill grants a state and deals no damage, and the NEXT_ATTACK lifetime
        // ends only when its OWNER actually attacks (AbstractBuff:173). Measured with the skill: the boost stayed at 0.24.
        battle.castImmediate(withCone.getSkills().get(com.laosun.aluminium.enums.SkillType.COMMON), withCone, List.of(enemy));
        double afterAttack = withCone.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        System.out.println("[23009] after attacking: boost=" + afterAttack);
        Assertions.assertEquals(before, afterAttack, 1e-9, "attacking ends the effect");
    }

    @Test
    public void payingAnHpPriceAlsoRaisesDamage() {
        Character withCone = wearer(true);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(withCone), List.of(enemy), new Random(0));
        battle.startBattle();
        double before = withCone.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        battle.fireTriggers(TriggerEvent.HP_CONSUMED, withCone, withCone, 100, 0);
        double after = withCone.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        System.out.println("[23009] HP price: boost " + before + " -> " + after);
        Assertions.assertEquals(before + 0.24, after, 1e-9, "spending HP raises damage dealt by 24 points");
    }
}
