package com.laosun.aluminium.test.battle;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.DoubleValue;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Every settlement carries the cast that produced it.
 *
 * <p><b>Why.</b> A rule can schedule a second action that settles inside the first one's window - 1415's ultimate leaves an
 * extra basic - so a caller comparing a recorded cast against the engine could only count the window's total and reported
 * twice the recorded instances (measured: fourteen against a capture's seven, every per-instance base identical). The
 * sequence number is what lets the two be told apart.
 */
public class CastSequenceTest {

    private static Character attacker() {
        return CharacterFactory.create(1001, 80);
    }

    private static Enemy dummy() {
        Enemy enemy = Enemy.fromAttributes("target", 900000, 200, 100, 100);
        enemy.setAttribute(AttributeType.HEALTH, new DoubleValue(900000));
        enemy.heal(900000);
        return enemy;
    }

    private static Random noCrit() {
        return new Random(0) {
            @Override
            public double nextDouble() {
                return 0.99;
            }
        };
    }

    /**
     * A cast bumps the sequence, and every instance it settles is stamped with it.
     */
    @Test
    public void everySettlementCarriesTheCastThatMadeIt() {
        Character character = attacker();
        Enemy enemy = dummy();
        Battle battle = new Battle(List.of(character), new ArrayList<>(List.of(enemy)), noCrit());
        battle.startBattle();
        Assertions.assertEquals(0, battle.castSequence(), "nothing has been cast yet");

        List<Long> stamped = new ArrayList<>();
        battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> stamped.add(damage.castSequence()));

        battle.castImmediate(character.getSkills().get(SkillType.COMMON), character, List.of(enemy));
        Assertions.assertFalse(stamped.isEmpty(), "the cast settled something");
        Assertions.assertTrue(stamped.stream().allMatch(each -> each == 1L),
                "and every instance belongs to cast 1: " + stamped);
        Assertions.assertEquals(1, battle.castSequence());

        stamped.clear();
        battle.castImmediate(character.getSkills().get(SkillType.COMMON), character, List.of(enemy));
        Assertions.assertTrue(stamped.stream().allMatch(each -> each == 2L),
                "a second cast stamps its own number: " + stamped);
        Assertions.assertEquals(2, battle.castSequence());
    }

    /**
     * Damage settled outside any cast is stamped 0, so "no cast" is a value rather than a stale number.
     */
    @Test
    public void damageOutsideACastIsStampedZero() {
        Character character = attacker();
        Enemy enemy = dummy();
        Battle battle = new Battle(List.of(character), new ArrayList<>(List.of(enemy)), noCrit());
        battle.startBattle();

        List<Long> stamped = new ArrayList<>();
        battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> stamped.add(damage.castSequence()));
        battle.applyAdditionalDamage(character, enemy, com.laosun.aluminium.enums.DamageElement.ICE, 1000);

        Assertions.assertEquals(List.of(0L), stamped, "a rider fired by hand belongs to no cast");
    }
}
