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
 * 1221 Yunli, from her own file (2026-09-29, round 227): the heal's derived-plus-flat amount, and the counter that reaches THE ATTACKER.
 */
public class YunliTest {
    private static final int YUNLI = 1221;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** \u26a0 The heal is 30% of her ATTACK plus a flat 200, and it heals HER. */
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
                "\u300c\u6062\u590d\u7b49\u540c\u4e8e\u4e91\u748330.00%\u653b\u51fb\u529b+200\u7684\u751f\u547d\u503c\u300d: expected " + expected);
    }

    /** \u26a0 The counter reaches the ATTACKER and nobody else: two enemies make that checkable. */
    @Test
    public void theCounterHitsTheAttackerOnly() {
        Character yunli = CharacterFactory.create(YUNLI, LEVEL);
        Enemy attacker = EnemyFactory.create(MONSTER, 90, 1);
        Enemy bystander = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(yunli), List.of(attacker, bystander), fixed());
        battle.startBattle();
        double attackerBefore = attacker.getCurrentHp();
        double bystanderBefore = bystander.getCurrentHp();
        double energyBefore = yunli.getCurrentEnergy();

        battle.fireTriggers(TriggerEvent.TAKING_HIT, attacker, yunli, 0, 100);

        Assertions.assertEquals(15.0, yunli.getCurrentEnergy() - energyBefore, 1e-6,
                "\u300c\u989d\u5916\u6062\u590d15\u70b9\u80fd\u91cf\u300d");
        Assertions.assertTrue(attackerBefore - attacker.getCurrentHp() > 0,
                "\u300c\u7acb\u5373\u5411\u653b\u51fb\u8005\u53d1\u8d77\u53cd\u51fb\u300d -- the ATTACKER takes it");
        Assertions.assertEquals(bystanderBefore, bystander.getCurrentHp(), 1e-9,
                "and a bystander does not: `target: attacker` is what makes this exact");
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
