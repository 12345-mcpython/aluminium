package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.equipment.Weapon;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * The low-health family, and a kill-triggered one: 20003 (defence, plus an extra below half health), 20016 (crit rate below a health threshold) and
 * 2000(attack after a kill).
 *
 * <p>The condition is judged by firing BATTLE_START twice: healthy (must not fire) and wounded (must fire). NOTE the fixture drives current HP --
 * `takeDamage` -- and checks `getCurrentHp() / getMaxHp()`, because `HEALTH.get()` is the max-HP STAT, which is what blocked the previous attempt.
 */
public class ConeLowHealthTest {
    private static final int WEARER = 1001;
    private static final int LEVEL = 80;


    @Test
    public void cone20007AttackAfterAKill() {
        Character unit = wearer(20007, 1);
        Battle battle = battleWith(unit);
        battle.startBattle();
        double before = unit.getAttribute(AttributeType.ATTACK).get();
        double base = unit.getAttribute(AttributeType.ATTACK).baseValue();
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.KILL, unit, null, 0, 0));
        double gain = unit.getAttribute(AttributeType.ATTACK).get() - before;
        Assertions.assertTrue(gain > 0, "a kill grants attack (gain " + gain + ", base " + base + ")");
    }

    private static Character wearer(int cone, int rank) {
        return CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(cone, LEVEL, false, rank));
    }

    private static Battle battleWith(Character wearer) {
        return BattleFixture.battle(List.of(wearer), List.of(BattleFixture.monster()));
    }

    private static double share(int cone, int rank, AttributeType attribute, boolean wounded, boolean secondFire) {
        Character unit = wearer(cone, rank);
        Battle battle = battleWith(unit);
        battle.startBattle();
        if (!secondFire) {
            return (unit.getAttribute(attribute).get() - unit.getAttribute(attribute).baseValue())
                    / unit.getAttribute(attribute).baseValue();
        }
        if (wounded) {
            unit.takeDamage(unit.getMaxHp() * 0.8);
            Assertions.assertTrue(unit.getCurrentHp() / unit.getMaxHp() <= 0.5,
                    "the fixture must be below half health: " + unit.getCurrentHp() + " / " + unit.getMaxHp());
        }
        double before = unit.getAttribute(attribute).get();
        double base = unit.getAttribute(attribute).baseValue();
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.BATTLE_START, unit, null, 0, 0));
        double gain = unit.getAttribute(attribute).get() - before;
        return attribute == AttributeType.CRIT_CHANCE ? gain : gain / base;
    }

}
