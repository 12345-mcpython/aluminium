package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

public class ScratchStackCapTest {
    @Test
    public void fire() {
        Character jiaoqiu = CharacterFactory.create(1218, 80);
        Character ally = CharacterFactory.create(1210, 80);
        Enemy enemy = EnemyFactory.create(1002011, 90, 1);
        Battle battle = new Battle(List.of(jiaoqiu, ally), List.of(enemy), new Random(0));
        battle.startBattle();
        System.out.println("[loop] --- firing ALLY_ATTACK ---");
        battle.fireTriggers(TriggerEvent.ALLY_ATTACK, jiaoqiu, enemy, 1, 1000);
        System.out.println("[loop] after: " + enemy.getBuffManager().stacksOf("\u70ec\u714e")
                + " stackBuffs=" + enemy.getBuffManager().countBuffs(
                        com.laosun.aluminium.models.buff.StackBuff.class));
    }
}
