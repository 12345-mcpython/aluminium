package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * The map basic attack: 「攻击敌人，进入战斗后削弱敌方目标对应属性韧性」.
 *
 * <p><b>Why it is a declaration and not a simulation.</b> The hit happens on the overworld, which this engine does
 * not model - the same boundary the technique sits on ({@code Battle.markTechniqueUsed}). So the caller states that
 * the map hit landed ({@code Battle.declareMapAttack}) and the battle applies the sentence's only in-battle
 * consequence when it opens: the struck enemy loses the map skill's own toughness against its matching weakness.
 *
 * <p><b>What this file pins.</b> That the numbers come from the character's <b>slot-6</b> skill (element and stance),
 * that the engine's weakness rule still governs it (a non-matching element shaves nothing), that the reduction is
 * visible when the battle opens, that an emptied bar really breaks, and that a declaration for a unit with no map skill
 * is refused instead of silently doing nothing.
 *
 * <p>⚠ <b>What it cannot distinguish, measured:</b> reading slot 1 (the in-battle basic attack) instead of slot 6
 * leaves every case here green - for 1002 the two slots state the same element and the same 30 toughness. The code
 * reads slot 6 because that is the skill the sentence names ("地图攻击", {@code MazeNormal}); the choice is a
 * correctness-of-source decision, not an observable one, and this file says so rather than implying it was pinned.
 */
public class MapAttackTest {
    private static final int DANHENG = 1002;
    private static final int LEVEL = 80;
    /** 1002's slot 6 says 「韧性 30」 for a single target - read from the data, not restated in the engine. */
    private static final double MAP_TOUGHNESS = 30;

    /**
     * The struck enemy opens the battle with 30 of its Wind toughness gone.
     */
    @Test
    public void theMapAttackShavesItsOwnToughnessOnEntry() {
        Fixture f = new Fixture();
        f.declareMapAttack();

        Assertions.assertEquals(f.stanceBefore - MAP_TOUGHNESS, f.enemy.getStance(), 1e-9,
                "「进入战斗后削弱敌方目标对应属性韧性」: " + f.stanceBefore + " -> " + f.enemy.getStance());
    }

    /**
     * Note: The control - without the declaration the battle opens untouched.
     */
    @Test
    public void nothingIsShavedWithoutTheDeclaration() {
        Fixture f = new Fixture();

        Assertions.assertEquals(f.stanceBefore, f.enemy.getStance(), 1e-9);
    }

    /**
     * The engine's one weakness rule still governs it: only a matching element shaves toughness.
     */
    @Test
    public void aNonMatchingElementShavesNothing() {
        Fixture f = new Fixture();
        f.enemy.setStanceWeak(java.util.Set.of(DamageElement.FIRE));
        f.declareMapAttack();

        Assertions.assertEquals(f.stanceBefore, f.enemy.getStance(), 1e-9,
                "the map attack is Wind, the enemy is weak to Fire only");
    }

    /**
     * An emptied bar is a break, and it is settled before the first turn - that is the state the enemy carries in.
     */
    @Test
    public void anEmptiedBarBreaksOnEntry() {
        Fixture f = new Fixture();
        f.enemy.setStance(MAP_TOUGHNESS - 5);
        f.declareMapAttack();

        Assertions.assertTrue(f.enemy.isBroken(), "the bar reached zero, so the enemy really is broken");
        Assertions.assertEquals(0, f.enemy.getStance(), 1e-9);
    }

    /**
     * Note: A unit with no map skill cannot have declared a map hit - refusing is the only reading that is not silent.
     */
    @Test
    public void aDeclarationForAUnitWithoutAMapSkillIsRefused() {
        Fixture f = new Fixture();
        f.battle.declareMapAttack(f.enemy, f.enemy);

        IllegalStateException refused = Assertions.assertThrows(IllegalStateException.class, f.battle::startBattle);
        Assertions.assertTrue(refused.getMessage().contains("map skill"), refused.getMessage());
    }

    /**
     * Note: Both halves are required: a hit with no target has no bar to reduce.
     */
    @Test
    public void aHalfDeclarationIsRefused() {
        Fixture f = new Fixture();

        Assertions.assertThrows(IllegalArgumentException.class,
                () -> f.battle.declareMapAttack(f.danheng, null));
    }

    /**
     * Note: And the target must be an enemy - nothing else on the field has a toughness bar to shave.
     */
    @Test
    public void aDeclarationAgainstAnAllyIsRefused() {
        Fixture f = new Fixture();

        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> f.battle.declareMapAttack(f.danheng, f.danheng));
        Assertions.assertTrue(refused.getMessage().contains("not an enemy"), refused.getMessage());
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static final class Fixture {
        private final Character danheng = CharacterFactory.create(DANHENG, LEVEL);
        private final Enemy enemy = BattleFixture.monster();
        private final Battle battle = new Battle(List.of(danheng), List.of(enemy), BattleFixture.alwaysRoll(0.99));
        private final double stanceBefore;

        private Fixture() {
            // A long bar and a Wind weakness, so the 30-point shave is measurable and the element really matches.
            enemy.setMaxStance(100);
            enemy.setStance(100);
            enemy.setStanceWeak(java.util.Set.of(DamageElement.WIND));
            stanceBefore = enemy.getStance();
        }

        private void declareMapAttack() {
            battle.declareMapAttack(danheng, enemy);
            battle.startBattle();
        }
    }
}
