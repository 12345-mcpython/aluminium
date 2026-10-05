package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
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
 * "制胜的瞬间 / Moment of Victory": the aggro doubling, and the two properties its data backs, per rank.
 *
 * <p>The sentence states no number for the aggro half; the data states the factor 2. The two property-backed halves are read
 * by the same rule that identified them -- a slot equal to an `ability_property` value belongs to it.
 */
public class LightConeMomentTest {
    private static final int WEAPON_ID = 23005;
    private static final int WEARER = 1210;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    @Test
    public void theAggroIsDoubled() {
        double without = aggro(false);
        double with = aggro(true);
        Assertions.assertEquals(2.0, with / without, 1e-9,
                "同时使自身受到攻击的概率提高 -- the data says the factor is 2");
    }

    @Test
    public void theTwoPropertiesFollowTheRank() {
        double defenceOne = over(AttributeType.DEFENCE, 1);
        double defenceFive = over(AttributeType.DEFENCE, 5);
        Assertions.assertEquals(0.16, defenceFive - defenceOne, 1e-9,
                "defence is 40% at rank 5 and 24% at rank 1, over the base: " + defenceOne + " vs " + defenceFive);

        // Note: Effect hit has NO base value (it is a pure percentage), so a ratio over the base is Infinity -- the raw
        // value is the bonus itself, and the progression is asserted on that.
        double hitOne = raw(AttributeType.EFFECT_HIT_RATE, 1);
        double hitFive = raw(AttributeType.EFFECT_HIT_RATE, 5);
        Assertions.assertEquals(0.16, hitFive - hitOne, 1e-9,
                "and effect hit is the same progression, stated as a raw percentage: " + hitOne + " vs " + hitFive);
    }

    private static double aggro(boolean equipped) {
        Fixture f = fixture(equipped);
        return f.battle.aggroOf(f.wearer);
    }

    /** The wearer's attribute over its own base value, at the given rank. */
    private static double over(AttributeType attribute, int rank) {
        Fixture f = fixture(true, rank);
        double base = f.wearer.getAttribute(attribute).baseValue();
        return (f.wearer.getAttribute(attribute).get() - base) / base;
    }

    private static Fixture fixture(boolean equipped) {
        return fixture(equipped, 1);
    }

    private static Fixture fixture(boolean equipped, int rank) {
        Weapon weapon = equipped ? Weapon.build(WEAPON_ID, LEVEL, false, rank) : null;
        Character wearer = CharacterFactory.create(WEARER, LEVEL, true, weapon);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(wearer, ally), List.of(enemy), new Random(0));
        battle.startBattle();
        return new Fixture(wearer, battle);
    }

    private record Fixture(Character wearer, Battle battle) {
    }

    /** An attribute's plain value, for those with no base to form a ratio against. */
    private static double raw(AttributeType attribute, int rank) {
        return fixture(true, rank).wearer.getAttribute(attribute).get();
    }
}
