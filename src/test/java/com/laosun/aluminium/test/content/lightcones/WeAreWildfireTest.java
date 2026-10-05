package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
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
 * Light cone 21023: at battle start, damage taken is reduced for 5 turns AND "at the same time immediately restore to all of our side
 * HP equal to 30% of each one's own lost HP".
 *
 * <p>"each one's own" is the whole point of the new `target_lost_hp` scale: the share is read per RECIPIENT, so a
 * full-HP ally heals nothing while a hurt one heals 30% of its own gap. Measuring both in one battle is what makes this
 * reading attributable.
 */
public class WeAreWildfireTest {
    private static final int CONE = 21023;
    private static final int WEARER = 1205;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final double SHARE = 0.3;

    private Character wearer;
    private Character ally;
    private Enemy enemy;

    /** Builds the battle but does NOT start it, so a unit can be hurt before the cone's battle-start heal fires. */
    private Battle preBattle(boolean withCone) {
        wearer = withCone
                ? CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1))
                : CharacterFactory.create(WEARER, LEVEL);
        ally = CharacterFactory.create(ALLY, LEVEL);
        enemy = EnemyFactory.create(MONSTER, 90, 1);
        return new Battle(List.of(wearer, ally), List.of(enemy), new Random(0));
    }

    private void hurt(Battle battle, Character victim, double amount) {
        battle.applyDamage(victim, new Damage(enemy, victim, DamageElement.FIRE, DamageType.NORMAL, amount));
    }

    @Test
    public void theHealReadsEachRecipientsOwnLostHp() {
        Battle battle = preBattle(true);
        hurt(battle, ally, 400);
        double allyLost = ally.getMaxHp() - ally.getCurrentHp();
        Assertions.assertTrue(allyLost > 0, "precondition: the ally is hurt before the battle starts");
        double allyBefore = ally.getCurrentHp();
        double wearerBefore = wearer.getCurrentHp();
        Assertions.assertEquals(wearer.getMaxHp(), wearerBefore, 1e-6, "precondition: the wearer is at full HP");

        battle.startBattle();

        double allyHealed = ally.getCurrentHp() - allyBefore;
        double wearerHealed = wearer.getCurrentHp() - wearerBefore;
        System.out.println("[21023] lost=" + allyLost + " allyHealed=" + allyHealed
                + " expected=" + (allyLost * SHARE) + " wearerHealed=" + wearerHealed);
        Assertions.assertEquals(allyLost * SHARE, allyHealed, 1e-6, "30% of the ALLY's own lost HP");
        Assertions.assertEquals(0.0, wearerHealed, 1e-9,
                "and an ally at full HP heals nothing -- \"each one's own\", not a share of one pool");
    }

    @Test
    public void theSpecPinsTheScaleAndTheNumbers() {
        Battle battle = preBattle(true);
        battle.startBattle();
        int pinned = 0;
        for (var rule : wearer.getTriggerTable().matching(TriggerEvent.BATTLE_START,
                new TriggerTable.TriggerContext(wearer, wearer, enemy, 0, 0))) {
            for (var effect : rule.effects()) {
                if ("HEAL".equals(effect.getOp())) {
                    pinned++;
                    System.out.println("[21023] spec scale=" + effect.getScale() + " percent=" + effect.getPercent()
                            + " target=" + effect.getTarget());
                    Assertions.assertEquals("target_lost_hp", effect.getScale(), "the new scale");
                    Assertions.assertEquals(SHARE, effect.getPercent(), 1e-9, "rank 1 states 30%");
                    Assertions.assertEquals("all_allies", effect.getTarget(), "the whole party");
                }
            }
        }
        Assertions.assertEquals(1, pinned, "exactly one heal rule from this cone");
    }

    @Test
    public void withoutTheConeNothingIsHealed() {
        Battle battle = preBattle(false);
        hurt(battle, ally, 400);
        double before = ally.getCurrentHp();
        battle.startBattle();
        System.out.println("[21023] without the cone: " + before + " -> " + ally.getCurrentHp());
        Assertions.assertEquals(before, ally.getCurrentHp(), 1e-9, "no cone, no heal (false case)");
    }
}
