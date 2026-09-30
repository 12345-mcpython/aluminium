package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1205 Blade's skill: 「消耗等同于刃生命上限 30% 的生命值，进入【地狱变】状态…若当前生命值不足…降低至 1 点」.
 *
 * <p>Judged by the HP difference -- a quantity nothing else in the fixture touches -- and the floor is reached by damaging her first.
 */
public class ConsumeHpTest {
    private static final int WEARER = 1205;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    @Test
    public void theSkillSpendsThirtyPercentOfMaxHp() {
        Character unit = CharacterFactory.create(WEARER, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(unit), List.of(enemy), new Random(0));
        battle.startBattle();
        double maxHp = unit.getMaxHp();
        double before = unit.getCurrentHp();
        Assertions.assertEquals(maxHp, before, 1e-6, "precondition: she starts whole");
        battle.fireTriggers(TriggerEvent.SKILL_CAST, unit, enemy, 0, 0);
        double spent = before - unit.getCurrentHp();
        System.out.println("[1205] maxHp=" + maxHp + " spent=" + spent + " fraction=" + (spent / maxHp));
        Assertions.assertEquals(0.30 * maxHp, spent, 1e-6, "the skill pays 30% of her max HP");
    }

    @Test
    public void theSpendStopsAtOneHp() {
        Character unit = CharacterFactory.create(WEARER, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(unit), List.of(enemy), new Random(0));
        battle.startBattle();
        int guard = 0;
        // ⚠ Small steps: `Damage`'s value is a SKILL BASE, not raw damage (round 193/240), so a big one-shot figure kills
        // her and a dead unit answers nothing (0 HP for every reading).
        while (unit.getCurrentHp() / unit.getMaxHp() > 0.05 && guard++ < 400 && !unit.isDeath()) {
            battle.applyDamage(unit, new Damage(enemy, unit, DamageElement.PHYSICAL, DamageType.NORMAL,
                    unit.getMaxHp() * 0.02));
        }
        double before = unit.getCurrentHp();
        battle.fireTriggers(TriggerEvent.SKILL_CAST, unit, enemy, 0, 0);
        double after = unit.getCurrentHp();
        System.out.println("[1205] floorTest before=" + before + " after=" + after
                + " beforeFraction=" + (before / unit.getMaxHp()));
        Assertions.assertTrue(before / unit.getMaxHp() < 0.30, "precondition: less HP left than the price");
        Assertions.assertEquals(1.0, after, 1e-6, "the spend stops at 1 HP");
    }
}
