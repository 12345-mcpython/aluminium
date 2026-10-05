package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1218 Jiaoqiu, from his own file (2026-09-29, round 201): [烬煨]'s capped stacks and the Burn equivalence, both of whose numbers the document states.
 *
 * <p>The cap is tested by EXCEEDING it (round 192's lesson): seven applications must still read five. And `hasState` resolves the engine's DOT states, so the Burn the
 * talent's second sentence creates is askable - which is why it is asserted rather than assumed.
 */
public class JiaoqiuTest {
    private static final int JIAOQIU = 1218;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** Note: Seven applications, five layers (the document's cap), and the Burn state alongside it. */
    @Test
    public void theStacksStopAtFiveAndTheBurnIsReal() {
        Character jiaoqiu = CharacterFactory.create(JIAOQIU, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(jiaoqiu), List.of(enemy), fixed());
        battle.startBattle();

        for (int i = 0; i < 7; i++) {
            battle.fireTriggers(TriggerEvent.ALLY_ATTACK, jiaoqiu, enemy, 0, 0);
        }

        Assertions.assertEquals(5, jiaoqiu.getBuffManager() == null ? enemy.getBuffManager().stacksOf("烬煨") : enemy.getBuffManager().stacksOf("烬煨"),
                "「【烬煨】最多叠加5层」 -- seven applications must still read five");
        Assertions.assertTrue(enemy.getBuffManager().hasState("灼烧"),
                "「也会被视为同时陷入了灼烧状态」 -- a Fire DotBuff IS 灼烧 by the engine's own translation");
        // and this rule applies a Fire DoT. Registered as an open question rather than asserted falsely or deleted silently.
    }

    /** Note: The technique's opening: the AoE and a stack, only when the technique was declared. */
    @Test
    public void theTechniqueHitsAndStacksOnlyWhenDeclared() {
        Character jiaoqiu = CharacterFactory.create(JIAOQIU, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(jiaoqiu), List.of(enemy), fixed());
        battle.markTechniqueUsed(jiaoqiu);
        double before = enemy.getCurrentHp();

        battle.startBattle();

        Assertions.assertTrue(before - enemy.getCurrentHp() > 0,
                "「对敌方全体造成等同于椒丘100%攻击力的火属性伤害」");
        Assertions.assertEquals(1, enemy.getBuffManager().stacksOf("烬煨"), "并施加1层【烬煨】");

        Character plain = CharacterFactory.create(JIAOQIU, LEVEL);
        Enemy enemy2 = EnemyFactory.create(MONSTER, 90, 1);
        Battle plainBattle = new Battle(List.of(plain), List.of(enemy2), fixed());
        double untouched = enemy2.getCurrentHp();
        plainBattle.startBattle();
        Assertions.assertEquals(untouched, enemy2.getCurrentHp(), 1e-9, "「使用秘技后」 -- undeclared, so nothing");
    }

    private static Random fixed() {
        return new Random() {
            @Override
            public double nextDouble() {
                return 0.0;   // a debuff lands when nextDouble() < chance; 1.0 would RESIST every one (round 202)
            }
        };
    }
}
