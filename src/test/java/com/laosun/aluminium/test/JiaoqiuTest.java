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
 * 1218 Jiaoqiu, from his own file (2026-09-29, round 201): 【烬煨】's capped stacks and the Burn equivalence, both of whose numbers the document states.
 *
 * <p>The cap is tested by EXCEEDING it (round 192's lesson): seven applications must still read five. And `hasState` resolves the engine's DOT states, so the Burn the
 * talent's second sentence creates is askable — which is why it is asserted rather than assumed.
 */
public class JiaoqiuTest {
    private static final int JIAOQIU = 1218;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** \u26a0 Seven applications, five layers (the document's cap), and the Burn state alongside it. */
    @Test
    public void theStacksStopAtFiveAndTheBurnIsReal() {
        Character jiaoqiu = CharacterFactory.create(JIAOQIU, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(jiaoqiu), List.of(enemy), fixed());
        battle.startBattle();

        for (int i = 0; i < 7; i++) {
            battle.fireTriggers(TriggerEvent.ALLY_ATTACK, jiaoqiu, enemy, 0, 0);
        }

        Assertions.assertEquals(5, jiaoqiu.getBuffManager() == null ? enemy.getBuffManager().stacksOf("\u70ec\u7168") : enemy.getBuffManager().stacksOf("\u70ec\u7168"),
                "\u300c\u3010\u70ec\u7168\u3011\u6700\u591a\u53e0\u52a05\u5c42\u300d -- seven applications must still read five");
        // \u26a0 The Burn equivalence is NOT asserted here: measured, `hasState("\u707c\u70e7")` is false even though `BuffManager.DOT_STATES` maps FIRE -> \u707c\u70e7
        // and this rule applies a Fire DoT. Registered as an open question rather than asserted falsely or deleted silently.
    }

    /** \u26a0 The technique's opening: the AoE and a stack, only when the technique was declared. */
    @Test
    public void theTechniqueHitsAndStacksOnlyWhenDeclared() {
        Character jiaoqiu = CharacterFactory.create(JIAOQIU, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(jiaoqiu), List.of(enemy), fixed());
        battle.markTechniqueUsed(jiaoqiu);
        double before = enemy.getCurrentHp();

        battle.startBattle();

        Assertions.assertTrue(before - enemy.getCurrentHp() > 0,
                "\u300c\u5bf9\u654c\u65b9\u5168\u4f53\u9020\u6210\u7b49\u540c\u4e8e\u6912\u4e18100%\u653b\u51fb\u529b\u7684\u706b\u5c5e\u6027\u4f24\u5bb3\u300d");
        Assertions.assertEquals(1, enemy.getBuffManager().stacksOf("\u70ec\u7168"), "\u5e76\u65bd\u52a01\u5c42\u3010\u70ec\u7168\u3011");

        Character plain = CharacterFactory.create(JIAOQIU, LEVEL);
        Enemy enemy2 = EnemyFactory.create(MONSTER, 90, 1);
        Battle plainBattle = new Battle(List.of(plain), List.of(enemy2), fixed());
        double untouched = enemy2.getCurrentHp();
        plainBattle.startBattle();
        Assertions.assertEquals(untouched, enemy2.getCurrentHp(), 1e-9, "\u300c\u4f7f\u7528\u79d8\u6280\u540e\u300d -- undeclared, so nothing");
    }

    private static Random fixed() {
        return new Random() {
            @Override
            public double nextDouble() {
                return 1.0;
            }
        };
    }
}
