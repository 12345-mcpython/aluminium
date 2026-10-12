package com.laosun.aluminium.test.content.characters.pearl;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1503 真珠's trace 艺术壁垒: 「进入战斗和施放终结技后，若【美学底本】为「欢愉」命途角色，使<b>其下一次</b>施放终结技时，<b>为真珠</b>固定恢复
 * <b>90</b> 点能量。该效果<b>无法叠加</b>。」
 *
 * <p><b>What was missing.</b> The file applied the marker to the Elation archetype, but as {@code permanent: true} - while the
 * sentence says 「下一次」 - and the payment itself was neither written nor registered in that rule's note (only the
 * battle-start half was). So this case pins the whole sentence: the marker arrives, a Skill does not consume it, the
 * holder's NEXT ultimate pays 真珠 exactly 90 and consumes it, a second ultimate pays nothing, and a non-Elation archetype
 * gets no marker at all.
 *
 * <p>⚠ 「为真珠」 is the rule's OWN owner, and 「下一次」 is the marker's lifetime ({@code until: next_ultimate}, read off the
 * marker's owner - the ally). Both are measured here rather than assumed.
 */
public class PearlArtBarrierTest {
    private static final int PEARL = 1503;
    private static final int ELATION_ALLY = 1505;      // 「欢愉」命途: the barrier applies
    private static final int OTHER_ALLY = 1002;        // not that path: it must not
    private static final String BARRIER = "艺术壁垒";
    private static final int ENERGY = 90;

    private Character pearl;
    private Character ally;
    private Enemy enemy;
    private Battle battle;

    /**
     * The whole sentence: marker, a Skill that leaves it, the next ultimate paying 90 and consuming it.
     */
    @Test
    public void theBarrierPaysPearlOnTheHoldersNextUltimate() {
        build(ELATION_ALLY);
        pearlUltimateOnTheArchetype();
        Assertions.assertTrue(ally.getBuffManager().hasState(BARRIER),
                "「使其…」: the Elation archetype carries the marker");

        pearl.setCurrentEnergy(0);
        battle.castImmediate(ally.getSkills().get(SkillType.SKILL), ally, List.of(enemy));
        battle.processRequests();
        Assertions.assertEquals(0, pearlEnergy(), "a Skill is not a 终结技: nothing is paid");
        Assertions.assertTrue(ally.getBuffManager().hasState(BARRIER), "and the marker waits for the REAL ultimate");

        double paid = allyUltimate();
        System.out.println("[1503] the ally's next ultimate paid " + paid + " energy ; the marker is now "
                + ally.getBuffManager().hasState(BARRIER));
        Assertions.assertEquals(ENERGY, paid, "「下一次施放终结技时，为真珠固定恢复 90 点能量」");
        Assertions.assertFalse(ally.getBuffManager().hasState(BARRIER),
                "「下一次」 is a lifetime: that ultimate consumed the marker");

        pearl.setCurrentEnergy(0);
        allyUltimate();
        Assertions.assertEquals(0, pearlEnergy(), "and the next one pays nothing: the effect happened once");
    }

    /**
     * 「若【美学底本】为「欢愉」命途角色」: somebody off that path never carries it.
     */
    @Test
    public void aNonElationArchetypeGetsNothing() {
        build(OTHER_ALLY);
        pearlUltimateOnTheArchetype();
        Assertions.assertFalse(ally.getBuffManager().hasState(BARRIER),
                "the gate is the target's PATH, and this one is not 欢愉");

        pearl.setCurrentEnergy(0);
        double paid = allyUltimate();
        System.out.println("[1503] a non-Elation archetype's ultimate paid " + paid);
        Assertions.assertEquals(0, paid, "no marker, no payment");
    }

    /**
     * 「该效果无法叠加」: two of her ultimates still leave ONE marker, and that ultimate pays once.
     *
     * <p>⚠ Measured while writing this: {@code stacksOf} does NOT count a plain state (it returned 0 while
     * {@code hasState} was true), so the claim is asserted through the payment - one marker pays 90, not 180 - rather
     * than by counting buffs with an accessor that only counts stack-buffs.
     */
    @Test
    public void theBarrierDoesNotStackOrPayTwice() {
        build(ELATION_ALLY);
        pearlUltimateOnTheArchetype();
        pearlUltimateOnTheArchetype();
        System.out.println("[1503] after two of her ultimates the marker is present: "
                + ally.getBuffManager().hasState(BARRIER));
        Assertions.assertTrue(ally.getBuffManager().hasState(BARRIER), "「使其…」 still holds after the second one");

        pearl.setCurrentEnergy(0);
        double paid = allyUltimate();
        System.out.println("[1503] and that ultimate paid " + paid + " (not twice the 90)");
        Assertions.assertEquals(ENERGY, paid, "one marker pays once: 「该效果无法叠加」");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private void build(int allyCid) {
        pearl = CharacterFactory.create(PEARL, 80);
        ally = CharacterFactory.create(allyCid, 80);
        enemy = BattleFixture.monster();
        battle = new Battle(List.of(pearl, ally), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
    }

    /**
     * Her own ultimate, aimed at the archetype: the rule that hands out the marker.
     */
    private void pearlUltimateOnTheArchetype() {
        pearl.setCurrentEnergy(pearl.getMaxEnergy());
        Assertions.assertTrue(battle.castUltra(pearl, List.of(ally)), "precondition: her ultimate is cast");
        battle.processRequests();
    }

    /**
     * The ally's own ultimate; answers with how much energy 真珠 gained from it.
     */
    private double allyUltimate() {
        double before = pearlEnergy();
        ally.setCurrentEnergy(ally.getMaxEnergy());
        Assertions.assertTrue(battle.castUltra(ally, List.of(enemy)), "precondition: the ally's ultimate is cast");
        battle.processRequests();
        return pearlEnergy() - before;
    }

    private double pearlEnergy() {
        return pearl.getCurrentEnergy();
    }
}
