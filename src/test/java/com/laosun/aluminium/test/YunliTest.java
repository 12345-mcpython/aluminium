package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
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
 * 1221 Yunli, from her own file (2026-09-29, round 22): the heal's derived-plus-flat amount, and the counter that reaches THE ATTACKER.
 */
public class YunliTest {
    private static final int YUNLI = 1221;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** Note: The heal is 30% of her ATTACK plus a flat 200, and it heals HER. */
    @Test
    public void theSkillHealsHerByASharePlusAFlatAmount() {
        Character yunli = CharacterFactory.create(YUNLI, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(yunli), List.of(enemy), fixed());
        battle.startBattle();
        // Spend some HP first, so the heal has room to show.
        battle.applyDamage(yunli, new com.laosun.aluminium.models.Damage(enemy, yunli,
                com.laosun.aluminium.enums.DamageElement.PHYSICAL, com.laosun.aluminium.enums.DamageType.NORMAL, yunli.getMaxHp() * 0.5));
        double before = yunli.getCurrentHp();
        double expected = yunli.getAttribute(AttributeType.ATTACK).get() * 0.3 + 200;

        battle.fireTriggers(TriggerEvent.SKILL_CAST, yunli, enemy, 0, 0);

        Assertions.assertEquals(expected, yunli.getCurrentHp() - before, expected * 0.02,
                "「恢复等同于云璃30.00%攻击力+200的生命值」: expected " + expected);
    }

    /** Note: The counter reaches the ATTACKER and nobody else, and its 120% is pinned against a hand-built 240% reference. */
    @Test
    public void theCounterHitsTheAttackerOnly() {
        double shipped = counterLoss(true);
        double reference = counterLoss(false);

        Character yunli = CharacterFactory.create(YUNLI, LEVEL);
        Enemy attacker = EnemyFactory.create(MONSTER, 90, 1);
        Enemy bystander = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(yunli), List.of(attacker, bystander), fixed());
        battle.startBattle();
        double bystanderBefore = bystander.getCurrentHp();
        double energyBefore = yunli.getCurrentEnergy();

        battle.fireTriggers(TriggerEvent.TAKING_HIT, attacker, yunli, 0, 100);

        Assertions.assertEquals(15.0, yunli.getCurrentEnergy() - energyBefore, 1e-6,
                "「额外恢复15点能量」");
        Assertions.assertEquals(bystanderBefore, bystander.getCurrentHp(), 1e-9,
                "and a bystander does not: `target: attacker` is what makes this exact");
        Assertions.assertTrue(reference > 0, "the reference must land at all");
        Assertions.assertEquals(0.5, shipped / reference, 0.05,
                "120% against a hand-built 240% reference: " + shipped + " vs " + reference
                        + " -- the ratio is what pins the MAGNITUDE (a surviving mutation proved a bare \"> 0\" did not)");
    }

    /** The damage the counter deals to the attacker: the shipped file, or a hand-built 240% reference rule. */
    private static double counterLoss(boolean shipped) {
        Character yunli = CharacterFactory.create(YUNLI, LEVEL);
        if (!shipped) {
            com.laosun.aluminium.beans.EffectSpec effect = new com.laosun.aluminium.beans.EffectSpec();
            TriggerSpecs.set(effect, "op", "DAMAGE");
            TriggerSpecs.set(effect, "scale", "self_attr:ATTACK");
            TriggerSpecs.set(effect, "percent", 2.4);
            TriggerSpecs.set(effect, "element", "Physical");
            TriggerSpecs.set(effect, "target", "attacker");
            yunli.setTriggerTable(new com.laosun.aluminium.models.TriggerTable(YUNLI, List.of(TriggerSpecs.rule(
                    TriggerEvent.TAKING_HIT.name(), List.of("target == self"), effect))));
        }
        Enemy attacker = EnemyFactory.create(MONSTER, 90, 1);
        Enemy bystander = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(yunli), List.of(attacker, bystander), fixed());
        battle.startBattle();
        double before = attacker.getCurrentHp();
        battle.fireTriggers(TriggerEvent.TAKING_HIT, attacker, yunli, 0, 100);
        return before - attacker.getCurrentHp();
    }

    private static Random fixed() {
        return new Random() {
            @Override
            public double nextDouble() {
                return 0.0;
            }
        };
    }
}
