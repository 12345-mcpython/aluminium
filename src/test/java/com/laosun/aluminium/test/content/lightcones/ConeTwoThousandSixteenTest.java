package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.equipment.Weapon;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 俱殁 (20016): 「装备者当前生命值百分比小于 <b>80%</b> 时，暴击率提高 <b>12%</b>」.
 *
 * <p><b>Why this used to be a gap, and what closed it.</b> The clause is a CONTINUOUS condition, and the rule only evaluated it at
 * her turn start - so a hit that took her under 80% mid-turn read no bonus until her next turn, which is exactly the moment the
 * bonus matters. The rule now also listens on {@code HP_LOST}, and the engine reads {@code hp_percent} off the table's OWNER, so
 * the reading is right on the event that changes it (measured: 0.05 -> 0.17 with the wearer at 78% HP).
 *
 * <p>What is deliberately NOT modelled: healing back above 80% within the same turn leaves the bonus until her next turn boundary
 * (the modifier is granted with {@code turns: 1} and the rule re-evaluates there). The sentence's condition is continuous, so that
 * is a bounded window rather than a wrong number, and it is stated here instead of being hidden.
 */
public class ConeTwoThousandSixteenTest {

    private static final int LEVEL = 80;
    private static final int WEARER = 1002;
    private static final double SHARE = 0.12;

    private Battle battle;
    private Character wearer;
    private Enemy enemy;

    private void build() {
        wearer = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(20016, LEVEL, false, 1));
        enemy = BattleFixture.monster();
        battle = new Battle(new ArrayList<>(List.of(wearer)), new ArrayList<>(List.of(enemy)),
                BattleFixture.seeded());
        battle.startBattle();
    }

    private double crit() {
        return wearer.getAttribute(AttributeType.CRIT_CHANCE).get();
    }

    private void take(double shareOfMaxHp) {
        battle.applyDamage(wearer, new Damage(enemy, wearer, DamageElement.PHYSICAL, DamageType.NORMAL,
                wearer.getMaxHp() * shareOfMaxHp));
        battle.processRequests();
    }

    /**
     * The bonus appears IN THE TURN the hit lands, not on her next turn.
     */
    @Test
    public void theBonusAppearsTheMomentSheDropsBelowEightyPercent() {
        build();
        double full = crit();
        take(0.35);
        Assertions.assertTrue(wearer.getCurrentHp() / wearer.getMaxHp() < 0.8,
                "precondition: the hit took her under the threshold (hp " + wearer.getCurrentHp() / wearer.getMaxHp() + ")");
        Assertions.assertEquals(SHARE, crit() - full, 1e-9,
                "「当前生命值百分比小于 80% 时，暴击率提高 12%」 - read on the very turn that crossed the line");
    }

    /**
     * Above the threshold it pays nothing, even after a hit.
     */
    @Test
    public void aHitThatLeavesHerAboveTheThresholdPaysNothing() {
        build();
        double full = crit();
        take(0.10);
        Assertions.assertTrue(wearer.getCurrentHp() / wearer.getMaxHp() > 0.8, "precondition: still above 80%");
        Assertions.assertEquals(0.0, crit() - full, 1e-9, "no threshold, no bonus");
    }
}
