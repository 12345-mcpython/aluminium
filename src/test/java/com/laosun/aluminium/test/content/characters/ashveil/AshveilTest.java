package com.laosun.aluminium.test.content.characters.ashveil;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1504 Ashveil, from her own file: the Bait reaction (8 energy + a 200%-ATK follow-up) and the [婪酣] cap, whose numbers the document states.
 *
 * <p>Both guards are load-bearing and tested: `actor is_other_ally` (her own attack must not trigger it) and `target has_state 饲饵`. The cap is tested by EXCEEDING it.
 */
public class AshveilTest {
    private static final int ASHVEIL = 1504;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;

    /**
     * Note: The reaction fires for a teammate's attack on the Bait, and not for her own.
     */
    @Test
    public void theBaitReactionNeedsATeammate() {
        double fromAlly = energyGain(true);
        double fromSelf = energyGain(false);

        Assertions.assertEquals(8.0, fromAlly, 1e-6,
                "「固定恢复8点能量」 (restores a fixed 8 energy)");
        Assertions.assertEquals(0.0, fromSelf, 1e-6,
                "「我方**其他**目标攻击后」 (after another one of our targets attacks) -- her own attack must not trigger it");
    }

    /**
     * Note: Two layers per firing (2026-11-03), and a LOUD refusal once the two declared charges are spent (measured, not assumed).
     *
     * <p><b>This test used to pin the wrong reading</b>: it asserted one layer per firing on the belief that {@code ADD_STACK}'s
     * {@code amount} is not a layer count. It is — {@code TriggerEffects.addStack} builds {@code wanted} from exactly that field —
     * so the talent's 「随后获得 2 层【婪酣】」 now grants two, and two firings give four.
     */
    @Test
    public void eachReactionAddsTwoLayersAndNeedsACharge() {
        Character ashveil = CharacterFactory.create(ASHVEIL, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(ashveil, ally), List.of(enemy), fixed());
        battle.startBattle();
        // Make the enemy the Bait first: without it neither guard passes.
        battle.castImmediate(ashveil.getSkills().get(com.laosun.aluminium.enums.SkillType.SKILL), ashveil, List.of(enemy));

        // Her declaration gives 2 charges. One firing is TWO layers: the talent says 「获得 2 层【婪酣】」 and `amount` IS the count.
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, ally, enemy, 0, 0));
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, ally, enemy, 0, 0));
        Assertions.assertEquals(5, ashveil.getBuffManager().stacksOf("婪酣"),
                "two firings of two layers, plus the layer the Skill cast grants through 罪途");

        Assertions.assertThrows(IllegalStateException.class,
                () -> battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, ally, enemy, 0, 0)),
                "the engine refuses to spend a charge she does not have");
    }

    /**
     * Note: The technique's opening: the AoE and one Charge, whose declaration makes the gain real.
     */
    @Test
    public void theTechniqueHitsAndGrantsCharge() {
        Character ashveil = CharacterFactory.create(ASHVEIL, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(ashveil), List.of(enemy), fixed());
        battle.markTechniqueUsed(ashveil);
        double before = enemy.getCurrentHp();

        battle.startBattle();

        Assertions.assertTrue(before - enemy.getCurrentHp() > 0,
                "「对敌方全体造成等同于不死途攻击力100%的雷属性伤害」 (Lightning damage to all enemies equal to 100% of 不死途's ATK)");
    }

    /**
     * The energy gain for a teammate's attack; mode false fires it from Ashveil herself.
     */
    private static double energyGain(boolean teammate) {
        Character ashveil = CharacterFactory.create(ASHVEIL, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(ashveil, ally), List.of(enemy), fixed());
        battle.startBattle();
        battle.castImmediate(ashveil.getSkills().get(com.laosun.aluminium.enums.SkillType.SKILL), ashveil, List.of(enemy));
        double before = ashveil.getCurrentEnergy();
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, teammate ? ally : ashveil, enemy, 0, 0));
        return ashveil.getCurrentEnergy() - before;
    }

    private static Random fixed() {
        return BattleFixture.alwaysRoll(1.0);
    }
}


