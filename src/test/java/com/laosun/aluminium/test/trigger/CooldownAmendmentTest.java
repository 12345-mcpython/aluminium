package com.laosun.aluminium.test.trigger;

import com.google.gson.Gson;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TestTurns;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * {@code MODIFY_RULE} with {@code cooldown_delta} - "this talent comes back one turn sooner".
 *
 * <p><b>Why this vocabulary exists.</b> 1002 Dan Heng's Eidolon 2 is 「天赋的冷却时间减少 1 回合」: the talent states
 * {@code cooldown: 2}, and the Eidolon lowers it to 1. None of the existing amendments could say that - they move a
 * firing count, a probability, an effect value, an effect duration and a stack cap, all of which are about what the
 * rule does or how often per turn, not about how many turns it must wait. A second rule with {@code cooldown: 1}
 * would be the same sentence written twice (and {@code ContentDuplicateGuardTest} refuses exactly that).
 *
 * <p><b>What this file pins.</b> That the amendment really shortens the wait by one of the owner's turns (against a
 * control that keeps the stated cooldown), that it is a delta rather than a replacement, and the three shapes the
 * load refuses: naming a rule that states no cooldown, a delta that would go below zero, and a delta of zero.
 */
public class CooldownAmendmentTest {
    private static final int OWNER = 1003;
    private static final int LEVEL = 80;
    private static final String RULE = "limited_rule";

    /**
     * The amendment is worth exactly one turn: with it, one of the owner's turns clears the limit; without it, two
     * are needed.
     */
    @Test
    public void theAmendmentShortensTheWaitByOneTurn() {
        Battle shortened = battle(limited(2), shortening());
        Character owner = shortened.characters().getFirst();

        Assertions.assertEquals(1, fire(shortened, owner), "the first event fires the rule");
        TestTurns.take(shortened, owner);
        Assertions.assertEquals(1, fire(shortened, owner),
                "cooldown 2 amended by -1 is 1: one of the owner's turns is enough");

        Battle control = battle(limited(2));
        Character controlOwner = control.characters().getFirst();
        Assertions.assertEquals(1, fire(control, controlOwner));
        TestTurns.take(control, controlOwner);
        Assertions.assertEquals(0, fire(control, controlOwner),
                "the control: the rule still states 2, so one turn is not enough");
        TestTurns.take(control, controlOwner);
        Assertions.assertEquals(1, fire(control, controlOwner), "and the second turn clears it");
    }

    /**
     * Note: A rule that states no cooldown has no number to lower, so the amendment would load and change nothing.
     */
    @Test
    public void namingARuleWithNoCooldownIsRefusedAtLoad() {
        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> battle(limited(null), shortening()));

        Assertions.assertTrue(refused.getMessage().contains("cooldown"), refused.getMessage());
        Assertions.assertTrue(refused.getMessage().contains(RULE), "the message names the rule it could not amend");
    }

    /**
     * Note: The result may not go below zero - "a negative cooldown" would be a third state nothing reads.
     */
    @Test
    public void anAmendmentThatWouldGoNegativeIsRefusedAtLoad() {
        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> battle(limited(1), shortening(-2)));

        Assertions.assertTrue(refused.getMessage().contains("negative"), refused.getMessage());
    }

    /**
     * Note: Zero is not a delta; it is the field being omitted.
     */
    @Test
    public void aZeroDeltaIsRefusedAtLoad() {
        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> battle(limited(2), shortening(0)));

        Assertions.assertTrue(refused.getMessage().contains("cooldown_delta"), refused.getMessage());
    }

    /**
     * The JSON spelling binds: a snake_case mistake would leave the field null and the amendment would vanish.
     */
    @Test
    public void theJsonSpellingBinds() {
        EffectSpec effect = new Gson().fromJson(
                "{\"op\":\"MODIFY_RULE\",\"rule\":\"limited_rule\",\"cooldown_delta\":-1}", EffectSpec.class);
        Assertions.assertEquals(-1, effect.getCooldownDelta(), "the field is spelled `cooldown_delta`");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /**
     * The rule under test: on any ally's attack, pay 1 skill point, with the stated cooldown.
     *
     * @param cooldown the rule's own {@code cooldown}, or {@code null} for none
     */
    private static TriggerSpec limited(Integer cooldown) {
        TriggerSpec spec = TriggerSpecs.rule("ALLY_ATTACK", null, gain());
        TriggerSpecs.set(spec, "id", RULE);
        TriggerSpecs.set(spec, "cooldown", cooldown);
        return spec;
    }

    /**
     * The amendment: at battle start, lower the named rule's cooldown.
     */
    private static TriggerSpec shortening() {
        return shortening(-1);
    }

    private static TriggerSpec shortening(int delta) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "MODIFY_RULE");
        TriggerSpecs.set(effect, "rule", RULE);
        TriggerSpecs.set(effect, "cooldownDelta", delta);
        return TriggerSpecs.rule("BATTLE_START", null, effect);
    }

    private static EffectSpec gain() {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "GAIN_SKILL_POINT");
        TriggerSpecs.set(effect, "amount", 1.0);
        return effect;
    }

    private static Battle battle(TriggerSpec... specs) {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(specs)));
        Battle battle = new Battle(List.of(owner), List.of(dummy()), BattleFixture.seeded());
        battle.startBattle();
        return battle;
    }

    private static int fire(Battle battle, Character actor) {
        return battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, actor, null, 1, 0));
    }

    private static Enemy dummy() {
        return Enemy.fromAttributes("dummy", 100_000, 100, 100, 100);
    }
}
