package com.laosun.aluminium.test.content.characters.silverwolf;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.buff.DotBuff;
import com.laosun.aluminium.buff.VulnerabilityBuff;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1006 银狼's Eidolon 1: 「目标每有 1 个负面效果，银狼恢复 1 点能量，每次终结技最多触发 5 次」.
 */
public class SilverWolfEidolonOneTest {

    private static final int WOLF = 1006;
    private static final int ALLY = 1001;
    private static final int LEVEL = 80;

    @Test
    public void eachDebuffPaysOnePoint() {
        // The Ultimate lands a debuff of its own, and the clause counts the target AFTER that - so the expectation is the
        // count at settlement time, not the one the fixture started with.
        Assertions.assertEquals(28.0, gain(3), 1e-9, "four debuffs at 7 points each (under the five-trigger cap)");
        Assertions.assertEquals(14.0, gain(1), 1e-9, "two debuffs at 7 points each");
    }

    /** @return how much MORE energy her Ultimate pays with the Eidolon than without, for that many debuffs */
    private static double gain(int debuffs) {
        return cast(debuffs, 1) - cast(debuffs, 0);
    }

    private static double cast(int debuffs, int eidolon) {
        Character wolf = CharacterFactory.create(WOLF, LEVEL, true, null, null, eidolon);
        Character holder = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(wolf, holder)),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        // One vulnerability plus one DOT per element: distinct kinds, because the same kind refreshes instead of stacking.
        enemy.getBuffManager().addBuff(new VulnerabilityBuff(1, 0.01));
        DamageElement[] elements = {DamageElement.FIRE, DamageElement.ICE, DamageElement.THUNDER, DamageElement.WIND};
        for (int i = 1; i < debuffs; i++) {
            enemy.getBuffManager().addBuff(new DotBuff(holder, elements[i - 1], 10, 2));
        }
        Assertions.assertEquals(debuffs, enemy.getBuffManager().debuffCount(), "precondition");
        double before = wolf.getCurrentEnergy();
        battle.castImmediate(wolf.getSkills().get(SkillType.ULTRA), wolf, List.of(enemy));
        System.out.println("[1006] e" + eidolon + " " + debuffs + " debuffs => +" + (wolf.getCurrentEnergy() - before));
        return wolf.getCurrentEnergy() - before;
    }
    @Test
    public void eidolonSixRaisesDamageWithTheEnemysDebuffs() {
        // Rank 6 against rank 5: her Eidolon 2 also moves the damage now (it is the enhanced form, +20% taken), and this
        // case is about Eidolon 6 alone.
        double one = boostWith(1, 6) - boostWith(1, 5);
        double three = boostWith(3, 6) - boostWith(3, 5);
        double seven = boostWith(5, 6) - boostWith(5, 5);
        System.out.println("[1006] E6 boost: 1 debuff " + one + ", 3 " + three + ", 5 " + seven);
        // 0.4, not 0.2: her Eidolon 2 now plants a vulnerability on entry (the enhanced form), which is a 负面效果 of its own -
        // so "one planted debuff" is TWO for this clause, and the counts below each carry that extra one.
        Assertions.assertEquals(0.4, closer(one, 0.4), 0.02, "one planted debuff plus Eidolon 2's vulnerability: 2 x 20%");
        Assertions.assertEquals(0.8, closer(three, 0.8), 0.02, "four debuffs: 80%");
        Assertions.assertEquals(1.0, closer(seven, 1.0), 0.02, "「最多提高 100%」 (six debuffs would exceed it)");
    }

    /** The counts include the Ultimate-free path, so rounding noise is expected; compare loosely. */
    private static double closer(double value, double expected) {
        return Math.abs(value - expected) < 0.02 ? expected : value;
    }

    private static double boostWith(int debuffs, int eidolon) {
        Character wolf = CharacterFactory.create(WOLF, LEVEL, true, null, null, eidolon);
        Character holder = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(wolf, holder)),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        enemy.getBuffManager().addBuff(new VulnerabilityBuff(1, 0.01));
        DamageElement[] elements = {DamageElement.FIRE, DamageElement.ICE, DamageElement.THUNDER, DamageElement.WIND};
        for (int i = 1; i < debuffs; i++) {
            enemy.getBuffManager().addBuff(new DotBuff(holder, elements[i - 1], 10, 2));
        }
        double[] zone = new double[]{-1};
        battle.addDamageObserver((damage, applied, hp, shield, died) ->
                zone[0] = damage.breakdown().getOrDefault("BoostArea", 0.0));
        battle.applyDamage(enemy, new com.laosun.aluminium.damage.Damage(wolf, enemy,
                DamageElement.QUANTUM, com.laosun.aluminium.enums.DamageType.NORMAL, 100));
        return zone[0];
    }
}
