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
 * Cones 21023 (the party takes less damage) and 23011 (the party deals more when the wearer is hurt). Both are AURAS:
 * the unit that benefits is not the wearer, so each judge measures a THIRD party with and without the cone in the team,
 * and each keeps a reading where the trigger is absent (discipline 141).
 */
public class ConeAuraPartyTest {
    private static final int ALLY = 1002;
    private static final int WEARER = 1205;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    private Character wearer(int cone) {
        return cone == 0 ? CharacterFactory.create(WEARER, LEVEL)
                : CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(cone, LEVEL, false, 1));
    }

    /** The ally's damage taken, with or without the cone wearer in the party. */
    private double damageTaken(int cone, boolean withWearer) {
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        List<Character> party = withWearer ? List.of(wearer(cone), ally) : List.of(ally);
        Battle battle = new Battle(party, List.of(enemy), new Random(0));
        battle.startBattle();
        return battle.applyDamage(ally, new Damage(enemy, ally, DamageElement.PHYSICAL, DamageType.NORMAL, 1000));
    }

    @Test
    public void thePartyTakesLessDamageWhileTheWearerIsPresent() {
        double alone = damageTaken(21023, false);
        double watched = damageTaken(21023, true);
        double control = damageTaken(0, true);
        System.out.println("[21023] alone=" + alone + " withWearer=" + watched + " control(no cone)=" + control
                + " ratio=" + (watched / alone));
        Assertions.assertEquals(0.92, watched / alone, 1e-6, "the party takes 8% less");
        Assertions.assertEquals(1.0, control / alone, 1e-6, "without the cone the reading is untouched (false case)");
        // Note: Same lesson as 23011: pin the duration, or shortening it is invisible to this judge.
        Character owner = wearer(21023);
        int pinned = 0;
        for (var r : owner.getTriggerTable().matching(TriggerEvent.BATTLE_START,
                new TriggerTable.TriggerContext(owner, owner, owner, 0, 0))) {
            for (var effect : r.effects()) {
                if ("MODIFY_DAMAGE_TAKEN".equals(effect.getOp())) {
                    pinned++;
                    System.out.println("[21023] spec percent=" + effect.getPercent() + " turns=" + effect.getTurns()
                            + " target=" + effect.getTarget());
                    Assertions.assertEquals(-0.08, effect.getPercent(), 1e-9, "rank 1 states 8% less");
                    Assertions.assertEquals(5, effect.getTurns(), "for five turns");
                    Assertions.assertEquals("all_allies", effect.getTarget(), "the whole party");
                }
            }
        }
        Assertions.assertEquals(1, pinned, "exactly one party damage-taken rule comes from this cone");
    }

    @Test
    public void thePartyDealsMoreOnlyWhenTheWearerLosesHp() {
        Character wearer = wearer(23011);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(wearer, ally), List.of(enemy), new Random(0));
        battle.startBattle();
        double before = ally.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        // The FALSE case first: the ALLY losing HP must not move the party bonus.
        battle.applyDamage(ally, new Damage(enemy, ally, DamageElement.PHYSICAL, DamageType.NORMAL, 20));
        double afterAllyHurt = ally.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        System.out.println("[23011] after the ALLY was hurt: boost " + before + " -> " + afterAllyHurt);
        Assertions.assertEquals(before, afterAllyHurt, 1e-9, "only the WEARER losing HP moves it");
        battle.applyDamage(wearer, new Damage(enemy, wearer, DamageElement.PHYSICAL, DamageType.NORMAL, 20));
        double after = ally.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        System.out.println("[23011] after the WEARER was hurt: boost " + before + " -> " + after);
        Assertions.assertEquals(before + 0.09, after, 1e-9, "the wearer losing HP raises the party's damage by 9 points");
        // Note: Durations must be PINNED: every reading above happens in the same turn, so shortening one changes
        // nothing (measured: 2 -> 1 turn gave reds 0 until this block existed).
        int pinned = 0;
        for (var r : wearer.getTriggerTable().matching(TriggerEvent.HP_LOST,
                new TriggerTable.TriggerContext(wearer, enemy, wearer, 0, 0))) {
            for (var effect : r.effects()) {
                if ("ALL_DAMAGE_TYPE_BOOST".equals(effect.getAttribute()) && effect.getPercent() == 0.09) {
                    pinned++;
                    System.out.println("[23011] spec percent=" + effect.getPercent() + " turns=" + effect.getTurns()
                            + " target=" + effect.getTarget());
                    Assertions.assertEquals(2, effect.getTurns(), "for two turns");
                    Assertions.assertEquals("all_allies", effect.getTarget(), "the party, not the wearer");
                }
            }
        }
        Assertions.assertEquals(1, pinned, "the party damage rule is registered on HP_LOST");
    }
}
