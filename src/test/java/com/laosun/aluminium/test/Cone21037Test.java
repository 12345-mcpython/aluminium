package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.TriggerEvent;
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
 * Light cone 21037: a critical hit grants one layer of 【好运】 (up to four), each worth 8 points of crit damage, and the
 * layers are removed when the wearer's turn ends. This cone is why {@code CRIT_DEALT} exists.
 */
public class Cone21037Test {
    private static final int CONE = 21037;
    private static final int WEARER = 1205;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    private Character wearer(boolean withCone) {
        return withCone
                ? CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1))
                : CharacterFactory.create(WEARER, LEVEL);
    }

    private Battle battleWith(Character unit, Enemy enemy) {
        Battle battle = new Battle(List.of(unit), List.of(enemy), new Random(0));
        battle.startBattle();
        return battle;
    }

    private void forceCrit(Character unit) {
        unit.getAttribute(AttributeType.CRIT_CHANCE)
                .addModifier(DoubleValue.Modifier.pure(1.0, DoubleValue.Modifier.ModifierSource.BUFF, 210370));
    }

    @Test
    public void criticalHitsStackLuckAndANonCritDoesNot() {
        Character unit = wearer(true);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = battleWith(unit, enemy);
        double before = unit.getAttribute(AttributeType.CRIT_ATTACK).get();
        battle.applyDamage(enemy, new Damage(unit, enemy, DamageElement.FIRE, DamageType.NORMAL, 100));
        System.out.println("[21037] after a non-crit: " + before + " -> "
                + unit.getAttribute(AttributeType.CRIT_ATTACK).get());
        Assertions.assertEquals(before, unit.getAttribute(AttributeType.CRIT_ATTACK).get(), 1e-9,
                "a non-critical hit grants nothing");
        forceCrit(unit);
        for (int i = 0; i < 6; i++) {
            battle.applyDamage(enemy, new Damage(unit, enemy, DamageElement.FIRE, DamageType.NORMAL, 100));
        }
        double after = unit.getAttribute(AttributeType.CRIT_ATTACK).get();
        System.out.println("[21037] after six crits: " + before + " -> " + after + " (delta=" + (after - before) + ")");
        Assertions.assertEquals(0.08 * 4, after - before, 1e-9, "six crits cap at four layers of 8 points");
    }

    /**
     * ⚠ The owner gate needs a SECOND ally: with only the wearer in the party, `actor == self` and `actor is_ally`
     * behave identically, so swapping them changed nothing (measured: 0 red). A teammate's crit is the case that tells
     * them apart -- it must not stack the wearer's layers.
     */
    @Test
    public void aTeammatesCritDoesNotGrantTheWearersLayers() {
        Character wearer = wearer(true);
        Character ally = CharacterFactory.create(1002, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(wearer, ally), List.of(enemy), new Random(0));
        battle.startBattle();
        double before = wearer.getAttribute(AttributeType.CRIT_ATTACK).get();
        ally.getAttribute(AttributeType.CRIT_CHANCE)
                .addModifier(DoubleValue.Modifier.pure(1.0, DoubleValue.Modifier.ModifierSource.BUFF, 210371));
        for (int i = 0; i < 3; i++) {
            battle.applyDamage(enemy, new Damage(ally, enemy, DamageElement.FIRE, DamageType.NORMAL, 50));
        }
        double afterAllyCrits = wearer.getAttribute(AttributeType.CRIT_ATTACK).get();
        System.out.println("[21037] wearer after three ALLY crits: " + before + " -> " + afterAllyCrits);
        Assertions.assertEquals(before, afterAllyCrits, 1e-9, "the clause is about the WEARER's own crits");
        wearer.getAttribute(AttributeType.CRIT_CHANCE)
                .addModifier(DoubleValue.Modifier.pure(1.0, DoubleValue.Modifier.ModifierSource.BUFF, 210372));
        battle.applyDamage(enemy, new Damage(wearer, enemy, DamageElement.FIRE, DamageType.NORMAL, 50));
        System.out.println("[21037] wearer after its OWN crit: " + wearer.getAttribute(AttributeType.CRIT_ATTACK).get());
        Assertions.assertEquals(before + 0.08, wearer.getAttribute(AttributeType.CRIT_ATTACK).get(), 1e-9,
                "and it does stack on the wearer's own crit");
    }

    @Test
    public void theLayersGoAwayWhenTheWearersTurnEnds() {
        Character unit = wearer(true);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = battleWith(unit, enemy);
        double before = unit.getAttribute(AttributeType.CRIT_ATTACK).get();
        forceCrit(unit);
        battle.applyDamage(enemy, new Damage(unit, enemy, DamageElement.FIRE, DamageType.NORMAL, 100));
        double stacked = unit.getAttribute(AttributeType.CRIT_ATTACK).get();
        battle.fireTriggers(TriggerEvent.TURN_END, unit, enemy, 0, 0);
        double cleared = unit.getAttribute(AttributeType.CRIT_ATTACK).get();
        System.out.println("[21037] stacked=" + stacked + " after turn end=" + cleared + " (baseline " + before + ")");
        Assertions.assertEquals(before + 0.08, stacked, 1e-9, "one crit is one layer");
        Assertions.assertEquals(before, cleared, 1e-9, "the wearer's turn end removes them");

        Character plain = wearer(false);
        Enemy other = EnemyFactory.create(MONSTER, 90, 1);
        Battle plainBattle = battleWith(plain, other);
        double plainBefore = plain.getAttribute(AttributeType.CRIT_ATTACK).get();
        forceCrit(plain);
        plainBattle.applyDamage(other, new Damage(plain, other, DamageElement.FIRE, DamageType.NORMAL, 100));
        System.out.println("[21037] without the cone: " + plainBefore + " -> "
                + plain.getAttribute(AttributeType.CRIT_ATTACK).get());
        Assertions.assertEquals(plainBefore, plain.getAttribute(AttributeType.CRIT_ATTACK).get(), 1e-9,
                "no cone, no layers (false case)");
    }
}
