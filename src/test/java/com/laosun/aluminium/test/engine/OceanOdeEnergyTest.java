package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1415's memosprite skill 22 "献予'海洋'之诗": the mark, and the energy it pays (2026-10-02).
 *
 * <p>"对海瑟音施放时，使海瑟音获得[暖流]。海瑟音施放攻击后消耗[暖流]为自身恢复 #4 点能量。"
 *
 * <p>Two readings: the mark lands when the ode is cast at her, and ONE attack spends it for exactly #4 = 60 energy -- the mark must be gone afterwards, so a rule
 * that paid without spending (or spent without paying) cannot pass.
 */
public class OceanOdeEnergyTest {
    private static final int LEVEL = 80;
    private static final int CYRENE = 1415;
    private static final int HYSILENS = 1410;
    private static final int MONSTER = 1002011;
    private static final String MARK = "暖流";

    @Test
    public void theMarkLandsAndOneAttackSpendsItForSixty() {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character hysilens = CharacterFactory.create(HYSILENS, LEVEL);
        Battle battle = new Battle(List.of(cyrene, hysilens),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        cyrene = battle.characters.get(0);
        hysilens = battle.characters.get(1);

        var demiurge = battle.summonServant(cyrene);
        var ode = demiurge.skillAt(22);
        Assertions.assertNotNull(ode, "precondition: the memosprite carries slot 22");
        SkillExecutor.execute(battle, ode, demiurge, List.of(hysilens));
        battle.processRequests();
        Assertions.assertTrue(hysilens.getBuffManager().hasState(MARK),
                "「使海瑟音获得【暖流】」-- the mark is on her");

        hysilens.setCurrentEnergy(20);
        double before = hysilens.getCurrentEnergy();
        battle.fireTriggers(TriggerEvent.ALLY_ATTACK, cyrene, battle.enemies.getFirst(), 1, 0);
        battle.processRequests();
        double after = hysilens.getCurrentEnergy();
        boolean stillMarked = hysilens.getBuffManager().hasState(MARK);
        System.out.println("[ocean] energy " + before + " -> " + after + " ; the mark is still on her = " + stillMarked);

        Assertions.assertEquals(before + 60, after, 1e-6,
                "「消耗【暖流】为自身恢复 #4 点能量」-- #4 is 60 at every level");
        Assertions.assertFalse(stillMarked, "and the mark is consumed -- 消耗 means spent, not merely read");
    }
}
