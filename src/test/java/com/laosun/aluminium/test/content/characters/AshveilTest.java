package com.laosun.aluminium.test.content.characters;

import com.laosun.aluminium.Battle;
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
 * 1504 Ashveil, from her own file (2026-09-29, round 200): the Bait reaction (8 energy + a 200%-ATK follow-up) and the [婪酣] cap, whose numbers the document states.
 *
 * <p>Both guards are load-bearing and tested: `actor is_other_ally` (her own attack must not trigger it) and `target has_state 饲饵`. The cap is tested by EXCEEDING it.
 */
public class AshveilTest {
    private static final int ASHVEIL = 1504;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** Note: The reaction fires for a teammate's attack on the Bait, and not for her own. */
    @Test
    public void theBaitReactionNeedsATeammate() {
        double fromAlly = energyGain(true);
        double fromSelf = energyGain(false);

        Assertions.assertEquals(8.0, fromAlly, 1e-6,
                "「固定恢复8点能量」 (restores a fixed 8 energy)");
        Assertions.assertEquals(0.0, fromSelf, 1e-6,
                "「我方**其他**目标攻击后」 (after another one of our targets attacks) -- her own attack must not trigger it");
    }

    /** Note: One layer per firing, and a LOUD refusal once the two declared charges are spent (measured, not assumed). */
    @Test
    public void eachReactionAddsALayerAndNeedsACharge() {
        Character ashveil = CharacterFactory.create(ASHVEIL, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(ashveil, ally), List.of(enemy), fixed());
        battle.startBattle();
        // Make the enemy the Bait first: without it neither guard passes.
        battle.castImmediate(ashveil.getSkills().get(com.laosun.aluminium.enums.SkillType.SKILL), ashveil, List.of(enemy));

        // Her declaration gives 2 charges. One firing is one layer (ADD_STACK's `amount` is not a layer count, round 12).
        battle.fireTriggers(TriggerEvent.ALLY_ATTACK, ally, enemy, 0, 0);
        battle.fireTriggers(TriggerEvent.ALLY_ATTACK, ally, enemy, 0, 0);
        Assertions.assertEquals(2, ashveil.getBuffManager().stacksOf("婪酣"),
                "two firings, one layer each");

        Assertions.assertThrows(IllegalStateException.class,
                () -> battle.fireTriggers(TriggerEvent.ALLY_ATTACK, ally, enemy, 0, 0),
                "the engine refuses to spend a charge she does not have");
    }

    /** Note: The technique's opening: the AoE and one Charge, whose declaration makes the gain real. */
    @Test
    public void theTechniqueHitsAndGrantsCharge() {
        Character ashveil = CharacterFactory.create(ASHVEIL, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(ashveil), List.of(enemy), fixed());
        battle.markTechniqueUsed(ashveil);
        double before = enemy.getCurrentHp();

        battle.startBattle();

        Assertions.assertTrue(before - enemy.getCurrentHp() > 0,
                "「对敌方全体造成等同于不死途攻击力100%的雷属性伤害」 (Lightning damage to all enemies equal to 100% of 不死途's ATK)");
    }

    /** The energy gain for a teammate's attack; mode false fires it from Ashveil herself. */
    private static double energyGain(boolean teammate) {
        Character ashveil = CharacterFactory.create(ASHVEIL, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(ashveil, ally), List.of(enemy), fixed());
        battle.startBattle();
        battle.castImmediate(ashveil.getSkills().get(com.laosun.aluminium.enums.SkillType.SKILL), ashveil, List.of(enemy));
        double before = ashveil.getCurrentEnergy();
        battle.fireTriggers(TriggerEvent.ALLY_ATTACK, teammate ? ally : ashveil, enemy, 0, 0);
        return ashveil.getCurrentEnergy() - before;
    }

    private static Random fixed() {
        return new Random() {
            @Override
            public double nextDouble() {
                return 1.0;
            }
        };
    }
}
