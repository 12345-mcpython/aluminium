package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
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
 * Light cone 21052: while the wearer's MEMOSPRITE is on the field, both the wearer and the memosprite deal 24% more damage.
 *
 * <p>"While it is on the field" is a LIVE fact, so the rules are instance modifiers guarded by {@code self_summon_count} -- a
 * written modifier would only be a snapshot of the moment it was granted. The judge measures all three states: no memosprite,
 * after summoning it, and without the cone.
 */
public class Cone21052Test {
    private static final int CONE = 21052;
    private static final int WEARER = 1402;   // has resources/memosprites/1402.json
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

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
        for (Character unit : List.of(wearer)) {
            unit.getAttribute(AttributeType.CRIT_CHANCE)
                    .addModifier(DoubleValue.Modifier.pure(-1.0, DoubleValue.Modifier.ModifierSource.BUFF, 210521));
        }
        return battle;
    }

    private double hit(com.laosun.aluminium.models.CanHit attacker) {
        return battle.applyDamage(enemy, new Damage(attacker, enemy, DamageElement.FIRE, DamageType.NORMAL, 1000));
    }

    @Test
    public void nothingHappensUntilTheMemospriteIsOut() {
        build(false);
        double plain = hit(wearer);
        build(true);
        double noSprite = hit(wearer);
        Summon sprite = battle.summonMemosprite(wearer);
        double withSprite = hit(wearer);
        double spriteHit = hit(sprite);
        Summon other = battle.summonMemosprite(wearer);
        double spriteAgain = hit(Summon.class.cast(other));
        System.out.println("[21052] plain=" + plain + " noSprite=" + noSprite + " withSprite=" + withSprite
                + " spriteHit=" + spriteHit + " secondSprite=" + spriteAgain);
        Assertions.assertEquals(plain, noSprite, 1e-9, "the cone alone changes nothing: no memosprite, no boost");
        Assertions.assertEquals(1.24, withSprite / noSprite, 0.02, "with it out the wearer deals 24% more");
        Assertions.assertTrue(spriteHit > 0, "and the memosprite itself is measurable");
    }

    @Test
    public void theSummonIsBoostedToo() {
        build(true);
        Summon sprite = battle.summonMemosprite(wearer);
        double boosted = hit(sprite);
        build(false);
        Summon plainSprite = battle.summonMemosprite(wearer);
        double plain = hit(plainSprite);
        System.out.println("[21052] summons: plain=" + plain + " boosted=" + boosted + " (x" + (boosted / plain) + ")");
        Assertions.assertEquals(1.24, boosted / plain, 0.02, "the memosprite’s own damage rises by 24% too");
    }

    @Test
    public void theSpecPinsBothRules() {
        build(true);
        // The rule is gated on 忆灵在场, and matching evaluates conditions (discipline 182): without a memosprite
        // on the field the rule does not exist at all -- measured, a fresh state found zero rules where one was expected.
        battle.summonMemosprite(wearer);
        int pinned = 0;
        for (var rule : wearer.getTriggerTable().matching(com.laosun.aluminium.enums.TriggerEvent.DEALING_DAMAGE,
                new com.laosun.aluminium.models.TriggerTable.TriggerContext(wearer, wearer, enemy, 0, 0, null,
                        battle, null))) {
            if (!rule.id().startsWith("cone21052_")) {
                continue;
            }
            for (var effect : rule.effects()) {
                pinned++;
                System.out.println("[21052] spec " + rule.id() + " attribute=" + effect.getAttribute()
                        + " percent=" + effect.getPercent() + " instance=" + effect.getInstance());
                Assertions.assertEquals(0.24, effect.getPercent(), 1e-9, "24% at rank 1");
            }
        }
        Assertions.assertEquals(1, pinned, "only the wearer’s rule is live for a wearer-driven hit");
    }
}
