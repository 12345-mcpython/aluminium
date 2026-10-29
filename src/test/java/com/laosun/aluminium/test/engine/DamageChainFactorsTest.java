package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.models.entity.Character;
import com.laosun.aluminium.models.damage.Damage;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import com.laosun.aluminium.models.damage.BattleDamage;

/**
 * The multiplicative factors the game's damage chain has beside the additive DMG-boost bucket
 * ({@code HSR.md} §2.2 / §10.3):
 *
 * <pre>
 * x = base
 * x × (1 + Σ元素增伤 + 全属性增伤 + DoT增伤)      // BoostArea
 * x × (1 + Level_FinalDamageAddedRatio)          // FinalBoostArea
 * x × (1 + ComboDamageAddedRatio)                // ComboArea
 * x × BaseDamageMultiRatio                       // BaseMultiArea
 * x × min(cap, 1 + Σ易伤)                        // VulnerableArea (capped)
 * x × (1 + Monster_DamageTakenRatio)             // MonsterTakenArea - OUTSIDE the cap
 * x × 暴击                                        // CritArea
 * x − 元素吸收                                    // absorbed - a SUBTRACTION, not a zone
 * </pre>
 *
 * <p><b>Why this exists.</b> The engine used to fold every DMG boost into one additive sum and to stop at the
 * vulnerability cap, so the three factors above were silently absent: a sentence that raised "最终伤害" had nowhere to
 * land, and a monster's own +X% taken was either lost or (worse) folded inside the cap that is supposed to exclude it.
 *
 * <p><b>What this file pins.</b> That each factor is a multiplier of its own ({@code 1.2 × 1.3}, not {@code 1 + 0.2 +
 * 0.3}), that the monster factor really sits outside the cap (a victim already at 3.5 still pays it), that absorb is
 * subtracted after every multiplier and floors at zero, that break/super-break/true damage skip the whole boost side,
 * and that {@code BattleDamage} really reads these attributes off the two units.
 */
public class DamageChainFactorsTest {
    private static final int DANHENG = 1002;
    private static final int LEVEL = 80;
    private static final double EPS = 1e-6;

    /**
     * The second additive factor is its OWN multiplier: 1.2 × 1.3, not 1 + 0.2 + 0.3.
     */
    @Test
    public void theFinalBoostIsAMultiplierOfItsOwn() {
        Damage damage = raw(1000).addBoost(0.2).addFinalBoost(0.3);

        Assertions.assertEquals(1000 * 1.2 * 1.3, damage.toValue(), EPS,
                "1 + 0.2 + 0.3 = 1.5 would be the folded reading, and that is what the game does NOT do");
    }

    /**
     * The combo boost multiplies too, and the base-damage multiplier multiplies as {@code 1 + delta}.
     */
    @Test
    public void theComboBoostAndTheBaseMultiplierMultiply() {
        Damage damage = raw(1000).addBoost(0.5).addComboBoost(0.4).addBaseMulti(0.25);

        Assertions.assertEquals(1000 * 1.5 * 1.4 * 1.25, damage.toValue(), EPS);
    }

    /**
     * The monster's own vulnerability is applied OUTSIDE the vulnerability cap.
     *
     * <p>This is the case a folded implementation cannot pass: at the cap (1 + 2.5 = 3.5) the capped zone is already
     * saturated, so an implementation that adds 0.2 inside it stays at 3.5 while the game gives 3.5 × 1.2.
     */
    @Test
    public void theMonsterVulnerabilitySitsOutsideTheCap() {
        Damage damage = raw(1000).addVulnerable(2.5).addMonsterTaken(0.2);

        Assertions.assertEquals(1000 * 3.5 * 1.2, damage.toValue(), EPS,
                "the capped zone saturates at 3.5; the monster factor still multiplies on top");
    }

    /**
     * Absorb is a subtraction after every multiplier, in the settled number's units.
     */
    @Test
    public void absorbIsSubtractedNotMultiplied() {
        Damage damage = raw(1000).addBoost(0.5).addAbsorb(100);

        Assertions.assertEquals(1000 * 1.5 - 100, damage.toValue(), EPS,
                "a ratio reading would have given 1500 x (1 - 0.1) or 1500 - 100 x 1.5");
    }

    /**
     * Note: An absorb larger than the hit floors it at zero - it cannot turn damage into healing.
     */
    @Test
    public void absorbFloorsAtZero() {
        Damage damage = raw(1000).addAbsorb(5000);

        Assertions.assertEquals(0, damage.toValue(), EPS);
    }

    /**
     * Break / super break / true damage keep their own formulas: none of the boost-side factors apply, and neither
     * does absorb.
     */
    @Test
    public void breakAndTrueDamageSkipTheBoostSide() {
        for (DamageType type : List.of(DamageType.BREAK, DamageType.SUPER_BREAK, DamageType.TRUE)) {
            Damage damage = new Damage(attacker(), defender(), DamageElement.WIND, type, 1000)
                    .addBoost(0.2).addFinalBoost(1).addComboBoost(1).addBaseMulti(1).addAbsorb(500);

            Assertions.assertEquals(1000, damage.toValue(), EPS, type + " must not take the boost chain");
        }
    }

    /**
     * The wiring: {@code BattleDamage} reads the four attributes off the two units, so a rule that grants them through
     * {@code MODIFY_ATTR} really moves a settled hit.
     */
    @Test
    public void theBattleReadsTheAttributesOffTheUnits() {
        double plain = settled(hero -> {
        });

        Assertions.assertEquals(plain * 1.5, settled(hero -> hero.setAttribute(
                AttributeType.FINAL_DAMAGE_BOOST, new DoubleValue(0.5))), EPS, "final boost +50%");

        Assertions.assertEquals(plain * 1.25, settledWithEnemy(enemy -> enemy.setAttribute(
                AttributeType.MONSTER_DAMAGE_TAKEN, new DoubleValue(0.25))), EPS, "monster taken +25%");

        Assertions.assertEquals(plain - 50, settledWithEnemy(enemy -> enemy.setAttribute(
                AttributeType.WIND_DAMAGE_ABSORB, new DoubleValue(50))), EPS, "Wind absorb 50 is subtracted");

        Assertions.assertEquals(plain * 1.3, settled(hero -> hero.setAttribute(
                AttributeType.COMBO_DAMAGE_BOOST, new DoubleValue(0.3))), EPS, "combo boost +30%");

        Assertions.assertEquals(plain * 1.1, settled(hero -> hero.setAttribute(
                AttributeType.BASE_DAMAGE_MULTI_RATIO, new DoubleValue(0.1))), EPS, "base multi +10%");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static Damage raw(double base) {
        return new Damage(attacker(), defender(), DamageElement.WIND, DamageType.NORMAL, base);
    }

    private static Character attacker() {
        return CharacterFactory.create(DANHENG, LEVEL);
    }

    private static Enemy defender() {
        return BattleFixture.monster();
    }

    /**
     * Settles one Wind hit in a real battle, optionally changing the attacker first.
     */
    private static double settled(java.util.function.Consumer<Character> change) {
        return run(change, hero -> {
        });
    }

    /**
     * The same, changing the enemy.
     */
    private static double settledWithEnemy(java.util.function.Consumer<Enemy> change) {
        return run(hero -> {
        }, change);
    }

    private static double run(java.util.function.Consumer<Character> heroChange,
                              java.util.function.Consumer<Enemy> enemyChange) {
        Character hero = attacker();
        Enemy enemy = defender();
        Battle battle = new Battle(List.of(hero), List.of(enemy), BattleFixture.alwaysRoll(0.99));
        battle.startBattle();
        heroChange.accept(hero);
        enemyChange.accept(enemy);
        return battle.applyDamage(enemy, new Damage(hero, enemy, DamageElement.WIND, DamageType.NORMAL, 1000));
    }
}
