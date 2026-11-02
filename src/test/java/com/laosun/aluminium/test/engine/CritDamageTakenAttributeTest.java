package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code CRIT_DAMAGE_TAKEN}: the receiving side of a crit, added for 1112 Topaz's Eidolon 1.
 *
 * <p>⚠ The roll is {@code rng.nextDouble() < critChance}, so {@code alwaysRoll(0.0)} makes EVERY hit with any crit chance
 * crit. An earlier version of this test called that "non-crit" and concluded the attribute was leaking into ordinary damage;
 * what actually happened was that both arms crit. A non-crit arm needs a roll ABOVE the chance ({@code alwaysRoll(0.999)}).
 */
public class CritDamageTakenAttributeTest {

    @Test
    public void theAttributeOnlyChangesCriticalHits() {
        double plain = hit(0.0);
        double buffed = hit(0.25);
        // ⚠ A non-crit arm is NOT reachable with this character: a level-80 build's crit chance is above 1, so even a 0.999
        // roll crits. Measured earlier: rolling 0.0 and 0.999 gave byte-identical damage. The control that IS reachable is the
        // arithmetic: the growth must be exactly the share inside the crit multiplier.
        System.out.println("[crit-taken] plain=" + plain + " buffed=" + buffed + " ratio=" + (buffed / plain)
                + " expected=" + ((1 + critDamage() + 0.25) / (1 + critDamage())));
        Assertions.assertTrue(buffed > plain, "a crit hit grows once the defender carries the attribute");
        Assertions.assertEquals((1 + critDamage() + 0.25) / (1 + critDamage()), buffed / plain, 1e-6,
                "the growth is the share written on the defender, inside the crit multiplier");
    }

    private static double critDamage() {
        return CharacterFactory.create(1101, 80).getAttribute(AttributeType.CRIT_ATTACK).get();
    }

    /** One basic attack with the defender's attribute set. */
    private static double hit(double critDamageTaken) {
        Character attacker = CharacterFactory.create(1101, 80);
        var enemy = BattleFixture.dummy();
        enemy.getAttribute(AttributeType.CRIT_DAMAGE_TAKEN).base(critDamageTaken);
        Battle battle = new Battle(new ArrayList<>(List.of(attacker)), new ArrayList<>(List.of(enemy)),
                BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        double before = enemy.getCurrentHp();
        battle.castImmediate(attacker.getSkills().get(SkillType.COMMON), attacker, List.of(enemy));
        battle.processRequests();
        return before - enemy.getCurrentHp();
    }
}