package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.utils.CharacterFactory;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Light cone 23022: every damage-over-time TYPE the wearer strikes marks one layer of [先知], once per type per battle, up to four.
 *
 * <p>The DoTs are planted by NAME through {@code DotBuff}'s named constructor. The probe measured that a DotBuff planted with a
 * name answers {@code has_state} and {@code stacksOf} for exactly that name -- all four game names included.
 */
public class ReforgedRemembranceTest {
    private static final int CONE = 23022;
    private static final int WEARER = 1205;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final String SEER = "先知";
    private static final String WIND_NAME = "风化";
    private static final String FIRE_NAME = "灼烧";
    private static final String THUNDER_NAME = "触电";
    private static final String PHYSICAL_NAME = "裂伤";

    private Character wearer;
    private Enemy enemy;
    private Battle battle;

    private void build(boolean withCone) {
        wearer = withCone
                ? CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1))
                : CharacterFactory.create(WEARER, LEVEL);
        enemy = EnemyFactory.create(MONSTER, 90, 1);
        battle = new Battle(List.of(wearer, CharacterFactory.create(ALLY, LEVEL)), List.of(enemy), new Random(0));
        battle.startBattle();
    }

    private void plant(String name, DamageElement element) {
        enemy.getBuffManager().addBuff(new com.laosun.aluminium.models.buff.DotBuff(wearer, element, 50, 3, 0, name));
    }

    private void strike() {
        battle.fireTriggers(TriggerEvent.DEALING_DAMAGE, wearer, enemy, 0, 0);
    }

    @Test
    public void oneLayerPerDotTypeAndOnlyOnceEach() {
        build(true);
        plant(FIRE_NAME, DamageElement.FIRE);
        strike();
        int afterBurn = wearer.getBuffManager().stacksOf(SEER);
        strike();
        int burnAgain = wearer.getBuffManager().stacksOf(SEER);
        plant(THUNDER_NAME, DamageElement.THUNDER);
        strike();
        int afterShock = wearer.getBuffManager().stacksOf(SEER);
        plant(WIND_NAME, DamageElement.WIND);
        plant(PHYSICAL_NAME, DamageElement.PHYSICAL);
        strike();
        int allFour = wearer.getBuffManager().stacksOf(SEER);
        System.out.println("[23022] seer: burn=" + afterBurn + " burnAgain=" + burnAgain
                + " +shock=" + afterShock + " +wind+physical=" + allFour);
        Assertions.assertEquals(1, afterBurn, "Burn (灼烧) gives one layer");
        Assertions.assertEquals(1, burnAgain, "and repeating it gives nothing (once per type)");
        Assertions.assertEquals(2, afterShock, "Shock (触电) adds a second");
        Assertions.assertEquals(4, allFour, "and all four types reach the cap of four");
    }

    @Test
    public void theLayersLiftAttack() {
        build(true);
        double before = wearer.getAttribute(AttributeType.ATTACK).get();
        plant(FIRE_NAME, DamageElement.FIRE);
        strike();
        double after = wearer.getAttribute(AttributeType.ATTACK).get();
        System.out.println("[23022] attack " + before + " -> " + after + " (delta " + (after - before)
                + ", 5% of the base is " + (0.05 * before) + ")");
        // The SHARE, as an equality (discipline 200): a percent modifier is a share of the pre-bonus base, so one layer adds
        // exactly 5% of `before`. Measured, `after > before` survives a `5 -> 2 percent` mutation (0 red).
        Assertions.assertEquals(0.05 * before, after - before, 1e-9, "one layer is 5% of the base, exactly");
    }

    @Test
    public void noDotNoLayer() {
        build(true);
        strike();
        System.out.println("[23022] without any DoT on the enemy: seer=" + wearer.getBuffManager().stacksOf(SEER));
        Assertions.assertEquals(0, wearer.getBuffManager().stacksOf(SEER), "the gate needs a DoT state (false case)");
    }

    @Test
    public void withoutTheConeNoLayer() {
        build(false);
        plant(FIRE_NAME, DamageElement.FIRE);
        plant(THUNDER_NAME, DamageElement.THUNDER);
        strike();
        System.out.println("[23022] with DoTs but no cone: seer=" + wearer.getBuffManager().stacksOf(SEER));
        Assertions.assertEquals(0, wearer.getBuffManager().stacksOf(SEER), "no cone, no layers (false case)");
    }

    @Test
    public void theDotInstanceCarriesTheIgnore() {
        // Read off the INSTANCE, not off a second battle: rules of one DEALING_DAMAGE event are evaluated in order and see
        // each other (discipline 212), so the very first DOT instance already granted the seer layer that the ignore rule needs.
        // Measured: the DOT instance carries 0.02 while an ordinary hit of the same battle carries 0.0 (damage_type: DOT).
        build(true);
        plant(FIRE_NAME, DamageElement.FIRE);
        com.laosun.aluminium.models.Damage dot = new com.laosun.aluminium.models.Damage(wearer, enemy,
                DamageElement.FIRE, com.laosun.aluminium.enums.DamageType.DOT, 1000);
        double dotValue = battle.applyDamage(enemy, dot);
        com.laosun.aluminium.models.Damage normal = new com.laosun.aluminium.models.Damage(wearer, enemy,
                DamageElement.FIRE, com.laosun.aluminium.enums.DamageType.NORMAL, 1000);
        double normalValue = battle.applyDamage(enemy, normal);
        System.out.println("[23022] seer=" + wearer.getBuffManager().stacksOf(SEER) + " ; DOT value=" + dotValue
                + " defenceIgnore=" + dot.getDefenceIgnore() + " zone=" + dot.breakdown().get("DefenceArea")
                + " ; NORMAL value=" + normalValue + " defenceIgnore=" + normal.getDefenceIgnore());
        Assertions.assertEquals(0.072, dot.getDefenceIgnore(), 1e-9,
                "the DOT instance itself carries one layer's 7.2% ignore");
        Assertions.assertEquals(0.0, normal.getDefenceIgnore(), 1e-9,
                "and an ordinary hit is outside the clause’s scope");
    }

    @Test
    public void theNoLayerSide() {
        // The other half of the clause, and the shape that a `when`-dropping mutation cannot survive: with NO DoT on the
        // enemy the four seer rules have nothing to match, so no layer is ever granted -- and then the DOT instance must carry
        // no ignore at all. Note: Measuring this in a battle that HAS a DoT planted does not work: the first DOT instance grants
        // the layer itself (discipline 212), which is exactly how the gap looked like an engine limitation.
        build(true);
        com.laosun.aluminium.models.Damage dot = new com.laosun.aluminium.models.Damage(wearer, enemy,
                DamageElement.FIRE, com.laosun.aluminium.enums.DamageType.DOT, 1000);
        battle.applyDamage(enemy, dot);
        System.out.println("[23022] no DoT on the enemy: seer=" + wearer.getBuffManager().stacksOf(SEER)
                + " DOT defenceIgnore=" + dot.getDefenceIgnore());
        Assertions.assertEquals(0, wearer.getBuffManager().stacksOf(SEER), "no DoT type, no layer");
        Assertions.assertEquals(0.0, dot.getDefenceIgnore(), 1e-9, "and therefore no ignore on the instance");
    }
}
