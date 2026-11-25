package com.laosun.aluminium.test.engine;


import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.buff.*;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * {@code EXTEND_BUFF}: "... increases the duration by 1 turn".
 *
 * <p><b>Why the vocabulary needed it.</b> Documents lengthen a buff that is <b>already up</b> rather
 * than creating a new one - March 7th (三月七)'s "加护" (Reinforce) ("increases the duration of the shield provided by the Skill by 1 turn"), Bronya (布洛妮娅)'s eidolon 6, Sampo (桑博)'s Wind Shear (dot:wind_shear),
 * Himeko (姬子)'s Burn (dot:burn), Bailu (白露)'s Invigoration (生息), Huohuo (藿藿)'s Divine Provision (禳命), Gallagher (加拉赫)'s Besotted (酩酊) ... Before this op the only way to write one was to <b>fold the +1 into the
 * ability it lengthens</b>, which erases the trace's own line from the data and makes the base ability state a
 * duration that is not its own.
 *
 * <p><b>The two filters, and why neither is optional.</b> Every sentence identifies the buff by its <b>origin</b>
 * ("provided by the Skill") and then by <b>what it is</b>: a state's name (Burn / Invigoration / Frozen / shield), or - when the text names an
 * effect rather than a state ("damage increase effect") - the <b>attribute</b> the modifier sits on. "Everything I have on that
 * unit" is deliberately not a spelling, and {@link #onlyTheOwnersOwnBuffTheFilterNamesIsLengthened} is the case that shows why:
 * Bronya's DEFENCE trace buff from {@code BATTLE_START} can still be ticking when she casts her Skill.
 *
 * <p><b>L3:</b> the single-clause cases are grouped into the scenarios below, one {@code assertAll} per scenario so a
 * failing clause still names itself; the two load-time rejection paths stay their own tests, because an
 * {@code assertThrows} is not a positive clause and must not hide behind one.
 */
public class ExtendBuffTest {
    private static final double EPS = 1e-6;

    private static final int OWNER = 1002;
    private static final int ALLY = 1202;
    private static final int LEVEL = 80;

    /**
     * What a filter names: a state's own name, a DOT's name (its element's), or the attribute a modifier sits on.
     *
     * <p>Three positive spellings, one clause - "the buff the rule names" is the thing that grows. The "加护"
     * (Reinforce) shape comes first because it is the whole reason the op exists: the same rule applies the shield
     * and then lengthens it, and the two numbers stay in two rules.
     */
    @Test
    public void theFilterNamesTheBuffItLengthens() {
        Fixture reinforced = new Fixture(
                TriggerSpecs.shield(500, 2, "target"),
                TriggerSpecs.extendBuff(BuffManager.SHIELD_STATE, null, 1));
        reinforced.fire();

        Fixture state = new Fixture(
                TriggerSpecs.applyBuff("char:1309/concerto", 1, null),
                TriggerSpecs.extendBuff("char:1309/concerto", null, 2));
        state.fire();

        Fixture dot = new Fixture(
                TriggerSpecs.dot("Fire", 500.0, null, null, 2, null),
                TriggerSpecs.extendBuff("dot:burn", null, 1));
        dot.fire();

        Fixture modifier = new Fixture(
                TriggerSpecs.modifyAttr("ATTACK", 0.2, 1, null, null, null, "target"),
                TriggerSpecs.extendBuff(null, "ATTACK", 1));
        modifier.fire();

        Assertions.assertAll("the filter names the buff it lengthens",
                () -> Assertions.assertEquals(3, reinforced.buff(ShieldBuff.class).duration(),
                        "shield state 「加护」: 2 turns from the shield + 1 from the trace -- and the two numbers stay in two rules"),
                () -> Assertions.assertEquals(3, state.buff(StateBuff.class).duration(),
                        "state name: 1 + 2"),
                () -> Assertions.assertEquals(3, dot.buff(DotBuff.class).duration(),
                        "DOT name: the DOT's name is its element's (Fire -> Burning), translated in one place"),
                () -> Assertions.assertEquals(2, modifier.buff(StatModifierBuff.class).duration(),
                        "attribute name: 1 + 1"));
    }

    /**
     * And only that buff: neither filter is optional - not another unit's buff, and not the owner's other buffs.
     *
     * <p>The two negatives are the two halves of the filter. "Provided by the Skill" is about <b>who</b> applied the
     * buff, so a shield somebody else applied keeps its 2 turns. And "the shield" is about <b>what</b> the buff is, so
     * out of two buffs that are both the owner's, only the named one grows; a filter that lengthened every one of them
     * would look right in every single-buff test and silently add a turn to the burn in a real fight.
     */
    @Test
    public void onlyTheOwnersOwnBuffTheFilterNamesIsLengthened() {
        Fixture anotherUnitsShield = new Fixture(TriggerSpecs.extendBuff(BuffManager.SHIELD_STATE, null, 1));
        Character someoneElse = CharacterFactory.create(ALLY, LEVEL);
        anotherUnitsShield.target.getBuffManager().addBuff(new ShieldBuff(someoneElse, 500, 2));
        anotherUnitsShield.fire();

        Fixture ownersOtherBuffs = new Fixture(
                TriggerSpecs.dot("Fire", 500.0, null, null, 2, null),
                TriggerSpecs.shield(500, 2, "target"),
                TriggerSpecs.extendBuff(BuffManager.SHIELD_STATE, null, 1));
        ownersOtherBuffs.fire();

        Assertions.assertAll("only the owner's buff that the filter names is lengthened",
                () -> Assertions.assertEquals(2, anotherUnitsShield.buff(ShieldBuff.class).duration(),
                        "another unit's buff: a shield somebody else applied is not 「the shield provided by the Skill」"),
                () -> Assertions.assertEquals(3, ownersOtherBuffs.buff(ShieldBuff.class).duration(),
                        "the owner's named buff: the named buff got its turn"),
                () -> Assertions.assertEquals(2, ownersOtherBuffs.buff(DotBuff.class).duration(),
                        "the owner's other buff: and the owner's burn did not"));
    }

    /**
     * A permanent buff has no countdown, so there is nothing to lengthen - and that is not an error.
     *
     * <p>Note: The two effects sit on <b>two events</b> on purpose: with both in one rule the second firing would
     * re-apply the state (a same-kind buff replaces), so the duration would look unchanged whether or not the
     * {@code permanent} guard were there - a test that passes for the wrong reason.
     */
    @Test
    public void aPermanentBuffHasNoCountdownToLengthen() {
        Fixture f = new Fixture(
                TriggerSpecs.rule("ALLY_ATTACK", null, TriggerSpecs.applyBuff("char:1309/concerto", null, true)),
                TriggerSpecs.rule("BASIC_ATTACK", null, TriggerSpecs.extendBuff("char:1309/concerto", null, 5)));
        f.fire();
        StateBuff state = f.buff(StateBuff.class);
        int duration = state.duration();

        Assertions.assertAll("a permanent buff has no countdown to lengthen",
                () -> Assertions.assertTrue(state.isPermanent(), "precondition: the state is 「the whole battle」"),
                () -> Assertions.assertDoesNotThrow(() -> f.fire(TriggerEvent.BASIC_ATTACK),
                        "the +5 is not an error: a permanent buff is never ticked"),
                () -> Assertions.assertEquals(duration, state.duration(),
                        "permanent means \"never ticked\": the +5 is a number nobody would ever read"));
    }

    /**
     * Note: Every buff a rule creates records <b>who applied it</b>.
     *
     * <p>This is what the origin filter rests on, and it has to hold everywhere rather than only where a constructor
     * demands it
     * ({@code DotBuff} needs it for kill credit) - a {@code StateBuff} or a stat modifier could be anonymous, and an
     * anonymous buff would make "the shield provided by the Skill" silently extend nothing at all. The audit is pinned here so that a
     * new buff-creating op has to keep its half of the deal.
     */
    @Test
    public void everyBuffARuleCreatesRecordsItsSource() {
        record Case(EffectSpec effect, Class<? extends AbstractBuff> kind) {
        }
        List<Case> cases = List.of(
                new Case(TriggerSpecs.shield(500, 2, "target"), ShieldBuff.class),
                new Case(TriggerSpecs.applyBuff("char:1309/concerto", 2, null), StateBuff.class),
                new Case(TriggerSpecs.modifyAttr("ATTACK", 0.2, 2, null, null, null, "target"), StatModifierBuff.class),
                new Case(TriggerSpecs.modifyDamageTaken(0.2, 2), VulnerabilityBuff.class),
                new Case(TriggerSpecs.taunt(1), TauntBuff.class),
                new Case(TriggerSpecs.applyControl("ctrl:frozen", 1, null, "target"), ControlBuff.class),
                new Case(TriggerSpecs.dot("Ice", 500.0, null, null, 2, null), DotBuff.class));

        Assertions.assertAll("every buff a rule creates records its source", () -> {
            for (Case one : cases) {
                Fixture f = new Fixture(one.effect());
                f.fire();
                Assertions.assertSame(f.owner, f.buff(one.kind()).getSource(),
                        one.kind().getSimpleName() + " must remember who applied it -- EXTEND_BUFF filters by origin");
            }
        });
    }

    // ==================================================================
    // Fail fast: what an EXTEND_BUFF rule may not say
    // ==================================================================

    @Test
    public void theFilterIsRequiredAndUnique() {
        IllegalArgumentException missing = Assertions.assertThrows(IllegalArgumentException.class,
                () -> tableOf(TriggerSpecs.extendBuff(null, null, 1)));
        Assertions.assertTrue(missing.getMessage().contains("buff"), missing.getMessage());

        IllegalArgumentException both = Assertions.assertThrows(IllegalArgumentException.class,
                () -> tableOf(TriggerSpecs.extendBuff("dot:burn", "ATTACK", 1)));
        Assertions.assertTrue(both.getMessage().contains("both"), both.getMessage());

        Assertions.assertThrows(IllegalArgumentException.class,
                () -> tableOf(TriggerSpecs.extendBuff(null, "NOT_AN_ATTRIBUTE", 1)), "unknown attribute");
    }

    @Test
    public void turnsAreRequiredAndNothingElseIsAccepted() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> tableOf(TriggerSpecs.extendBuff("dot:burn", null, null)), "no turns");
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> tableOf(TriggerSpecs.extendBuff("dot:burn", null, 0)), "0 turns");

        EffectSpec withAmount = TriggerSpecs.extendBuff("dot:burn", null, 1);
        TriggerSpecs.set(withAmount, "amount", 5.0);
        IllegalArgumentException rejected = Assertions.assertThrows(IllegalArgumentException.class,
                () -> tableOf(withAmount));
        Assertions.assertTrue(rejected.getMessage().contains("amount"), rejected.getMessage());
    }

    // ==================================================================
    // helpers
    // ==================================================================

    /**
     * One owner (with the rule under test), one ally to hold the buff, one enemy.
     */
    private static final class Fixture {
        private final Character owner;
        private final Character target;
        private final Battle battle;

        private Fixture(EffectSpec... effects) {
            this(TriggerSpecs.rule("ALLY_ATTACK", null, effects));
        }

        private Fixture(TriggerSpec... rules) {
            this.owner = CharacterFactory.create(OWNER, LEVEL);
            this.target = CharacterFactory.create(ALLY, LEVEL);
            this.owner.setTriggerTable(new TriggerTable(OWNER, List.of(rules)));
            this.target.setTriggerTable(new TriggerTable(ALLY, List.of()));
            this.battle = new Battle(List.of(owner, target), List.of(dummy()), BattleFixture.seeded());
            battle.startBattle();
        }

        /**
         * Fires the rule with the ally as the event's subject (which is what {@code target} resolves to).
         */
        private int fire() {
            return fire(TriggerEvent.ALLY_ATTACK);
        }

        private int fire(TriggerEvent event) {
            return battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(event, owner, target, 1, 0));
        }

        /**
         * The buff the rule was expected to touch, failing loudly when it is not there.
         */
        private <T extends AbstractBuff> T buff(Class<T> kind) {
            T found = target.getBuffManager().findBuff(kind);
            Assertions.assertNotNull(found, kind.getSimpleName() + " was expected on the ally");
            return found;
        }
    }

    /**
     * Compiles one effect into a table, which is where its validation runs.
     */
    private static TriggerTable tableOf(EffectSpec effect) {
        return new TriggerTable(OWNER, List.of(TriggerSpecs.rule("ALLY_ATTACK", null, effect)));
    }

    private static Enemy dummy() {
        return BattleFixture.monster();
    }
}
