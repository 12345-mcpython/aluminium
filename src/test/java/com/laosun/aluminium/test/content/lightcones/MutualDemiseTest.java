package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.Signal;
import com.laosun.aluminium.equipment.Weapon;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Light cone 20016: "装备者当前生命值百分比小于#1[i]%时，暴击率提高#2[i]%".
 *
 * <p>HP is lowered with small steps of `applyDamage` (its value is a skill base, not raw damage) because `Character` has no setter.
 */
public class MutualDemiseTest {
    private static final int WEARER = 1001;
    private static final int LEVEL = 80;

    @Test
    public void theConeRaisesCritChanceOnlyBelowTheThreshold() {
        double healthy = gain(0.0);
        double hurt = gain(0.8);
        System.out.println("[20016] healthy=" + healthy + " hurt=" + hurt);
        Assertions.assertEquals(0.0, healthy, 1e-6, "above the threshold nothing fires");
        Assertions.assertEquals(0.24, hurt, 1e-6, "below 80% the clause fires");
    }

    private static double gain(double fraction) {
        Character unit = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(20016, LEVEL, false, 5));
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(unit), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        double base = unit.getAttribute(AttributeType.CRIT_CHANCE).get();
        int guard = 0;
        while (fraction > 0 && unit.getCurrentHp() / unit.getMaxHp() >= fraction && guard++ < 80) {
            battle.applyDamage(unit, new Damage(enemy, unit, DamageElement.PHYSICAL, DamageType.NORMAL,
                    unit.getMaxHp() * 0.15));
        }
        // 2026-10-31: the clause is a CONTINUOUS condition and the rule now listens on HP_LOST too, so the reading is taken
        // right after the hit that crosses the threshold - which is the turn it matters. The old version measured the delta ACROSS
        // her turn start, which is where the bonus used to appear late.
        return unit.getAttribute(AttributeType.CRIT_CHANCE).get() - base;
    }
}
