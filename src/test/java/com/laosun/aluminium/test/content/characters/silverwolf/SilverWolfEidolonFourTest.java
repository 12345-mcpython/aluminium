package com.laosun.aluminium.test.content.characters.silverwolf;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.buff.DotBuff;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1006 银狼's Eidolon 4: 「目标每有 1 个负面效果，则额外造成等同于银狼 20% 攻击力的量子属性附加伤害。该效果在每次终结技攻击中
 * <b>对每个目标</b>最多触发 <b>5</b> 次。」 (the 强化后 form).
 *
 * <p>The repeat count is the VICTIM's negative-effect count, so the check plants N distinct debuffs on the monster and reads
 * how much more the Ultimate settles at rank 4 than at rank 3 - the difference is the rider, and it must grow by exactly
 * one instance (20% of her ATK) per debuff, stopping at five.
 */
public class SilverWolfEidolonFourTest {

    private static final int WOLF = 1006;
    private static final int LEVEL = 80;

    @Test
    public void eachDebuffAddsOneInstanceAndFiveIsTheCeiling() {
        double one = rider(1);
        double three = rider(3);
        double seven = rider(7);
        System.out.println("[1006] E4 rider: 1 debuff " + one + ", 3 " + three + ", 7 " + seven);

        // Her Eidolon 2 lands a vulnerability of its own (the enhanced form), which is a 负面效果 too - so "one planted
        // debuff" is two for this clause, and every count below carries that extra one.
        Assertions.assertTrue(one > 0, "precondition: the rider settles something");
        Assertions.assertEquals(one * 2, three, one * 2 * 1e-6, "four debuffs settle four instances (two per TWO)");
        Assertions.assertEquals(one * 2.5, seven, one * 2.5 * 1e-6, "…and 「最多触发 5 次」 caps eight at five");
    }

    /** Damage the Ultimate settles at rank 4 minus the same at rank 3, with {@code debuffs} planted on the monster. */
    private static double rider(int debuffs) {
        return ultimate(4, debuffs) - ultimate(3, debuffs);
    }

    private static double ultimate(int eidolon, int debuffs) {
        Character wolf = CharacterFactory.create(WOLF, LEVEL, true, null, null, eidolon);
        Character holder = CharacterFactory.create(1001, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(wolf, holder)),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        DamageElement[] elements = {DamageElement.FIRE, DamageElement.ICE, DamageElement.THUNDER, DamageElement.WIND,
                DamageElement.QUANTUM, DamageElement.PHYSICAL, DamageElement.IMAGINARY};
        for (int i = 0; i < debuffs; i++) {
            enemy.getBuffManager().addBuff(new DotBuff(holder, elements[i], 10, 3));
        }
        Assertions.assertEquals(debuffs + 1, enemy.getBuffManager().debuffCount(),
                "precondition: the planted debuffs plus her Eidolon 2 vulnerability");
        double before = enemy.getCurrentHp();
        battle.castImmediate(wolf.getSkills().get(SkillType.ULTRA), wolf, List.of(enemy));
        return before - enemy.getCurrentHp();
    }
}