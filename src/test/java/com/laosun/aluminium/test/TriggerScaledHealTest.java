package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * {@code HEAL} / {@code SHIELD} scaled by a Max HP, instead of a flat amount.
 *
 * <p><b>Why.</b> The game states most heals and shields as a share of somebody's Max HP — relic set 106's
 * 4-piece is 「恢复等同于生命上限 8% 的生命值」, and the skill-side loader has carried the same idea in
 * {@code skill_effects.json}'s {@code scale} field since P10-3. The trigger table had only {@code amount}, so an
 * ability written that way could not be authored at all: the number is different for every character and every
 * level, which is exactly why writing one in the file would be wrong.
 *
 * <p>Two spellings, and the difference matters: {@code target_max_hp} is the <b>recipient's</b> bar (the relic's
 * case), {@code owner_max_hp} is the <b>healer's</b> (what the skill data calls {@code healer_max_hp}). The
 * per-recipient one is why the amount is computed inside the target loop rather than once — a party-wide heal
 * that restores 8% of <i>each ally's own</i> Max HP is not one number.
 */
public class TriggerScaledHealTest {
    /** Himeko: no shipped rule file, so the table under test is the only one. */
    private static final int OWNER = 1003;
    private static final int ALLY = 1202;
    private static final int LEVEL = 80;
    private static final double EPS = 1e-6;

    // ==================================================================
    // 1. The two scales
    // ==================================================================

    @Test
    public void aHealScaledByTheTargetsMaxHpRestoresThatShare() {
        Battle battle = battleWith(healRule("target_max_hp", 0.08, null));
        Character owner = battle.characters.getFirst();
        double maxHp = owner.getMaxHp();
        owner.takeDamage(maxHp * 0.5);
        double before = owner.getCurrentHp();

        fire(battle, owner);
        Assertions.assertEquals(before + maxHp * 0.08, owner.getCurrentHp(), EPS,
                "8% of the receiving unit's own Max HP");
    }

    @Test
    public void aHealScaledByTheOwnersMaxHpUsesTheHealersBar() {
        Battle battle = withAlly(healRule("owner_max_hp", 0.1, "target"));
        Character healer = battle.characters.getFirst();
        Character ally = battle.characters.get(1);
        ally.takeDamage(ally.getMaxHp() * 0.5);
        double before = ally.getCurrentHp();

        battle.fireTriggers(TriggerEvent.ALLY_ATTACK, healer, ally, 1, 0);

        Assertions.assertEquals(before + healer.getMaxHp() * 0.1, ally.getCurrentHp(), EPS,
                "10% of the HEALER's Max HP -- the two scales are not interchangeable, and the ally's bar is a "
                        + "different size");
    }

    @Test
    public void everyRecipientOfAPartyHealUsesItsOwnBar() {
        Battle battle = withAlly(healRule("target_max_hp", 0.08, "all_allies"));
        Character owner = battle.characters.getFirst();
        Character ally = battle.characters.get(1);
        owner.takeDamage(owner.getMaxHp() * 0.5);
        ally.takeDamage(ally.getMaxHp() * 0.5);
        double ownerBefore = owner.getCurrentHp();
        double allyBefore = ally.getCurrentHp();

        fire(battle, owner);

        Assertions.assertEquals(ownerBefore + owner.getMaxHp() * 0.08, owner.getCurrentHp(), EPS);
        Assertions.assertEquals(allyBefore + ally.getMaxHp() * 0.08, ally.getCurrentHp(), EPS,
                "the amount is computed per recipient, not once for the whole party");
    }

    @Test
    public void aShieldCanBeScaledToo() {
        Battle battle = battleWith(shieldRule("target_max_hp", 0.1, null));
        Character owner = battle.characters.getFirst();

        fire(battle, owner);
        Assertions.assertEquals(owner.getMaxHp() * 0.1, owner.getShield(), EPS);
    }

    /** The flat spelling still works, unchanged. */
    @Test
    public void aFlatAmountStillMeansAFlatAmount() {
        Battle battle = battleWith(flatHealRule(500));
        Character owner = battle.characters.getFirst();
        owner.takeDamage(owner.getMaxHp() * 0.5);
        double before = owner.getCurrentHp();

        fire(battle, owner);
        Assertions.assertEquals(before + 500, owner.getCurrentHp(), EPS);
    }

    // ==================================================================
    // 2. Fail fast at load time
    // ==================================================================

    @Test
    public void anAmountAndAScaleTogetherAreRejected() {
        EffectSpec effect = healOp("target_max_hp", 0.08);
        TriggerSpecs.set(effect, "amount", 100.0);
        IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER, List.of(TriggerSpecs.rule("ALLY_ATTACK", null, effect))));
        Assertions.assertTrue(e.getMessage().contains("amount"), e.getMessage());
        Assertions.assertTrue(e.getMessage().contains("scale"), e.getMessage());
    }

    @Test
    public void aScaleWithoutAMagnitudeAndAPercentageWithoutAScaleAreBothRejected() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER, List.of(healRule("target_max_hp", null, null))),
                "a scale without a percent says 'some share of a Max HP'");

        EffectSpec percentOnly = new EffectSpec();
        TriggerSpecs.set(percentOnly, "op", "HEAL");
        TriggerSpecs.set(percentOnly, "percent", 0.08);
        IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER,
                        List.of(TriggerSpecs.rule("ALLY_ATTACK", null, percentOnly))));
        // a percentage of nothing is a mistake, not a default
        Assertions.assertTrue(e.getMessage().contains("scale"), e.getMessage());
    }

    /**
     * The scale names are a closed set, and the error lists them.
     *
     * <p>The unknown spelling used here is {@code healer_max_hp} on purpose: that is what
     * {@code skill_effects.json} calls the same idea, and the two vocabularies are deliberately not the same
     * string — a trigger rule's owner may be shielding rather than healing, so the trigger table says
     * {@code owner_max_hp} and the loader says {@code healer_max_hp}. A rule that copies the loader's spelling
     * gets told so instead of silently attaching nothing.
     */
    @Test
    public void anUnknownScaleIsRejectedWithTheKnownOnesListed() {
        IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER, List.of(unknownScaleRule())));
        Assertions.assertTrue(e.getMessage().contains("healer_max_hp"), e.getMessage());
        Assertions.assertTrue(e.getMessage().contains("target_max_hp"), e.getMessage());
        Assertions.assertTrue(e.getMessage().contains("owner_max_hp"), e.getMessage());
    }

    @Test
    public void aScaleCannotBeCombinedWithPerTarget() {
        EffectSpec effect = healOp("target_max_hp", 0.08);
        TriggerSpecs.set(effect, "perTarget", Boolean.TRUE);
        IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER, List.of(TriggerSpecs.rule("ALLY_ATTACK", null, effect))));
        Assertions.assertTrue(e.getMessage().contains("per_target"), e.getMessage());
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static TriggerSpec healRule(String scale, Double percent, String target) {
        EffectSpec effect = healOp(scale, percent);
        TriggerSpecs.set(effect, "target", target);
        return TriggerSpecs.rule("ALLY_ATTACK", null, effect);
    }

    private static TriggerSpec shieldRule(String scale, Double percent, String target) {
        EffectSpec effect = healOp(scale, percent);
        TriggerSpecs.set(effect, "op", "SHIELD");
        TriggerSpecs.set(effect, "target", target);
        return TriggerSpecs.rule("ALLY_ATTACK", null, effect);
    }

    private static EffectSpec healOp(String scale, Double percent) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "HEAL");
        TriggerSpecs.set(effect, "scale", scale);
        TriggerSpecs.set(effect, "percent", percent);
        return effect;
    }

    private static TriggerSpec unknownScaleRule() {
        return healRule("healer_max_hp", 0.08, null);
    }

    private static TriggerSpec flatHealRule(double amount) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "HEAL");
        TriggerSpecs.set(effect, "amount", amount);
        return TriggerSpecs.rule("ALLY_ATTACK", null, effect);
    }

    private static Battle battleWith(TriggerSpec... specs) {
        Battle battle = new Battle(List.of(ownerWith(specs)), List.of(dummy()), new Random(0));
        battle.startBattle();
        return battle;
    }

    private static Battle withAlly(TriggerSpec... specs) {
        Battle battle = new Battle(List.of(ownerWith(specs), CharacterFactory.create(ALLY, LEVEL)),
                List.of(dummy()), new Random(0));
        battle.startBattle();
        return battle;
    }

    private static Character ownerWith(TriggerSpec... specs) {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(specs)));
        return owner;
    }

    private static void fire(Battle battle, Character actor) {
        battle.fireTriggers(TriggerEvent.ALLY_ATTACK, actor, null, 1, 0);
    }

    private static Enemy dummy() {
        return Enemy.fromAttributes("dummy", 1_000_000, 0, 100, 100);
    }
}
