package com.laosun.aluminium.test.content.characters.blade;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1205 刃's enhanced Basic 无间剑树: 「对指定敌方单体造成等同于刃 <b>#2% 攻击力 + #4% 生命上限</b>的风属性伤害，同时对其相邻目标造成等同于刃
 * <b>#3% 攻击力 + #5% 生命上限</b>的风属性伤害」.
 *
 * <p><b>Why a criterion.</b> The row is {@code [0.1, 0.56, 0.224, 1.4, 0.56]}: the FIRST cell is a HP cost, the centre is the sum of
 * two columns scaled by two different attributes, and the neighbours sum two more. Before {@code base_terms} the engine took cell 1
 * as the whole centre share and multiplied it by ATK, so both the Max-HP term and the correct columns were lost - a wrong number
 * with no symptom. Here the criterion re-computes the base from the panel and bounds the settled damage by it.
 */
public class BladeEnhancedBasicBaseTest {
    private static final int BLADE = 1205;
    private static final int LEVEL = 80;

    @Test
    public void theCentreIsAnAttackTermPlusAMaxHpTerm() {
        Character blade = CharacterFactory.create(BLADE, LEVEL);
        Enemy left = BattleFixture.monster();
        Enemy centre = BattleFixture.monster();
        Enemy right = BattleFixture.monster();
        for (Enemy enemy : List.of(left, centre, right)) {
            enemy.setMaxStance(100_000);
            enemy.setStance(100_000);
        }
        Battle battle = new Battle(new ArrayList<>(List.of(blade)), new ArrayList<>(List.of(left, centre, right)),
                BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        battle.processRequests();

        // Cast the enhanced Basic directly: its data row is what this criterion is about, and the talent swap that normally
        // equips slot 8 is a separate clause with its own record.
        var enhanced = new com.laosun.aluminium.skill.DefaultSkill(BLADE, 8, LEVEL);
        double attack = blade.getAttribute(AttributeType.ATTACK).get();
        double maxHp = blade.getMaxHp();
        double expectedBase = 0.56 * attack + 1.4 * maxHp;

        double before = centre.getCurrentHp();
        battle.castImmediate(enhanced, blade, List.of(centre));
        battle.processRequests();
        double settled = before - centre.getCurrentHp();
        System.out.println("[1205] atk=" + attack + " maxHp=" + maxHp + " expectedBase=" + expectedBase + " settled=" + settled);

        // Zones shrink the base (DEF/res/break), so the settled number is BELOW it - but it can never exceed it, and the old
        // reading (0.56 x ATK only) would land far below this bound.
        Assertions.assertTrue(settled > 0, "the enhanced Basic dealt damage");
        Assertions.assertTrue(settled < expectedBase, "the settled instance stays under its own base: " + settled + " < " + expectedBase);
        Assertions.assertTrue(settled > 0.05 * expectedBase,
                "and it is not the ATK-only reading (" + 0.56 * attack + "): " + settled);
    }
}