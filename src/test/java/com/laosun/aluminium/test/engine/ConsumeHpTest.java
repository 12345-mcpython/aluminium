package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1205 Blade's skill: "消耗等同于刃生命上限 30% 的生命值，进入[地狱变]状态…若当前生命值不足…降低至 1 点".
 *
 * <p>Judged by the HP difference -- a quantity nothing else in the fixture touches -- and the floor is reached by damaging her first.
 */
public class ConsumeHpTest {
    private static final int WEARER = 1205;
    private static final int LEVEL = 80;

    @Test
    public void theSkillSpendsThirtyPercentOfMaxHp() {
        Character unit = CharacterFactory.create(WEARER, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(unit), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        double maxHp = unit.getMaxHp();
        double before = unit.getCurrentHp();
        Assertions.assertEquals(maxHp, before, 1e-6, "precondition: she starts whole");
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.SKILL_CAST, unit, enemy, 0, 0));
        double spent = before - unit.getCurrentHp();
        System.out.println("[1205] maxHp=" + maxHp + " spent=" + spent + " fraction=" + (spent / maxHp));
        Assertions.assertEquals(0.30 * maxHp, spent, 1e-6, "the skill pays 30% of her max HP");
    }

    @Test
    public void theSpendStopsAtOneHp() {
        Character unit = CharacterFactory.create(WEARER, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(unit), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        // Note: `takeDamage` lowers HP WITHOUT announcing a hit. The old loop used `applyDamage` in small steps, which
        // stopped working once the talent's follow-up landed: every fifth hit reaches the charge cap and heals 25% of
        // Max HP, so damaging him down that way reaches an equilibrium instead of the floor. The op under test is
        // `CONSUME_HP`'s floor, so the damage that sets it up must not run the talent.
        unit.takeDamage(unit.getMaxHp() * 0.95);
        double before = unit.getCurrentHp();
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.SKILL_CAST, unit, enemy, 0, 0));
        double after = unit.getCurrentHp();
        System.out.println("[1205] floorTest before=" + before + " after=" + after
                + " beforeFraction=" + (before / unit.getMaxHp()));
        Assertions.assertTrue(before / unit.getMaxHp() < 0.30, "precondition: less HP left than the price");
        Assertions.assertEquals(1.0, after, 1e-6, "the spend stops at 1 HP");
    }
}
