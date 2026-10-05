package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.Summon;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.utils.CharacterFactory;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Light cone 23036: the wearer AND its memosprite weave a stack of [织锦] with every attack, each layer lifts crit damage, and
 * once the stack is full every layer also lifts basic-attack damage.
 *
 * <p>Three readings: the stack from a wearer attack, the stack from a MEMOSPRITE attack, and the crit number at one layer versus
 * at the cap -- anchored to the crit base the engine reports, so a change in the share cannot hide.
 */
public class Cone23036Test {
    private static final int CONE = 23036;
    private static final int WEARER = 1402;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final String BROCADE = "织锦";
    private static final int CAP = 6;

    private Character wearer;
    private Enemy enemy;
    private Battle battle;

    private Battle build(boolean withCone) {
        wearer = withCone
                ? CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1))
                : CharacterFactory.create(WEARER, LEVEL);
        enemy = EnemyFactory.create(MONSTER, 90, 1);
        battle = new Battle(List.of(wearer, CharacterFactory.create(ALLY, LEVEL)), List.of(enemy), new Random(0));
        battle.startBattle();
        wearer.getAttribute(AttributeType.CRIT_CHANCE)
                .addModifier(DoubleValue.Modifier.pure(1.0, DoubleValue.Modifier.ModifierSource.BUFF, 230361));
        return battle;
    }

    private void attack(com.laosun.aluminium.models.CanHit attacker) {
        battle.fireTriggers(TriggerEvent.ALLY_ATTACK, attacker, enemy, 1, 0);
    }

    private double critHit() {
        // A NORMAL cast category, because clause ④ is scoped by rom_category Normal: measured, the engine refuses an
        // instance slot for BASIC_ATTACK_DAMAGE_BOOST, so the content limits the BOOST to a basic-attack instance instead.
        return battle.applyDamage(enemy, new Damage(wearer, enemy, DamageElement.FIRE, DamageType.NORMAL, 1000,
                com.laosun.aluminium.enums.SkillCategory.NORMAL));
    }

    @Test
    public void bothTheWearerAndTheMemospriteWeave() {
        build(true);
        attack(wearer);
        int afterWearer = wearer.getBuffManager().stacksOf(BROCADE);
        Summon sprite = battle.summonMemosprite(wearer);
        attack(sprite);
        int afterSprite = wearer.getBuffManager().stacksOf(BROCADE);
        System.out.println("[23036] brocade after the wearer's attack=" + afterWearer
                + " after the memosprite's attack=" + afterSprite);
        Assertions.assertEquals(1, afterWearer, "the wearer's own attack weaves one");
        Assertions.assertEquals(2, afterSprite, "and so does the memosprite’s (装备者和忆灵)");
    }

    @Test
    public void theStackStopsAtSix() {
        build(true);
        for (int i = 0; i < 9; i++) {
            attack(wearer);
        }
        int stacks = wearer.getBuffManager().stacksOf(BROCADE);
        System.out.println("[23036] brocade after nine attacks=" + stacks);
        Assertions.assertEquals(CAP, stacks, "最多叠加 6 层");
    }

    @Test
    public void eachLayerAddsCritDamage() {
        build(true);
        double untamed = critHit();
        attack(wearer);
        double atOne = critHit();
        for (int i = 0; i < CAP + 3; i++) {
            attack(wearer);
        }
        double atCap = critHit();
        double base = wearer.getAttribute(AttributeType.CRIT_ATTACK).get();
        double perLayer = (atOne - untamed) / untamed * (1 + base);
        // The cap, as the arithmetic both clauses imply: clause ③ adds to the crit MULTIPLIER, clause ④ multiplies the
        // damage zone (it is scoped to basic attacks, and this hit is one), so the cap is
        //   raw  x  (1 + base + 0.54)  x  (1 + 0.54),  where raw = untamed / (1 + base).
        double theCap = untamed / (1 + base) * (1 + base + 0.09 * CAP) * (1 + 0.09 * CAP);
        System.out.println("[23036] crit untamed=" + untamed + " one layer=" + atOne + " at cap=" + atCap
                + " ; per layer=" + perLayer + " ; the cap by arithmetic=" + theCap);
        Assertions.assertEquals(0.09, perLayer, 0.002, "9% per layer at rank 1");
        Assertions.assertEquals(theCap, atCap, theCap * 0.02,
                "six layers of crit damage AND six layers of basic-attack damage");
    }

    @Test
    public void withoutTheConeNothingIsWoven() {
        build(false);
        attack(wearer);
        System.out.println("[23036] without the cone: stacks=" + wearer.getBuffManager().stacksOf(BROCADE));
        Assertions.assertEquals(0, wearer.getBuffManager().stacksOf(BROCADE), "no cone, no stacks (false case)");
    }
}
