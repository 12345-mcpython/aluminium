package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.models.entity.Character;
import com.laosun.aluminium.models.damage.Damage;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.equipment.Weapon;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Light cone 20003: "防御力提高#1%。当装备者当前生命值百分比小于#2%时，其防御力额外提高#3%" at rank 5.
 *
 * <p>DEFENCE is flat (each share scales the post-start base).
 *
 * <p>⚠⚠ <b>This case used to pin a bug.</b> It asserted that the rule adds {@code #1} above the threshold and {@code #1+#3}
 * below it - true of the SHIPPED FILE at the time, and wrong as a statement about the panel, because the cone's own
 * {@code ability_property} already carries {@code #1} ({@code Weapon.build}) and the file's base rule added it again. The
 * old assertions read only the RULE's gain, never the panel, so nothing noticed (measured 2026-10-19: 1007.795 where one
 * share gives 947.665). The file now states only the EXTRA share below the threshold, and this case pins both halves:
 * <b>no rule gain at or above the threshold</b>, and <b>{@code #3} below it</b>.
 */
public class AmberTest {
    private static final int WEARER = 1001;
    private static final int LEVEL = 80;

    @Test
    public void thePropertyCarriesTheBaseAndTheRuleAddsTheExtraShare() {
        double[] healthy = defence(0.0);
        double[] middle = defence(0.7);
        double[] hurt = defence(0.5);
        System.out.println("[20003] healthyGain=" + healthy[0] + " base=" + healthy[1]
                + " middleGain=" + middle[0] + " middleHp=" + middle[2]
                + " hurtGain=" + hurt[0] + " hp=" + hurt[2]);
        // The band between the two thresholds: above #2, so ONLY the base branch may apply. Without this reading, a file whose
        // branches stop being exhaustive (e.g. base gated on hp >= 90%) would still pass.
        Assertions.assertTrue(middle[2] >= 0.5 && middle[2] < 0.9, "precondition: inside the untested band");
        // The base share is the cone's `ability_property`, so NO rule may add anything at or above the threshold.
        Assertions.assertEquals(0.0, middle[0], 1e-6, "above the threshold: the property alone carries #1");
        Assertions.assertEquals(0.0, healthy[0], 1e-6, "the same at full HP");
        Assertions.assertTrue(hurt[2] < 0.5, "precondition: below the threshold");
        Assertions.assertEquals(0.32 * hurt[1], hurt[0], 1e-6,
                "below 50% the rule adds the EXTRA share #3 only (0.32 at rank 5), on top of the property");
    }

    /**
     * gain, base value, and the final HP fraction.
     */
    private static double[] defence(double fraction) {
        Character unit = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(20003, LEVEL, false, 5));
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(unit), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        if (fraction > 0) {
            int guard = 0;
            while (unit.getCurrentHp() / unit.getMaxHp() >= fraction && guard++ < 80) {
                battle.applyDamage(unit, new Damage(enemy, unit, DamageElement.PHYSICAL, DamageType.NORMAL,
                        unit.getMaxHp() * 0.15));
            }
        }
        double before = unit.getAttribute(AttributeType.DEFENCE).get();
        battle.setCurrentMove(new Signal(unit));
        battle.beforeMove();
        return new double[]{unit.getAttribute(AttributeType.DEFENCE).get() - before,
                unit.getAttribute(AttributeType.DEFENCE).baseValue(), unit.getCurrentHp() / unit.getMaxHp()};
    }
}
