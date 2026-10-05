package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.DefaultSkill;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * The pre-cast hook ({@code TriggerEvent.CAST_SETUP}) and {@code DELEGATE_DAMAGE}.
 *
 * <p><b>What this pair is for.</b> A document can say that a skill's damage is somebody else's: Evernight (长夜月)'s ultimate
 * "make the memosprite '长夜' deal Ice damage equal to '长夜''s #1[i]% Max HP to all enemies" is the memosprite's swing, and the rule that
 * delivers it as such runs on {@code ULT_CAST} - i.e. <b>after</b> the damage has already been expanded. So the
 * engine's own damage path swings 141303's rows first (with <b>her</b> attack as the base) and the commanded hit
 * lands on top: two instances where the document describes one, the first scaled off the wrong attribute
 * (measured: 8818.5 of hers + the commanded share, on 1002011). {@code DELEGATE_DAMAGE} is the content
 * saying "not mine", and it has to be on the pre-cast event because that is the only moment at which the swing can
 * still be stopped.
 *
 * <p><b>Why the cases below are shaped the way they are.</b>
 * <ol>
 *   <li>every "deals nothing" case is measured <b>against a control with the same cast but no delegation</b> - a
 *       test that only asserted {@code 0} would also pass if the skill dealt no damage at all (or if the damage
 *       path broke), which is the opposite of what this op promises;</li>
 *   <li>the toughness is asserted next to the damage, because a delegated cast expands no damage and therefore
 *       removes no toughness either - the two travel together, and the op that <b>does</b> deliver the swing
 *       (here {@code COMMAND_SUMMON}) has to carry both;</li>
 *   <li>the same slot-matching is checked from both sides: the same slot is delegated successfully, a different
 *       slot is refused loudly. The condition DSL has no variable for "which slot is being cast", so that
 *       comparison <i>is</i> the gate.</li>
 * </ol>
 */
public class CastSetupTest {
    private static final double EPS = 1e-6;

    /** Evernight (长夜月) - the character whose ultimate is the first user of the op. */
    private static final int OWNER = 1413;
    /** Himeko (姬子) - a plain ally, whose casts must not be affected by Evernight (长夜月)'s rule. */
    private static final int ALLY = 1003;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    /** Slot numbers as {@code skills.json} numbers them. */
    private static final int SKILL_SLOT = 2;
    private static final int ULTRA_SLOT = 3;

    /** The bar is widened so that "removed nothing" and "removed 90" cannot read the same. */
    private static final double BAR = 300;

    // ==================================================================
    // 1. The swing is not the caster's
    // ==================================================================

    /**
     * A delegated cast deals <b>nothing</b> - no damage and no toughness - while the control (same cast, no
     * delegation) still deals a real amount.
     */
    @Test
    public void aDelegatedCastDealsNoDamageAndNoToughnessOfItsOwn() {
        double control = hitPointsLost(TriggerTable.EMPTY, ULTRA_SLOT);
        Assertions.assertTrue(control > 0,
                "control: 141303's own rows really do deal damage when nothing delegates them (" + control + ")");

        Enemy enemy = enemy();
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(delegate("CAST_SETUP", "ULTRA", List.of("actor == self")))));
        Battle battle = new Battle(List.of(owner), List.of(enemy), new Random(0));
        double hpBefore = enemy.getCurrentHp();

        battle.castImmediate(new DefaultSkill(OWNER, ULTRA_SLOT, 1), owner, List.of(enemy));

        Assertions.assertEquals(0, hpBefore - enemy.getCurrentHp(), EPS,
                "\"make the memosprite '长夜' ... deal damage\": the damage is the memosprite's, so her cast swings nothing of its own");
        Assertions.assertEquals(BAR, enemy.getStance(), EPS,
                "...and the toughness of that swing goes with it, to whoever delivers it");
    }

    /** The gate is the slot the rule names, not "the ultimate": another slot delegates just as well. */
    @Test
    public void delegatingAnotherSlotWorksTheSameWay() {
        // Himeko (姬子)'s Skill rather than Evernight (长夜月)'s: the point is that the gate is the slot the rule names, and Evernight (长夜月)'s own
        // slot 2 turns out not to be a damaging skill at all - the control is 0 for her, which is exactly what the
        // control in the case above exists to catch (a "delegated nothing" and a "nothing to delegate" read alike).
        double control = hitPointsLost(ALLY, TriggerTable.EMPTY, SKILL_SLOT);
        Assertions.assertTrue(control > 0, "control: Himeko (姬子)'s Skill deals damage of its own (" + control + ")");

        double delegated = hitPointsLost(ALLY,
                new TriggerTable(ALLY, List.of(delegate("CAST_SETUP", "SKILL", List.of("actor == self")))),
                SKILL_SLOT);

        Assertions.assertEquals(0, delegated, EPS, "a rule that names SKILL hands over the Skill, not the Ultimate");
    }

    // ==================================================================
    // 2. Refusals - at load time
    // ==================================================================

    /** The op changes the cast being set up, so only the event that hands one over may carry it. */
    @Test
    public void theOpIsRefusedOnAnyOtherEvent() {
        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER, List.of(delegate("ULT_CAST", "ULTRA", null))));

        Assertions.assertTrue(refused.getMessage().contains("CAST_SETUP"), refused.getMessage());
    }

    /** The slot is what the op acts on, so it is required. */
    @Test
    public void theSlotIsRequired() {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "DELEGATE_DAMAGE");

        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER, List.of(TriggerSpecs.rule("CAST_SETUP", null, effect))));

        Assertions.assertTrue(refused.getMessage().contains("skill"), refused.getMessage());
    }

    // ==================================================================
    // 3. Refusals - when it fires
    // ==================================================================

    /**
     * Naming a different slot than the one being cast is refused rather than ignored.
     *
     * <p>Ignoring it would be a rule that loads, fires, and does nothing - and the author would have no way to
     * tell it apart from one that works.
     */
    @Test
    public void theSlotNamedMustBeTheSlotBeingCast() {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(delegate("CAST_SETUP", "SKILL", null))));
        Enemy enemy = enemy();
        Battle battle = new Battle(List.of(owner), List.of(enemy), new Random(0));

        IllegalStateException refused = Assertions.assertThrows(IllegalStateException.class,
                () -> battle.castImmediate(new DefaultSkill(OWNER, ULTRA_SLOT, 1), owner, List.of(enemy)));

        Assertions.assertTrue(refused.getMessage().contains("slot " + SKILL_SLOT)
                        && refused.getMessage().contains("slot " + ULTRA_SLOT),
                "the message must name both slots: " + refused.getMessage());
    }

    /**
     * A rule that fires on a teammate's cast is refused, and the message names the fix.
     *
     * <p>{@code CAST_SETUP} reaches <b>every</b> character's table (the same broadcast the other cast events use),
     * so a rule without {@code actor == self} would hand away somebody else's damage - an over-trigger that no
     * later observation could tell from the intended one, which is why the op checks it instead of trusting the
     * condition.
     */
    @Test
    public void theDelegationMustBeTheRuleOwnersOwnCast() {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(delegate("CAST_SETUP", "COMMON", null))));
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = enemy();
        Battle battle = new Battle(List.of(owner, ally), List.of(enemy), new Random(0));

        IllegalStateException refused = Assertions.assertThrows(IllegalStateException.class,
                () -> battle.castImmediate(new DefaultSkill(ALLY, 1, 1), ally, List.of(enemy)));

        Assertions.assertTrue(refused.getMessage().contains("actor == self"), refused.getMessage());
    }

    // ==================================================================
    // Fixture
    // ==================================================================

    /** How much HP a real cast of {@code slot} takes off the enemy, under the given table. */
    private static double hitPointsLost(TriggerTable table, int slot) {
        return hitPointsLost(OWNER, table, slot);
    }

    /** The same, for a caster that is not Evernight (长夜月) (the slot gate is not about ultimates, so it needs another). */
    private static double hitPointsLost(int cid, TriggerTable table, int slot) {
        Character owner = CharacterFactory.create(cid, LEVEL);
        owner.setTriggerTable(table);
        Enemy enemy = enemy();
        Battle battle = new Battle(List.of(owner), List.of(enemy), new Random(0));
        double before = enemy.getCurrentHp();

        battle.castImmediate(new DefaultSkill(cid, slot, 1), owner, List.of(enemy));

        return before - enemy.getCurrentHp();
    }

    private static TriggerSpec delegate(String event, String slot, List<String> when) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "DELEGATE_DAMAGE");
        TriggerSpecs.set(effect, "skill", slot);
        return TriggerSpecs.rule(event, when, effect);
    }

    /**
     * A monster with a wide bar and an Ice weakness.
     *
     * <p>Both matter for the assertions: the weakness is what <b>allows</b> toughness to move at all (the engine's
     * standing rule), so without it "removed nothing" would prove nothing; the width is what makes "removed 90"
     * distinguishable from "removed nothing".
     */
    private static Enemy enemy() {
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        enemy.setStanceWeak(Set.of(DamageElement.ICE));
        enemy.setMaxStance(BAR);
        enemy.setStance(BAR);
        return enemy;
    }
}
