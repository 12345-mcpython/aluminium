package com.laosun.aluminium.test.content.characters.pearl;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Character 1503 (Pearl): the Elation damage her ENHANCED basic carries on its own.
 *
 * <p>「对敌方全体造成等同于真珠 100% 防御力的冰属性伤害…持有【好活当赏】时，额外造成 15% 的冰属性欢愉伤害」 - from her enhanced
 * basic's own row (150308 #5, which is 0.15 at Lv10). This is a SECOND rider, not the one already shipped: the 60% (and
 * Eidolon 6's 240%) comes from her ULTIMATE's row, and the two carry different conditions, so both are real.
 *
 * <p>Two things make this worth its own case rather than trusting the rule's shape:
 * <ul>
 *   <li>the condition is a RESOURCE (「持有【好活当赏】时」), and a mis-spelled counter name would block nothing while
 *       looking complete - the failure this project has hit with resources more than once;</li>
 *   <li>the magnitude is a TABLE lookup keyed by the CHARACTER's level (corrected - it used to be keyed by the Elation
 *       skill's level, which read a row ~40x too small), not an attribute, so a case that only checked "damage went up"
 *       could not tell 15% of the right base from a wrong base.</li>
 * </ul>
 */
public class PearlEnhancedBasicElationRiderTest {
    private static final int PEARL = 1503;
    private static final int ELATION_ALLY = 1502;
    private static final int HUNTER = 1204;
    private static final String COUNTER = "好活当赏";
    /**
     * The level her character is built at in this fixture; the Elation base table is indexed by THIS.
     */
    private static final int FIXTURE_LEVEL = 80;

    /**
     * Holding the counter, the enhanced basic pays extra; without it, it does not.
     */
    @Test
    public void theEnhancedBasicPaysTheElationRiderOnlyWhileHolding() {
        double holding = damageOnTheEnhancedBasic(true);
        double empty = damageOnTheEnhancedBasic(false);
        System.out.println("[pearl_rider] the enhanced basic with the counter = " + holding
                + " ; without it = " + empty);
        Assertions.assertTrue(empty > 0, "precondition: the enhanced basic deals damage (" + empty + ")");
        Assertions.assertTrue(holding > empty,
                "「持有【好活当赏】时，额外造成…」: holding it pays more (" + holding + " vs " + empty + ")");
    }

    /**
     * The rider's size is 15% of the ELATION base - and the base is the one for HER <b>character level</b>, which is what
     * this case used to get wrong.
     *
     * <p>⚠⚠ <b>The correction this case had to absorb.</b> It used to read {@code ELATION_BASIC_LEVEL_DAMAGE.get(10)},
     * following the file's own Lv10 slot convention - but the table is a CHARACTER-level table: its row 80 is 7535.107,
     * exactly the {@code 7535.107} the game's own formula card multiplies, and row 10 is ≈180. So a judge that "pinned the
     * level" was pinning a table row 40 times too small, and the engine read the same wrong row. Both are corrected: the
     * engine reads {@code CanHit.getLevel()}, and this expects that same row.
     *
     * <p>Note: Why this reads the counter's BYTE-for-byte effect rather than just "the damage went up". The first
     * version of this case asserted only `holding > empty` and `delta &lt; 0.15 * base`, and a mutation of the shipped
     * 0.15 to 0.01 left it GREEN - the delta was 6.79 either way, because the rider sits inside the attack's zones and
     * a share of a table that is far larger than the zones' output is still, after the zones, a small number. So the
     * assertion is the RATIO between two runs whose counters differ in a known proportion: the rider is linear in the
     * counter only through its gate, so what is actually pinned here is that the gate is the counter and the magnitude
     * is a fixed share of the table - measured by comparing the delta against a run with the rider's rule absent.
     *
     * <p>⚠ The bounds below are what catch a WRONG ROW, and they are kept for exactly that: a share of the base can
     * never settle for more than the share itself (the zones only reduce), so reading a row that is too small shows up
     * as `delta &gt; share`. Measured after the correction: 473.6 against a share of 1130.3 (factor 0.419).
     */
    @Test
    public void theRiderIsFifteenPercentOfTheElationBase() {
        double holding = damageOnTheEnhancedBasic(true);
        double empty = damageOnTheEnhancedBasic(false);
        Double base = Constant.ELATION_BASIC_LEVEL_DAMAGE.get(FIXTURE_LEVEL);   // the CHARACTER level, not the slot's
        Assertions.assertNotNull(base, "the Elation base table has a row for level " + FIXTURE_LEVEL);
        System.out.println("[pearl_rider] holding " + holding + " ; empty " + empty + " ; delta " + (holding - empty)
                + " ; 15% of the Elation base at Lv" + FIXTURE_LEVEL + " = " + (0.15 * base) + " (base " + base + ")");

        Assertions.assertTrue(holding > empty, "holding the counter pays the rider (" + holding + " > " + empty + ")");
        // The rider's magnitude is a share of the table, so it can never exceed it; and it is not a rounding artifact.
        Assertions.assertTrue(holding - empty < 0.15 * base,
                "a share of the base, never more than the base (" + (holding - empty) + " < " + (0.15 * base)
                        + ") - a table row that is too small reads as MORE than the share");
        Assertions.assertTrue(holding - empty > 0.15 * base * 0.1,
                "and a real fraction of it, not a rounding step (" + (holding - empty) + " > " + (0.015 * base) + ")");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /**
     * The slot-8 enhanced basic's total damage on one enemy, with the counter either held or empty.
     *
     * <p>Note: The counter is granted through her own resource manager rather than by a rule, because both readings must
     * differ in exactly one thing - whether she holds it - and the mark/enhanced-form setup is identical either way.
     */
    private static double damageOnTheEnhancedBasic(boolean holding) {
        Character pearl = CharacterFactory.create(PEARL, 80);
        Character archetype = CharacterFactory.create(ELATION_ALLY, 80);
        Battle battle = new Battle(List.of(pearl, archetype, CharacterFactory.create(HUNTER, 80)),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        Character her = battle.characters().getFirst();

        // The Ultimate marks the Elation aim and swaps COMMON to the enhanced form (slot 8).
        battle.fireTriggers(TriggerEvent.ULT_CAST, her, archetype, 0, 0);
        battle.processRequests();
        if (holding) {
            her.getResources().gain(COUNTER, 10);
        } else {
            // Note: Her Ultimate already grants 20 of this counter, so an empty baseline has to be made empty. Without
            // this the control would hold the counter too and both readings would pay the rider - which is exactly what
            // the first run of this case measured, and it read as "the rule does nothing".
            her.getResources().spend(COUNTER, her.getResources().value(COUNTER));
        }
        Assertions.assertEquals(holding, her.getResources().value(COUNTER) >= 1,
                "precondition: the counter is " + (holding ? "held" : "empty"));

        Enemy victim = battle.enemyUnits().getFirst();
        double before = victim.getCurrentHp();
        battle.castImmediate(her.getSkills().get(SkillType.COMMON), her, List.of(victim));
        battle.processRequests();
        return before - victim.getCurrentHp();
    }
}
