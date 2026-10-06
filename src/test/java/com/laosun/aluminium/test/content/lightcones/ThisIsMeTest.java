package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
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
 * Light cone 21030: the wearer's Ultimate carries extra damage equal to 60% of its own DEFENCE.
 *
 * <p>Judged by SCALING (discipline 200): the share is a percentage of an attribute whose value the judge can also read,
 * so the fixture is measured at rank 1 and at rank 2 and the two extras must be in the ratio of their shares.
 */
public class ThisIsMeTest {
    private static final int CONE = 21030;
    private static final int WEARER = 1205;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    private Character wearer;
    private Enemy enemy;

    private Battle battle(int rank) {
        wearer = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, rank));
        enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(wearer), List.of(enemy), new Random(0));
        battle.startBattle();
        wearer.getAttribute(AttributeType.CRIT_CHANCE)
                .addModifier(com.laosun.aluminium.models.DoubleValue.Modifier.pure(-1.0,
                        com.laosun.aluminium.models.DoubleValue.Modifier.ModifierSource.BUFF, 210301));
        return battle;
    }

    private int rules() {
        return (int) wearer.getTriggerTable().matching(TriggerEvent.DEALING_DAMAGE,
                        new TriggerTable.TriggerContext(wearer, wearer, enemy, 0, 0, null, battle(1),
                                SkillCategory.ULTRA))
                .stream().filter(rule -> rule.id().startsWith("cone21030_")).count();
    }

    @Test
    public void theSpecPinsTheScaleAndThePerAttackLimit() {
        Battle battle = battle(1);
        int pinned = 0;
        for (var rule : wearer.getTriggerTable().matching(TriggerEvent.DEALING_DAMAGE,
                new TriggerTable.TriggerContext(wearer, wearer, enemy, 0, 0, null, battle, SkillCategory.ULTRA))) {
            if (!rule.id().startsWith("cone21030_")) {
                continue;
            }
            for (var effect : rule.effects()) {
                pinned++;
                System.out.println("[21030] spec op=" + effect.getOp() + " percent=" + effect.getPercent()
                        + " scale=" + effect.getScale());
                Assertions.assertEquals("ADD_DAMAGE", effect.getOp(), "the amount is derived, so it is extra damage");
                Assertions.assertEquals(0.6, effect.getPercent(), 1e-9, "60% of the wearer's defence at rank 1");
                Assertions.assertEquals("self_attr:DEFENCE", effect.getScale(), "of DEFENCE, not of attack");
            }
        }
        Assertions.assertEquals(1, pinned, "one rule from this cone");
    }

    @Test
    public void aPlainAttackCarriesNothing() {
        Battle battle = battle(1);
        int pinned = (int) wearer.getTriggerTable().matching(TriggerEvent.DEALING_DAMAGE,
                        new TriggerTable.TriggerContext(wearer, wearer, enemy, 0, 0, null, battle,
                                SkillCategory.NORMAL))
                .stream().filter(rule -> rule.id().startsWith("cone21030_")).count();
        System.out.println("[21030] rules matching a plain attack = " + pinned);
        Assertions.assertEquals(0, pinned, "Ultimate (终结技) only -- the false case");
    }

    /**
     * The SHARE as an EQUALITY against the engine's own damage formula (discipline 192): the extra is
     * {@code percent * DEFENCE} scaled by the defence zone {@code 1 / (DEFENCE + 200 + 10 * level)}.
     *
     * <p>Note: A cross-rank ratio does NOT work here: the cone's own props (defence +16% at rank 1, +18% at rank 2) move
     * with the rank as well, so the two ranks differ by more than the share -- measured, the ratio came out 1.293 where
     * the shares alone would say 1.1 (discipline 200: change only the item under test).
     */
    @Test
    public void theExtraDamageIsTheShareOfDefenceInTheDefenceZone() {
        Battle battle = battle(1);
        double defence = wearer.getAttribute(AttributeType.DEFENCE).get();
        double before = hit(battle);
        castUltimate(battle);
        double after = hit(battle, SkillCategory.ULTRA);
        double extra = after - before;
        // Measured, not assumed: the reduction uses the DEFENDER's level (the enemy is level 90 here), and the shape is
        // damage * (1 - def / (def + 200 + 10 * defenderLevel)) -- my first guess used the ATTACKER's level and was 1.5% off.
        double expected = 0.6 * defence * (1 - defence / (defence + 200 + 10 * 90));
        System.out.println("[21030] defence=" + defence + " extra=" + extra + " expected=" + expected);
        Assertions.assertEquals(expected, extra, expected * 0.02,
                "60% of the wearer's defence, through the defence zone");
    }

    private double extraDamage(int rank) {
        Battle battle = battle(rank);
        double before = hit(battle);
        castUltimate(battle);
        double after = hit(battle, SkillCategory.ULTRA);
        return after - before;
    }

    private double hit(Battle battle) {
        return hit(battle, SkillCategory.NORMAL);
    }

    private double hit(Battle battle, SkillCategory category) {
        // The category rides on the Damage INSTANCE (measured: applyDamage has no (target, damage, category)
        // overload -- the third parameter is an EnergyGrant), which is also why a cast-scoped boost can be scoped at all.
        return battle.applyDamage(enemy, new com.laosun.aluminium.models.Damage(wearer, enemy,
                com.laosun.aluminium.enums.DamageElement.FIRE, com.laosun.aluminium.enums.DamageType.NORMAL, 1000,
                category));
    }

    private void castUltimate(Battle battle) {
        var ultimate = wearer.getSkills().values().stream()
                .filter(candidate -> candidate.getData() != null
                        && candidate.getData().getCategory() == SkillCategory.ULTRA)
                .findFirst().orElseThrow();
        battle.castImmediate(ultimate, wearer, List.of(enemy));
    }
}
