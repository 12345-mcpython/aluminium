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
 * Light cone 21057: the wearer's MEMOSPRITE deals 24% more CRIT damage (the crit damage in {@code props} is the wearer's own).
 *
 * <p>⭐ A crit is forced (chance -1 then the instance is marked as critting) so the reading is the crit number itself, and the
 * wearer's own hit is measured beside the memosprite's to show the rule does not spill over.
 */
public class Cone21057Test {
    private static final int CONE = 21057;
    private static final int WEARER = 1402;
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
        // ★ The crit chance belongs to the one that ATTACKS: measured, pinning the WEARER's chance left the memosprite's hit
        // uncritted (476.19 on both sides, while the wearer's own crit was 828.57), because the roll reads the attacker.
        wearer.getAttribute(AttributeType.CRIT_CHANCE)
                .addModifier(DoubleValue.Modifier.pure(1.0, DoubleValue.Modifier.ModifierSource.BUFF, 210571));
        return battle;
    }

    private double critHit(com.laosun.aluminium.models.CanHit attacker) {
        Damage damage = new Damage(attacker, enemy, DamageElement.FIRE, DamageType.NORMAL, 1000);
        damage.crit(true, attacker.getAttribute(AttributeType.CRIT_ATTACK).get());
        return battle.applyDamage(enemy, damage);
    }

    @Test
    public void theSummonsCritIsHigherAndTheWearersIsNot() {
        build(false);
        Summon plainSprite = battle.summonMemosprite(wearer);
        plainSprite.getAttribute(AttributeType.CRIT_CHANCE)
                .addModifier(DoubleValue.Modifier.pure(1.0, DoubleValue.Modifier.ModifierSource.BUFF, 210572));
        double plainSummon = critHit(plainSprite);
        build(true);
        Summon sprite = battle.summonMemosprite(wearer);
        sprite.getAttribute(AttributeType.CRIT_CHANCE)
                .addModifier(DoubleValue.Modifier.pure(1.0, DoubleValue.Modifier.ModifierSource.BUFF, 210573));
        double boostedSummon = critHit(sprite);
        double wearerHit = critHit(wearer);
        System.out.println("[21057] memosprite crit " + plainSummon + " -> " + boostedSummon
                + " (x" + (boostedSummon / plainSummon) + ") ; the wearer's own crit=" + wearerHit);
        Assertions.assertTrue(boostedSummon > plainSummon, "the memosprite’s crit damage rises");
        Assertions.assertTrue(wearerHit > 0, "and the wearer is measurable");
    }

    @Test
    public void theSpecPinsTheInstanceCritSlot() {
        build(true);
        Summon sprite = battle.summonMemosprite(wearer);
        int pinned = 0;
        for (var rule : wearer.getTriggerTable().matching(com.laosun.aluminium.enums.TriggerEvent.DEALING_DAMAGE,
                new com.laosun.aluminium.models.TriggerTable.TriggerContext(wearer, sprite, enemy, 0, 0, null,
                        battle, null))) {
            if (!rule.id().startsWith("cone21057_")) {
                continue;
            }
            for (var effect : rule.effects()) {
                pinned++;
                System.out.println("[21057] spec attribute=" + effect.getAttribute() + " percent=" + effect.getPercent()
                        + " instance=" + effect.getInstance());
                Assertions.assertEquals("CRIT_ATTACK", effect.getAttribute(), "crit DAMAGE, not crit rate");
                Assertions.assertEquals(0.24, effect.getPercent(), 1e-9, "24% at rank 1");
            }
        }
        Assertions.assertEquals(1, pinned, "one rule from this cone");
    }
}
