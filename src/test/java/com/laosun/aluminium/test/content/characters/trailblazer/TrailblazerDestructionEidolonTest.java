package com.laosun.aluminium.test.content.characters.trailblazer;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.buff.StateBuff;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * The Destruction Trailblazer's Eidolon 1 (energy for an Ultimate kill) and Eidolon 2 (a heal when the hit enemy has a
 * Physical weakness), on BOTH of its files.
 */
public class TrailblazerDestructionEidolonTest {

    private static final int LEVEL = 80;

    @Test
    public void anUltimateKillPaysTenEnergy() {
        for (int cid : new int[]{8001, 8002}) {
            Character unit = CharacterFactory.create(cid, LEVEL, true, null, null, 1);
            Enemy enemy = BattleFixture.monster();
            Battle battle = new Battle(new ArrayList<>(List.of(unit, CharacterFactory.create(1001, LEVEL))),
                    new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
            battle.startBattle();
            unit.setCurrentEnergy(0);
            battle.fire(new TriggerPayload.Cast(TriggerEvent.KILL, unit, enemy, 1, 0.0,
                    SkillCategory.ULTRA, 800103, 0, List.of(enemy)));
            double energy = unit.getCurrentEnergy();
            System.out.println("[" + cid + "] energy after the ultimate kill = " + energy);
            Assertions.assertEquals(10, energy, 1e-6, cid + ": 「额外恢复 10 点能量」");

            // A NON-ultimate kill pays nothing.
            unit.setCurrentEnergy(0);
            battle.fire(new TriggerPayload.Cast(TriggerEvent.KILL, unit, enemy, 1, 0.0,
                    SkillCategory.NORMAL, 800101, 0, List.of(enemy)));
            Assertions.assertEquals(0, unit.getCurrentEnergy(), 1e-6, cid + ": only the Ultimate qualifies");
        }
    }

    @Test
    public void theHealNeedsAPhysicalWeakness() {
        for (int cid : new int[]{8001, 8002}) {
            double healed = healedAgainst(cid, true);
            double none = healedAgainst(cid, false);
            System.out.println("[" + cid + "] healed physical=" + healed + " ice=" + none);
            Assertions.assertTrue(healed > 0, cid + ": 「弱点为物理属性」 heals");
            Assertions.assertEquals(0.0, none, 1e-9, cid + ": another weakness pays nothing");
        }
    }

    private static double healedAgainst(int cid, boolean physical) {
        Character unit = CharacterFactory.create(cid, LEVEL, true, null, null, 2);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(unit, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        // Give it exactly one weakness: the clause reads what the enemy is weak to.
        if (physical) {
            enemy.addWeakness(DamageElement.PHYSICAL);
        } else {
            enemy.addWeakness(DamageElement.ICE);
        }
        unit.consumeHp(unit.getMaxHp() * 0.5);
        double before = unit.getCurrentHp();
        battle.castImmediate(unit.getSkills().get(SkillType.COMMON), unit, List.of(enemy));
        return unit.getCurrentHp() - before;
    }
}