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
 * 1513 Aventurine - Waveflair, from his own file (2026-09-29, round 218): [热意], the one resource in this kit the document caps.
 */
public class AventurineWaveflairTest {
    private static final int AVENTURINE = 1513;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** Note: One Fervor per TEAMMATE's attack, and the document's cap of 30 enforced by exceeding it. */
    @Test
    public void teammateAttacksFeedFervorUpToThirty() {
        Character aventurine = CharacterFactory.create(AVENTURINE, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(aventurine, ally), List.of(enemy), fixed());
        battle.startBattle();

        Assertions.assertEquals(0, fervorOf(aventurine), "no initial value is stated, so it starts at 0");
        battle.fireTriggers(TriggerEvent.ALLY_ATTACK, ally, enemy, 0, 0);
        Assertions.assertEquals(1, fervorOf(aventurine),
                "「队友施放攻击后，砂金•戏浪获得1点【热意】」");

        battle.fireTriggers(TriggerEvent.ALLY_ATTACK, aventurine, enemy, 0, 0);
        Assertions.assertEquals(1, fervorOf(aventurine),
                "the gate is `actor is_other_ally`: her OWN attack adds nothing");

        for (int i = 0; i < 40; i++) {
            battle.fireTriggers(TriggerEvent.ALLY_ATTACK, ally, enemy, 0, 0);
        }
        Assertions.assertEquals(30, fervorOf(aventurine),
                "「【热意】上限为30点」 -- forty-one firings must still read thirty");
    }

    /** Note: The Ultimate: 8 Fervor and 30% more SPEED for the document's four turns. */
    @Test
    public void theUltimateAddsFervorAndSpeed() {
        Character aventurine = CharacterFactory.create(AVENTURINE, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(aventurine, ally), List.of(enemy), fixed());
        battle.startBattle();
        double before = aventurine.getAttribute(AttributeType.SPEED).get();
        double base = aventurine.getAttribute(AttributeType.SPEED).baseValue();
        int fervor = fervorOf(aventurine);

        battle.fireTriggers(TriggerEvent.ULT_CAST, aventurine, enemy, 0, 0);

        Assertions.assertEquals(fervor + 8, fervorOf(aventurine), "「获得8点【热意】」");
        Assertions.assertEquals(base * 0.3, aventurine.getAttribute(AttributeType.SPEED).get() - before, base * 0.02,
                "「使自身速度提高30%，持续4回合」 of the BASE speed: base " + base);
    }

    /** The declared resource's value, read through the combatant's own manager. */
    private static int fervorOf(Character unit) {
        return unit.getResources().get("热意").getValue();
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
