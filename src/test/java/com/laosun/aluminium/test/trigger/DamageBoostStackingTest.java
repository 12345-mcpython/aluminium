package com.laosun.aluminium.test.trigger;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.buff.BoostDamageBuff;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.test.support.BattleFixture;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Two independent damage-boost sentences must both land: the canon's boost zone is <b>additive</b> (1 + Σ 增伤), and each
 * {@code BoostDamageBuff} carries its own modifier id, so {@code isSameKind} now compares the creating rule - different
 * sentences coexist, the same sentence refreshes.
 */
public class DamageBoostStackingTest {

    private static final int LEVEL = 80;

    @Test
    public void differentRulesAddUpAndTheSameRuleRefreshes() {
        Fixture f = new Fixture();
        double base = f.probe();

        f.apply("rule-a", 0.2);
        Assertions.assertEquals(1.2, f.probe() / base, 1e-6, "one sentence: +20%");

        f.apply("rule-b", 0.15);
        Assertions.assertEquals(2, f.owner.getBuffManager().countBuffs(BoostDamageBuff.class),
                "two sentences, two modifiers");
        Assertions.assertEquals(1.35, f.probe() / base, 1e-6, "additive: 1 + 0.20 + 0.15");

        f.apply("rule-a", 0.1);
        Assertions.assertEquals(2, f.owner.getBuffManager().countBuffs(BoostDamageBuff.class),
                "the same rule refreshes instead of stacking");
        Assertions.assertEquals(1.25, f.probe() / base, 1e-6, "1 + 0.10 + 0.15");
    }

    private static final class Fixture {
        private final Character owner;
        private final Enemy enemy = BattleFixture.monster();
        private final Battle battle;

        private Fixture() {
            owner = Character.fromAttributes("Probe", 100000, 500, 1000, 100);
            battle = new Battle(new ArrayList<>(List.of(owner)), new ArrayList<>(List.of(enemy)),
                    BattleFixture.alwaysRoll(0.99));
            battle.startBattle();
        }

        private void apply(String rule, double rate) {
            BoostDamageBuff buff = new BoostDamageBuff(1, rate);
            buff.setRuleId(rule);
            owner.getBuffManager().addBuff(buff);
        }

        private double probe() {
            double before = enemy.getCurrentHp();
            battle.applyDamage(enemy, new Damage(owner, enemy, DamageElement.PHYSICAL, DamageType.NORMAL, 1000.0));
            return before - enemy.getCurrentHp();
        }
    }
}
