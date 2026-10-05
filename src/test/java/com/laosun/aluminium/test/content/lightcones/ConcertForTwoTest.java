package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Light cone 21043: the wearer deals 4% more damage for EVERY character holding a shield.
 *
 * <p>This is the "场上计数谓词" row's real shape. It needed an INSTANCE slot for the damage-boost attribute plus a live
 * {@code per_stack} count: a written modifier could only ever be a snapshot, and {@code BOOST_DAMAGE} ignores {@code per_stack}.
 * The judge changes the field BETWEEN hits, so a snapshot would show up as a wrong second reading.
 */
public class ConcertForTwoTest {
    private static final int CONE = 21043;
    private static final int WEARER = 1205;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final double PER_SHIELDED = 0.04;

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
        wearer.getAttribute(AttributeType.CRIT_CHANCE)
                .addModifier(DoubleValue.Modifier.pure(-1.0, DoubleValue.Modifier.ModifierSource.BUFF, 210431));
        return battle;
    }

    private double hit(Battle battle) {
        return battle.applyDamage(enemy, new Damage(wearer, enemy, DamageElement.FIRE, DamageType.NORMAL, 1000));
    }

    @Test
    public void eachShieldedCharacterAddsItsShare() {
        Battle battle = battle(true);
        double none = hit(battle);
        battle.grantShield(wearer, 500);
        double one = hit(battle);
        battle.grantShield(ally, 500);
        double two = hit(battle);
        System.out.println("[21043] none=" + none + " one=" + one + " two=" + two
                + " share=" + (one / none - 1) + " / " + (two / none - 1));
        Assertions.assertEquals(1 + PER_SHIELDED, one / none, 1e-6, "one shielded character is +4%");
        Assertions.assertEquals(1 + 2 * PER_SHIELDED, two / none, 1e-6,
                "two are +8% -- a LIVE count read per hit, not a snapshot");
    }

    @Test
    public void aShieldThatIsGoneStopsCounting() {
        Battle battle = battle(true);
        double none = hit(battle);
        battle.grantShield(ally, 500);
        double with = hit(battle);
        ally.setShield(0);
        double gone = hit(battle);
        System.out.println("[21043] with=" + with + " after the shield is gone=" + gone + " none=" + none);
        Assertions.assertEquals(1 + PER_SHIELDED, with / none, 1e-6, "counted while it is up");
        Assertions.assertEquals(1.0, gone / none, 1e-6, "and not counted once it is gone (false case)");
    }

    @Test
    public void withoutTheConeNothingMoves() {
        Battle battle = battle(false);
        double none = hit(battle);
        battle.grantShield(wearer, 500);
        battle.grantShield(ally, 500);
        System.out.println("[21043] without the cone: " + none + " -> " + hit(battle));
        Assertions.assertEquals(none, hit(battle), 1e-9, "no cone, no scaling (false case)");
    }
}
