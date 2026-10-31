package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.equipment.Weapon;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 于夜色中 (23001): 「速度大于 <b>100</b> 时，每超过 <b>10</b> 点，普攻和战技造成的伤害提高 <b>6%</b>，该效果可叠加 <b>6</b> 层」.
 *
 * <p>The stair counts the steps ABOVE a line, which is what {@code per_steps: "SPEED-100:10"} spells: 110 is one layer, 100 is none.
 * Without the threshold the same spelling would read {@code floor(110/10)} = eleven layers.
 */
public class ConeTwentyThreeThousandOneTest {

    private static final int LEVEL = 80;
    private static final int WEARER = 1204;
    private static final double SHARE = 0.06;

    /**
     * The wearer's basic-attack boost with the cone on, at a forced speed.
     */
    private static double boostAt(int speed) {
        Character wearer = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(23001, LEVEL, false, 1));
        double natural = wearer.getAttribute(AttributeType.SPEED).get();
        Battle battle = new Battle(new ArrayList<>(List.of(wearer)),
                new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.seeded());
        // Arm the panel before the battle starts, so the BATTLE_START rule reads the speed we mean to test.
        wearer.getAttribute(AttributeType.SPEED)
                .addModifier(com.laosun.aluminium.DoubleValue.Modifier.pure(speed - natural));
        battle.startBattle();
        return wearer.getAttribute(AttributeType.BASIC_ATTACK_DAMAGE_BOOST).get();
    }

    /**
     * The same stair on the terminator's own crit damage: 「同时终结技的暴击伤害提高 #4%，该效果可叠加 #5 层」.
     *
     * <p>Read on the instance ({@code extraCritDamage}), not on the panel: the clause is scoped to the ULTIMATE, so a
     * global CRIT_ATTACK modifier would be a different sentence (it would raise the basic attack too).
     */
    @Test
    public void theStairAlsoRaisesTheUltimatesCritDamage() {
        Assertions.assertEquals(0.0, ultCritDamageAt(100), 1e-9, "at 100 exactly: no layer");
        Assertions.assertEquals(0.12, ultCritDamageAt(110), 1e-9, "one step -> +12% crit damage on the ultimate");
        Assertions.assertEquals(0.36, ultCritDamageAt(130), 1e-9, "three steps");
        Assertions.assertEquals(0.72, ultCritDamageAt(160), 1e-9, "six steps - the ceiling");
        Assertions.assertEquals(0.72, ultCritDamageAt(200), 1e-9, "past the ceiling it stays at six");
        Assertions.assertEquals(0.0, basicCritDamageAt(160), 1e-9,
                "the basic attack must NOT get it: the clause names 「终结技」");
    }

    /** The instance's extra crit damage from the wearer's ULTIMATE at a forced speed. */
    private static double ultCritDamageAt(int speed) {
        return extraCritDamage(speed, SkillType.ULTRA);
    }

    /** The control: the same wearer, the same speed, a basic attack. */
    private static double basicCritDamageAt(int speed) {
        return extraCritDamage(speed, SkillType.COMMON);
    }

    private static double extraCritDamage(int speed, SkillType slot) {
        Character wearer = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(23001, LEVEL, false, 1));
        double natural = wearer.getAttribute(AttributeType.SPEED).get();
        Battle battle = new Battle(new ArrayList<>(List.of(wearer)),
                new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.seeded());
        wearer.getAttribute(AttributeType.SPEED)
                .addModifier(com.laosun.aluminium.DoubleValue.Modifier.pure(speed - natural));
        battle.startBattle();
        List<Damage> seen = new ArrayList<>();
        battle.addDamageObserver((Damage damage, double applied, double hpLost, double shield, boolean died) ->
                seen.add(damage));
        battle.castImmediate(wearer.getSkills().get(slot), wearer, List.of(battle.enemies().getFirst()));
        battle.processRequests();
        return seen.stream()
                .filter(d -> d.getType() == DamageType.NORMAL)
                .mapToDouble(Damage::getExtraCritDamage)
                .findFirst()
                .orElseThrow(() -> new AssertionError("the " + slot + " settled no instance"));
    }

    @Test
    public void theStairCountsTheStepsAboveOneHundred() {
        double atHundred = boostAt(100);
        Assertions.assertEquals(0.0, atHundred, 1e-9, "「大于 100 时」 - at 100 exactly, no layer");

        Assertions.assertEquals(SHARE, boostAt(110), 1e-9, "110 is one step above the line (not eleven)");
        Assertions.assertEquals(3 * SHARE, boostAt(130), 1e-9, "130 is three steps");
        Assertions.assertEquals(6 * SHARE, boostAt(160), 1e-9, "160 is six steps - the sentence's ceiling");
        Assertions.assertEquals(6 * SHARE, boostAt(200), 1e-9,
                "「可叠加 6 层」 - past the ceiling it keeps paying six, not ten");
    }
}

