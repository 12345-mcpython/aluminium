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
        Assertions.assertEquals(4.0, gain(3), 1e-9, "three planted + the Ultimate's own = four points");
        Assertions.assertEquals(2.0, gain(1), 1e-9, "one planted + the Ultimate's own = two points");
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
}