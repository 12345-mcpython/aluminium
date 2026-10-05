package com.laosun.aluminium.test;

import com.laosun.aluminium.beans.TriggerSpec;
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

/**
 * Light cone 21031: on a crit, a fixed chance strips one BUFF from the victim, at most once per attack.
 *
 * <p>This is the SPEC half: the op, its direction, the chance and the per-attack limit. Note: The LIMIT's behaviour is
 * measured by {@code OncePerAttackLimitTest} (a hand-built rule, 1/0/1 across the attack boundary), because the cone's own
 * 16% roll makes a behavioural reading of "at most one" statistical rather than exact.
 */
public class Cone21031Test {
    private static final int CONE = 21031;
    private static final int WEARER = 1205;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    @Test
    public void theSpecCarriesTheOpTheChanceAndThePerAttackLimit() {
        Character unit = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1));
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        int pinned = 0;
        for (var rule : unit.getTriggerTable().matching(TriggerEvent.CRIT_DEALT,
                new TriggerTable.TriggerContext(unit, unit, enemy, 0, 0))) {
            if (rule.perAttack() != 1) {
                continue;
            }
            pinned++;
            var effect = rule.effects().getFirst();
            System.out.println("[21031] spec chance=" + rule.chance() + " oncePerAttack=" + rule.perAttack() == 1
                    + " op=" + effect.getOp() + " amount=" + effect.getAmount() + " target=" + effect.getTarget());
            Assertions.assertEquals(0.16, rule.chance(), 1e-9,
                    "rank 1 states a 16% fixed chance -- slot #1 of the row, not the crit-rate constant in slot #0");
            // Note: Direction: `DISPEL` cleans OUR side's debuffs; removing an ENEMY's buff is its mirror op.
            Assertions.assertEquals("REMOVE_BUFF", effect.getOp(), "the victim loses a buff, not a debuff");
            Assertions.assertEquals(1, effect.getAmount(), 1e-9, "one buff");
            Assertions.assertEquals("target", effect.getTarget(), "the victim");
        }
        Assertions.assertEquals(1, pinned, "exactly one per-attack-limited rule from this cone");
    }
}
