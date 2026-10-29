package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.Signal;
import com.laosun.aluminium.equipment.Weapon;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Three cones a real battle drives: 20020 (attack on the ultimate), 2001(heal on breaking a weakness) and 20019 (party speed at battle start, in POINTS).
 *
 * <p>Events are fired inside a driven turn and with a target, both established in round 129; the heal and the attack are measured as deltas across the
 * trigger, and the flat ones (SPEED, ATTACK) are related to the attribute's base value.
 */
public class ConeUltBreakSpeedTest {
    private static final int WEARER = 1001;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;

    @Test
    public void cone20020GivesAttackOnTheUltimate() {
        Character unit = wearer(20020, 1);
        Enemy enemy = enemy();
        Battle battle = new Battle(List.of(unit), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        battle.setCurrentMove(new Signal(unit));
        battle.beforeMove();
        double before = unit.getAttribute(AttributeType.ATTACK).get();
        double base = unit.getAttribute(AttributeType.ATTACK).baseValue();
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ULT_CAST, unit, enemy, 0, 0));
        double gain = unit.getAttribute(AttributeType.ATTACK).get() - before;
        Assertions.assertEquals(0.24 * base, gain, 1e-3,
                "rank 1: base=" + base + " gain=" + gain + " (share 0.24)");
    }

    @Test
    public void cone20017HealsOnBreakingAWeakness() {
        Character unit = wearer(20017, 1);
        Enemy enemy = enemy();
        Battle battle = new Battle(List.of(unit), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        battle.setCurrentMove(new Signal(unit));
        battle.beforeMove();
        unit.takeDamage(unit.getMaxHp() * 0.5);
        double before = unit.getCurrentHp();
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.BREAK, unit, enemy, 0, 0));
        double healed = unit.getCurrentHp() - before;
        Assertions.assertEquals(unit.getMaxHp() * 0.12, healed, 1.0,
                "rank 1 heals 12% of max HP: " + healed + " of " + unit.getMaxHp());
    }

    @Test
    public void cone20019GivesThePartySpeedInPoints() {
        Character unit = wearer(20019, 1);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Battle battle = new Battle(List.of(unit, ally), List.of(enemy()), BattleFixture.seeded());
        double wearerBefore = unit.getAttribute(AttributeType.SPEED).get();
        double allyBefore = ally.getAttribute(AttributeType.SPEED).get();
        battle.startBattle();
        Assertions.assertEquals(12.0, unit.getAttribute(AttributeType.SPEED).get() - wearerBefore, 1e-6,
                "12.0 POINTS of speed, not a share");
        Assertions.assertTrue(ally.getAttribute(AttributeType.SPEED).get() > allyBefore,
                "and the ally too: " + allyBefore + " -> " + ally.getAttribute(AttributeType.SPEED).get());
    }

    private static Character wearer(int cone, int rank) {
        return CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(cone, LEVEL, false, rank));
    }

    private static Enemy enemy() {
        return BattleFixture.monster();
    }
}
