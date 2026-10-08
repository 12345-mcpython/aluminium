package com.laosun.aluminium.test.content.characters;

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
 * 1513 Aventurine - Waveflair, from his own file: [热意], the one resource in this kit the document caps.
 */
public class AventurineWaveflairTest {
    private static final int AVENTURINE = 1513;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /**
     * Note: One Fervor per TEAMMATE's attack, and the document's cap of 30 enforced by exceeding it.
     *
     * <p>⚠ The cap is driven by ONE gain, and the reason is measured: 「【热意】达到10点时…立即施放…」 means each crossing casts
     * and spends ALL of the counter, so forty-one single-point attacks never accumulate - they cycle (41 reads 1). While
     * the milestone clause was still an approximation (it fired on TURN_START and spent nothing) this case could reach
     * the cap by repetition; now the ceiling is what the DECLARATION does to one big gain, which is a different claim
     * and is asserted as such. The cycling itself is pinned in {@code AventurineWaveflairMilestoneTest}.
     */
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
                "「队友施放攻击后，砂金•戏浪获得1点【热意】」 (after an ally casts an attack, Aventurine Waveflair gains 1 point of [热意])");

        battle.fireTriggers(TriggerEvent.ALLY_ATTACK, aventurine, enemy, 0, 0);
        Assertions.assertEquals(1, fervorOf(aventurine),
                "the gate is `actor is_other_ally`: her OWN attack adds nothing");

        for (int i = 0; i < 40; i++) {
            battle.fireTriggers(TriggerEvent.ALLY_ATTACK, ally, enemy, 0, 0);
        }
        Assertions.assertEquals(1, fervorOf(aventurine),
                "forty-one single points cycle: every crossing at ten casts and spends ALL of it, leaving one");

        aventurine.getResources().gain("热意", 41);
        Assertions.assertEquals(30, fervorOf(aventurine),
                "「【热意】上限为30点」 is the DECLARATION's clamp on one gain");
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

        Assertions.assertEquals(fervor + 8, fervorOf(aventurine), "「获得8点【热意】」 (gains 8 points of [热意])");
        Assertions.assertEquals(base * 0.3, aventurine.getAttribute(AttributeType.SPEED).get() - before, base * 0.02,
                "「使自身速度提高30%，持续4回合」 (raises its own SPD by 30% for 4 turns) of the BASE speed: base " + base);
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
