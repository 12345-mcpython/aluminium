package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.buff.DotBuff;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import com.laosun.aluminium.utils.RelicFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Relic 117, 4 pieces: crit damage against targets carrying 2/4 debuffs, doubled for one turn after the wearer applies a debuff.
 *
 * <p>The doubling's trigger is exercised through `Battle.tryApplyDebuff` with the wearer as caster -- the chokepoint that emits `DEBUFF_APPLIED`.
 */
public class DebuffAppliedTierTest {
    private static final int WEARER = 1001;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final int RELIC_LEVEL = 15;
    private static final String COUNTER = "负面激化";

    @Test
    public void theFourthPieceScalesWithTheTargetsDebuffCount() {
        Assertions.assertEquals(0.08, critDamage(2, false), 1e-9, "two debuffs");
        Assertions.assertEquals(0.12, critDamage(4, false), 1e-9, "four debuffs");
    }

    @Test
    public void applyingADebuffDoublesItForOneTurn() {
        Assertions.assertEquals(0.16, critDamage(2, true), 1e-9, "doubled at two debuffs");
        Assertions.assertEquals(0.24, critDamage(4, true), 1e-9, "doubled at four debuffs");
    }

    private static double critDamage(int debuffs, boolean empower) {
        Character unit = CharacterFactory.create(WEARER, LEVEL, true, null, RelicFactory.suit(117, 4, RELIC_LEVEL));
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(unit), List.of(enemy), new Random(0));
        battle.startBattle();
        for (int index = 0; index < debuffs; index++) {
            enemy.getBuffManager().addBuff(new DotBuff(unit, DamageElement.FIRE, 100, 3));
        }
        if (empower) {
            boolean landed = battle.tryApplyDebuff(unit, enemy, new DotBuff(unit, DamageElement.FIRE, 100, 3), 100.0, null);
            Assertions.assertTrue(landed, "the debuff must land for the clause to apply");
            Assertions.assertEquals(1, unit.getBuffManager().stacksOf(COUNTER),
                    "DEBUFF_APPLIED must set the one-turn counter on the applier");
        }
        Damage damage = new Damage(unit, enemy, DamageElement.QUANTUM, DamageType.NORMAL, 1000);
        battle.applyDamage(enemy, damage);
        System.out.println("[117] debuffs=" + debuffs + " empower=" + empower
                + " critDamage=" + damage.getExtraCritDamage());
        return damage.getExtraCritDamage();
    }
}
