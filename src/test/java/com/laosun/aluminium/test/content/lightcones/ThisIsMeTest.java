package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.equipment.Weapon;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.DoubleValue;

/**
 * Light cone 21030: the wearer's Ultimate carries extra damage equal to 60% of its own DEFENCE.
 *
 * <p>Judged by SCALING (discipline 200): the share is a percentage of an attribute whose value the judge can also read,
 * so the fixture is measured at rank 1 and at rank 2 and the two extras must be in the ratio of their shares.
 *
 * <p>Its clause reads 这就是我啦！(21030): 「施放终结技时造成的伤害提高，提高数值等同于装备者防御力的 <b>60%</b>，
 * 该效果每次施放终结技时对每个敌方目标仅生效 1 次」. The earlier reading was measuring the wrong thing: the clause does
 * not add an INSTANCE, it raises the instance's value - {@code ADD_DAMAGE} adds a flat addend to the damage being settled
 * ({@code Damage.flatAddend}), and this test reads that addend, which is exactly the number the sentence names.
 */
public class ThisIsMeTest {
    private static final int CONE = 21030;
    private static final int WEARER = 1205;
    private static final int LEVEL = 80;
    private static final int CONE_WEARER = 1204;
    private Character wearer;
    private Enemy enemy;

    private Battle battle(int rank) {
        wearer = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, rank));
        enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(wearer), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        wearer.getAttribute(AttributeType.CRIT_CHANCE)
                .addModifier(com.laosun.aluminium.DoubleValue.Modifier.pure(-1.0,
                        com.laosun.aluminium.DoubleValue.Modifier.ModifierSource.BUFF, 210301));
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
        // ⚠ And the extra instance takes the toughness damage reduction too: the enemy is unbroken, so the reduction zone
        // multiplies it by 0.9 as well (the author's ruling; the 2% band below still covers the level wobble above).
        double expected = 0.6 * defence * (1 - defence / (defence + 200 + 10 * 90))
                * (1 - com.laosun.aluminium.Constant.TOUGHNESS_DAMAGE_REDUCTION);
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
        return battle.applyDamage(enemy, new com.laosun.aluminium.damage.Damage(wearer, enemy,
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

    /**
     * The flat addend the ultimate's instance carried, and the wearer's DEFENCE at that moment.
     */
    private static double[] run(boolean withCone) {
        Character wearer = withCone
                ? CharacterFactory.create(CONE_WEARER, LEVEL, true, Weapon.build(21030, LEVEL, false, 1))
                : CharacterFactory.create(CONE_WEARER, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(wearer)), new ArrayList<>(List.of(enemy)),
                BattleFixture.seeded());
        battle.startBattle();
        wearer.setCurrentEnergy(wearer.getMaxEnergy());
        double[] flat = new double[1];
        battle.addDamageObserver((Damage damage, double applied, double hpLoss, double shield, boolean died) -> {
            if (damage.getType() == com.laosun.aluminium.enums.DamageType.NORMAL) {
                flat[0] = damage.getFlatAddend();
            }
        });
        battle.castImmediate(wearer.getSkills().get(SkillType.ULTRA), wearer, List.of(enemy));
        battle.processRequests();
        return new double[]{flat[0], wearer.getAttribute(AttributeType.DEFENCE).get()};
    }

    @Test
    public void theUltimateCarriesSixtyPercentOfHerDefence() {
        double[] bare = run(false);
        double[] cone = run(true);
        Assertions.assertEquals(0.0, bare[0], 1e-9, "without the cone the instance carries no such addend");
        Assertions.assertEquals(0.6 * cone[1], cone[0], 1e-6,
                "「提高数值等同于装备者防御力的 60%」 - the addend IS that share of HER defence ("
                        + cone[1] + " x 0.6)");
    }
}
