package com.laosun.aluminium.test;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;

/** Diagnostics for the distinct-ally counter (2026-10-02). */
public class ProbeDistinctDiagTest {
    private static final String FUTURE = "\u672a\u6765";
    private static final String MARK = "\u4ece\u8fd9\u4f4d\u961f\u53cb\u5904\u5f97\u5230\u8fc7\u3010\u8ffd\u5fc6\u3011";
    private static final String COUNTER = "\u5fc6\u7075\u6280\u7684\u989d\u5916\u4e00\u51fb";

    @Test
    public void printTheState() {
        Character cyrene = CharacterFactory.create(1415, 80);
        Character one = CharacterFactory.create(1405, 80);
        Character two = CharacterFactory.create(1403, 80);
        Battle battle = new Battle(List.of(cyrene, one, two),
                List.of(EnemyFactory.create(1002011, 100, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        System.out.println("[diag] at battle start: ally1 未来=" + battle.characters.get(1).getBuffManager().hasState(FUTURE)
                + " ally2 未来=" + battle.characters.get(2).getBuffManager().hasState(FUTURE));
        var dragon = battle.summonMemosprite(battle.characters.get(0));
        battle.processRequests();
        System.out.println("[diag] after summoning: counter=" + (dragon.getResources().has(COUNTER) ? dragon.getResources().value(COUNTER) : -1));
        battle.currentMove = new Signal(battle.characters.get(1));
        battle.beforeMove();
        battle.processRequests();
        System.out.println("[diag] after ally1 acts: ally1 mark=" + battle.characters.get(1).getBuffManager().hasState(MARK)
                + " counter=" + (dragon.getResources().has(COUNTER) ? dragon.getResources().value(COUNTER) : -1)
                + " ally1 追忆-giver=" + battle.characters.get(0).getResources().value("\u8ffd\u5fc6"));
        Assertions.assertTrue(true);
    }
}
