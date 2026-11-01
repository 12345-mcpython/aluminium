package com.laosun.aluminium.test.content.characters.argenti;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
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
 * 1302 银枝's enhanced Ultimate (data slot 14): installed into the ULTRA slot only while the energy is full, so the same cast
 * deals the enhanced numbers instead of the ordinary ones.
 */
public class ArgentiEnhancedUltTest {

    private static final int ARGENTI = 1302;
    private static final int LEVEL = 80;

    @Test
    public void aFullBankGivesTheEnhancedUltimate() {
        double ordinary = ultimateDamage(false);
        double enhanced = ultimateDamage(true);
        System.out.println("[1302] ordinary=" + ordinary + " enhanced=" + enhanced + " ratio=" + (enhanced / ordinary));
        Assertions.assertTrue(enhanced > ordinary * 1.5, "「消耗180点能量」的强化行给出的伤害显著更高");
    }

    private static double ultimateDamage(boolean fillEnergy) {
        Character argenti = CharacterFactory.create(ARGENTI, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(argenti, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        if (fillEnergy) {
            argenti.setCurrentEnergy(argenti.getMaxEnergy());
            battle.fire(TriggerPayload.Simple.of(TriggerEvent.ENERGY_GAINED, argenti, argenti, 0, 0));
        }
        double before = enemy.getCurrentHp();
        battle.castImmediate(argenti.getSkills().get(SkillType.ULTRA), argenti, List.of(enemy));
        return before - enemy.getCurrentHp();
    }
}