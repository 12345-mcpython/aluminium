package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import com.laosun.aluminium.utils.RelicFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Relic 108, 4 pieces: "无视其 10% 防御力，若目标拥有量子属性弱点则额外无视 10%" -- an INSTANCE-scoped modifier.
 *
 * <p>The clause is a property of the hit, so it is asserted on the damage instance the battle settles: 10% against any target, 20% against a Quantum-weak one.
 * The expected values come from `relic_sets.json`'s `param`.
 */
public class QuantumWeaknessIgnoreTest {
    private static final int WEARER = 1001;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final int RELIC_LEVEL = 15;

    @Test
    public void theFourthPieceIgnoresDefenceAndMoreAgainstAQuantumWeakTarget() {
        double plain = ignoreAgainst(false);
        double weak = ignoreAgainst(true);
        Assertions.assertEquals(0.1, plain, 1e-9, "the base half: " + plain);
        Assertions.assertEquals(0.2, weak, 1e-9, "the weakness half adds to it: " + weak);
    }

    private static double ignoreAgainst(boolean forceWeakness) {
        Character unit = CharacterFactory.create(WEARER, LEVEL, true, null, RelicFactory.suit(108, 4, RELIC_LEVEL));
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        enemy.setStanceWeak(forceWeakness
                ? java.util.Set.of(DamageElement.QUANTUM)
                : java.util.Set.of(DamageElement.FIRE));
        Battle battle = new Battle(List.of(unit), List.of(enemy), new Random(0));
        battle.startBattle();
        Damage damage = new Damage(unit, enemy, DamageElement.QUANTUM, DamageType.NORMAL, 1000);
        battle.applyDamage(enemy, damage);
        return damage.getDefenceIgnore();
    }
}
