package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.buff.StatModifierBuff;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * {@code "ticks_on": "self"} — a buff whose duration is spent by <b>the rule owner's</b> turns (M-42 ④), and the
 * cleanup when that owner dies (③).
 *
 * <p><b>Why the clock is not always the carrier.</b> 星期日's 【蒙福者】: 「并使目标及其召唤物成为【蒙福者】…星期日自身
 * 每回合开始时【蒙福者】状态持续回合减1，共持续#3[i]回合。…当星期日陷入无法战斗状态时，【蒙福者】效果也会被解除。」
 * The state sits on the <b>ally</b> and is spent by <b>his</b> turns. Counting it down on the carrier's turns — what
 * a timed buff does by default — would end it after a different number of turns in every fight, with nothing to see.
 *
 * <p><b>What is pinned.</b> That the ally's own turns do <b>not</b> spend it; that the caster's turns do, and expire
 * it; that a buff which says nothing still ticks on its carrier (the default must not have moved); that the anchor's
 * death takes it off; and that a spelling other than {@code "self"} is refused while the file is read.
 */
public class TickAnchorTest {
    private static final double EPS = 1e-6;

    private static final int OWNER = 1003;
    private static final int ALLY = 1202;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    // ==================================================================
    // 1. Whose turns spend it
    // ==================================================================

    /**
     * Two turns pass — one for each character — and the buff has lost exactly one turn's worth: the caster's.
     *
     * <p>Written as one case with both orders because "ticks on the caster" and "does not tick on the carrier" are
     * two different claims: an implementation that ticked on <b>everybody's</b> turns would satisfy the first alone.
     */
    @Test
    public void theCastersTurnsSpendItAndTheCarriersDoNot() {
        Battle battle = battleWith(grantToAlly(true, 3), new Random(0));
        Character owner = battle.characters.getFirst();
        Character ally = battle.characters.get(1);
        Assertions.assertEquals(3, durationOf(ally), "precondition: 3 turns");

        takeTurn(battle, ally);
        Assertions.assertEquals(3, durationOf(ally), "the CARRIER's turn does not spend it");

        takeTurn(battle, owner);
        Assertions.assertEquals(2, durationOf(ally), "the rule owner's turn does");
    }

    /** …and it expires on his turns, not on hers. */
    @Test
    public void itExpiresOnTheCastersTurns() {
        Battle battle = battleWith(grantToAlly(true, 2), new Random(0));
        Character owner = battle.characters.getFirst();
        Character ally = battle.characters.get(1);

        takeTurn(battle, owner);
        takeTurn(battle, owner);

        Assertions.assertEquals(-1, durationOf(ally),
                "two of HIS turns and the state is gone, wherever it was sitting");
    }

    /**
     * A buff that says nothing ticks on the unit carrying it — the ordinary case, which must not have moved.
     *
     * <p>Pinned beside the case above because both are about the same mechanism and only the pair shows that the
     * anchor is an <b>option</b> rather than the new default.
     */
    @Test
    public void anUnanchoredBuffStillTicksOnItsCarrier() {
        Battle battle = battleWith(grantToAlly(false, 3), new Random(0));
        Character owner = battle.characters.getFirst();
        Character ally = battle.characters.get(1);

        takeTurn(battle, ally);
        Assertions.assertEquals(2, durationOf(ally), "her own turn spends it");

        takeTurn(battle, owner);
        Assertions.assertEquals(2, durationOf(ally), "…and his turn is nothing to do with her buff");
    }

    // ==================================================================
    // 2. The anchor's death (③)
    // ==================================================================

    /**
     * When the unit whose turns spend the buff dies, the buff goes with it.
     *
     * <p>⚠ The generic reason is stronger than 星期日's sentence: an anchored buff whose clock will never come again
     * is a <b>leak</b>, not a long duration. And it cannot be written as a rule on the dying unit — {@code
     * fireTriggers} skips dead units, so its own table never gets the chance.
     */
    @Test
    public void theAnchorsDeathTakesTheBuffOff() {
        Battle battle = battleWith(grantToAlly(true, 5), new Random(0));
        Character owner = battle.characters.getFirst();
        Character ally = battle.characters.get(1);
        Assertions.assertEquals(5, durationOf(ally), "precondition");

        owner.takeDamage(9_999_999);
        battle.processRequests();

        Assertions.assertTrue(owner.isDeath(), "precondition: the caster is down");
        Assertions.assertEquals(-1, durationOf(ally),
                "「当星期日陷入无法战斗状态时，【蒙福者】效果也会被解除」 -- and generally: no clock, no buff");
    }

    // ==================================================================
    // 3. The spelling is closed
    // ==================================================================

    /** Only the rule owner's clock has a document behind it, so anything else is refused while the file is read. */
    @Test
    public void anUnknownTickOwnerIsRefused() {
        EffectSpec effect = boost("BREAKING_EFFECT", 0.5, 3);
        TriggerSpecs.set(effect, "ticksOn", "target");

        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER, List.of(TriggerSpecs.rule("ALLY_ATTACK", null, effect))));

        Assertions.assertTrue(refused.getMessage().contains("ticks_on"), refused.getMessage());
        Assertions.assertTrue(refused.getMessage().contains("self"), refused.getMessage());
    }

    /** A permanent buff is never counted down at all, so an anchor on it would be ignored. */
    @Test
    public void anAnchorOnAPermanentBuffIsRefused() {
        EffectSpec effect = boost("BREAKING_EFFECT", 0.5, null);
        TriggerSpecs.set(effect, "permanent", true);
        TriggerSpecs.set(effect, "ticksOn", "self");

        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER, List.of(TriggerSpecs.rule("ALLY_ATTACK", null, effect))));

        Assertions.assertTrue(refused.getMessage().contains("permanent"), refused.getMessage());
    }

    // ==================================================================
    // Fixture
    // ==================================================================

    /** A rule that grants the ally a 提升 lasting {@code turns}, anchored to the owner's turns when asked. */
    private static TriggerSpec grantToAlly(boolean anchoredToOwner, int turns) {
        EffectSpec effect = boost("BREAKING_EFFECT", 0.5, turns);
        TriggerSpecs.set(effect, "target", "target");
        if (anchoredToOwner) {
            TriggerSpecs.set(effect, "ticksOn", "self");
        }
        return TriggerSpecs.rule("ALLY_ATTACK", null, effect);
    }

    private static EffectSpec boost(String attribute, double percent, Integer turns) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "MODIFY_ATTR");
        TriggerSpecs.set(effect, "attribute", attribute);
        TriggerSpecs.set(effect, "percent", percent);
        TriggerSpecs.set(effect, "turns", turns);
        return effect;
    }

    private static Battle battleWith(TriggerSpec rule, Random rng) {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        // ⚠ The speeds are left alone on purpose: 停云 is faster, so HER turn comes first. That matters because
        // `takeTurn` runs the queue up to the requested unit, and any other unit's turn inside that window is a
        // turn too — the first draft asked for the caster's turn first, got a window that also contained hers, and
        // read the two ticks as one ("the carrier spends it" — the opposite of what was happening).
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(rule)));
        Battle battle = new Battle(List.of(owner, CharacterFactory.create(ALLY, LEVEL)), List.of(dummy()), rng);
        battle.startBattle();
        // The rule fires on an event we drive, with the ally as the subject.
        battle.fireTriggers(TriggerEvent.ALLY_ATTACK, owner, battle.characters.get(1), 1, 0);
        return battle;
    }

    /**
     * The remaining duration of <b>the granted</b> modifier, or {@code -1} when it is not attached.
     *
     * <p>⚠ Filtered by attribute, and that is not tidiness: the ally keeps <b>her own</b> rules (only the rule
     * owner's table is replaced by the fixture), so she may carry modifiers of her own — reading "the first
     * StatModifierBuff" measured one of those in the first draft and produced a duration that fell by one per turn
     * of the wrong unit.
     */
    private static int durationOf(Character who) {
        for (StatModifierBuff buff : who.getBuffManager().allBuffsOf(StatModifierBuff.class)) {
            if (buff.getAttribute() == AttributeType.BREAKING_EFFECT) {
                return buff.duration();
            }
        }
        return -1;
    }

    /**
     * Runs turns until {@code who} is the actor, and settles that turn's boundaries (both halves).
     *
     * <p>Both halves matter here: a {@code MODIFY_ATTR} modifier is a <b>late</b> buff, so its duration is spent by
     * {@code afterMove} — driving only {@code beforeMove} would measure nothing.
     */
    private static void takeTurn(Battle battle, Character who) {
        for (int guard = 0; guard < 60; guard++) {
            battle.stepForward();
            if (battle.isOver()) {
                throw new AssertionError("the battle ended before the requested unit acted");
            }
            boolean mine = battle.queue.getCurrentActor().getCanHit() == who;
            if (mine) {
                battle.beforeMove();
                battle.afterMove();
                return;
            }
            battle.afterMove();
        }
        throw new AssertionError("no turn for the requested unit within 60 steps");
    }

    private static Enemy dummy() {
        return EnemyFactory.create(MONSTER, 90, 1);
    }
}
