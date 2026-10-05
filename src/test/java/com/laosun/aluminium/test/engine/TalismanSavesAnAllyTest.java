package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.Skill;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 121: "while Huohuo (藿藿) holds [禳命], if one of our targets takes a lethal blow ... immediately restore HP equal to 50% of its own Max HP.
 * This effect can trigger 2 times per battle".
 *
 * <p>ONE VARIABLE per test: the eidolon rank, or the NUMBER OF LETHAL BLOWS. Same party, same skill (which is what puts
 * [禳命] ("Divine Provision") on her), same blow.
 */
public class TalismanSavesAnAllyTest {
    private static final int HUOHUO = 1217;
    private static final int ALLY = 1002;
    private static final int MONSTER = 1002011;
    private static final String STATE = "禳命";

    /** At E2 the ally survives, at HALF OF ITS OWN Max HP. */
    @Test
    public void atEidolonTwoTheAllySurvives() {
        double[] result = afterLethalBlows(2, 1);
        Assertions.assertTrue(result[0] > 0, "\"will not enter the unable-to-fight state\" (hp " + result[0] + ")");
        Assertions.assertEquals(result[1] * 0.50, result[0], result[1] * 0.01,
                "\"restore equal to 50% of **its own** Max HP\" -- the VICTIM's own, not the healer's");
    }

    /** Note: Below E2 the ally falls. */
    @Test
    public void belowEidolonTwoTheAllyFalls() {
        Assertions.assertTrue(afterLethalBlows(0, 1)[0] <= 0, "only Eidolon 2 has this clause");
    }

    /**
     * "this effect can trigger 2 times per battle": the first two blows are answered, the third is not.
     *
     * <p>The count is a shipped spelling, not a new capability: a counter is `ADD_STACK` plus a `self_stacks:` condition
     * (sample: 1111's [斗志] ("Fighting Will")). Note: The two answered blows each leave her ALLY at half of ITS OWN Max HP, so the third
     * blow is lethal again -- the assertion is about the count, not about a first-blow-only effect.
     */
    @Test
    public void theLimitIsTwoBlowsInOneBattle() {
        double[] one = afterLethalBlows(2, 1);
        double[] two = afterLethalBlows(2, 2);
        double[] three = afterLethalBlows(2, 3);
        Assertions.assertTrue(one[0] > 0, "the first blow is answered");
        Assertions.assertTrue(two[0] > 0, "the second blow is still answered (\"can trigger 2 times\")");
        Assertions.assertTrue(three[0] <= 0,
                "the third is **no longer** answered (the cap of \"can trigger 2 times\")");
    }

    // ==================================================================

    /** { the ally's HP after the blows, the ally's Max HP }. */
    private static double[] afterLethalBlows(int eidolon, int blows) {
        Character her = CharacterFactory.create(HUOHUO, 80, false, null, null, eidolon);
        Character ally = CharacterFactory.create(ALLY, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(her, ally),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        // Her skill is what puts [禳命] ("Divine Provision") on her (2 turns, ticking on her own turns).
        Skill skill = her.getSkills().get(SkillType.SKILL);
        Assertions.assertNotNull(skill, "precondition: she has a skill");
        SkillExecutor.execute(battle, skill, her, List.of(battle.enemies.getFirst()));
        battle.processRequests();
        Assertions.assertTrue(her.getBuffManager().hasState(STATE), "precondition: [禳命] (\"Divine Provision\") is on her");

        for (int blow = 0; blow < blows; blow++) {
            if (ally.isDeath()) {
                break;
            }
            battle.applyTrueDamage(battle.enemies.getFirst(), ally, DamageElement.ICE, ally.getCurrentHp() * 2.0);
            battle.processRequests();
        }
        return new double[]{ally.isDeath() ? 0 : ally.getCurrentHp(), ally.getMaxHp()};
    }
}
