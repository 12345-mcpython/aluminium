package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.buff.DotBuff;
import com.laosun.aluminium.buff.StackBuff;
import com.laosun.aluminium.buff.StateBuff;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * {@code counts_as} / {@code counts_as_requires}: a buff that also answers to <b>other</b> state names, as opposed to
 * being a second buff.
 *
 * <p>This is the engine half on its own, with no content file involved - the same split {@code HolderStateGateTest}
 * documents, so that "the rule did not fire" and "the question got the wrong answer" stay distinguishable.
 */
public class BuffAliasTest {

    @Test
    public void aBuffAnswersToTheOtherNamesItAliases() {
        Enemy enemy = enemyInBattle();
        StackBuff arcana = new StackBuff("char:1307/arcana", 99, true, 50);
        arcana.setCountsAs(List.of("dot:wind_shear"), null);
        enemy.getBuffManager().addBuff(arcana);

        Assertions.assertTrue(enemy.getBuffManager().hasState("char:1307/arcana"), "precondition: the buff is on the unit");
        Assertions.assertTrue(enemy.getBuffManager().hasState("dot:wind_shear"),
                "「处于【char:1307/arcana】状态时，也会被视为陷入了风化状态」 - the alias answers");
        Assertions.assertFalse(enemy.getBuffManager().hasState("dot:bleed"),
                "an alias answers for the names it lists and no others");

        // The alias is a question about a state, NOT a second buff: a real 风化 would be a DotBuff and would tick.
        Assertions.assertEquals(0, enemy.getBuffManager().allBuffsOf(DotBuff.class).size(),
                "「被视为」 must not apply a DOT - four DOTs would deal four DOTs' damage, which the sentence does not say");

        enemy.getBuffManager().removeState("char:1307/arcana");
        Assertions.assertFalse(enemy.getBuffManager().hasState("dot:wind_shear"),
                "the alias lives exactly as long as the buff that carries it");
    }

    @Test
    public void anAliasAnswersOnlyWhileItsPrerequisiteIsAlsoThere() {
        Enemy enemy = enemyInBattle();
        StateBuff epiphany = new StateBuff("char:1307/epiphany", 2);
        epiphany.setCountsAs(List.of("dot:bleed", "dot:burn", "dot:shock"), "char:1307/arcana");
        enemy.getBuffManager().addBuff(epiphany);

        Assertions.assertFalse(enemy.getBuffManager().hasState("dot:bleed"),
                "「当敌方目标处于【char:1307/arcana】状态时」 - without the prerequisite the alias is not granted");

        enemy.getBuffManager().addBuff(new StateBuff("char:1307/arcana", 2));
        Assertions.assertTrue(enemy.getBuffManager().hasState("dot:bleed"), "with char:1307/arcana on the same unit it is");
        Assertions.assertTrue(enemy.getBuffManager().hasState("dot:shock"), "every name in the list, not just the first");

        enemy.getBuffManager().removeState("char:1307/arcana");
        Assertions.assertFalse(enemy.getBuffManager().hasState("dot:bleed"), "and it goes when the prerequisite goes");
    }

    /**
     * A pair of aliases that require each other is data the engine must answer "no" to, not hang on: the depth cap is
     * the only thing standing between a typo and a stack overflow, so it is pinned here.
     */
    @Test
    public void aCyclicPairOfAliasesAnswersNoInsteadOfRecursingForever() {
        Enemy enemy = enemyInBattle();
        // 甲 counts as 乙 while 丁 is there; 丙 counts as 丁 while 乙 is there. Neither prerequisite has a base case,
        // so the only possible answers are "no" (the depth cap) or "never returns".
        StateBuff first = new StateBuff("甲", 2);
        first.setCountsAs(List.of("乙"), "丁");
        StateBuff second = new StateBuff("丙", 2);
        second.setCountsAs(List.of("丁"), "乙");
        enemy.getBuffManager().addBuff(first);
        enemy.getBuffManager().addBuff(second);

        Assertions.assertTrue(enemy.getBuffManager().hasState("甲"), "each still answers to its own name");
        Assertions.assertFalse(enemy.getBuffManager().hasState("乙"),
                "a prerequisite that can only be reached through the alias itself is not satisfied");
        Assertions.assertFalse(enemy.getBuffManager().hasState("戊"), "and nothing invents a third name");
    }

    private static Enemy enemyInBattle() {
        Character ally = CharacterFactory.create(1002, 80);
        Enemy enemy = BattleFixture.enemy(BattleFixture.MONSTER_ID, BattleFixture.MONSTER_LEVEL);
        Battle battle = new Battle(List.of(ally), List.of(enemy), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        return enemy;
    }
}
