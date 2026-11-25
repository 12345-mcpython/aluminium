package com.laosun.aluminium.test.engine;


import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
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
 * Negative effects: what counts as one, how many are attached, and how "解除 N 个负面效果" removes them.
 *
 * <p><b>Why the classification is the centre of this class.</b> Two ops and one condition all depend on the same
 * question - "is this buff a debuff?" - and getting it wrong is invisible: a dispel that quietly removes a
 * <i>shield</i> instead of a burn looks like a working rule until somebody reads the log. So the answer is
 * decided in exactly one place ({@code AbstractBuff.isDebuff()}), the set of classes that answer "yes" is pinned
 * here as a table, and each entry says why in the game's own terms.
 *
 * <p>The interesting entries are the ones that <i>don't</i> follow from where the buff sits: damage reduction (减伤)
 * ({@code ReductionBuff}) is a positive effect on the defender, vulnerability (易伤) ({@code VulnerabilityBuff}) is a negative
 * one, taunt (嘲讽) is negative <b>on its bearer</b>, and a named state's side depends on who applied it - which is why a
 * state answers "no" and says so.
 *
 * <p><b>L3:</b> the clauses that share a fixture and the same bookkeeping are grouped into the scenarios below, one
 * {@code assertAll} per scenario so a failing clause still names itself. The two load-time rejection paths stay their
 * own tests: an {@code assertThrows} is not a positive clause and must not hide behind one.
 */
public class DebuffTest {
    private static final int OWNER = 1003;
    private static final int ALLY = 1202;
    private static final int LEVEL = 80;

    // ==================================================================
    // 1. The classification
    // ==================================================================

    /**
     * Every buff class that can be built without a rule, and the side the game gives it.
     *
     * <p>L3: the six single-clause cases share this fixture (nothing but constructed buffs) and this observable
     * ({@code isDebuff()}), so they are one scenario in which each entry names its class.
     */
    @Test
    public void theClassificationIsDecidedPerBuffClass() {
        Assertions.assertAll("every buff class answers for itself",
                () -> Assertions.assertTrue(new DotBuff(attacker(), DamageElement.FIRE, 100, 2).isDebuff(),
                        "Burn (dot:burn) / Shock (dot:shock) / ... are DoT Debuffs (持续伤害类负面状态)"),
                () -> Assertions.assertTrue(new StunBuff(1).isDebuff(), "a control state is negative on its bearer"),
                () -> Assertions.assertTrue(new TauntBuff(1).isDebuff(),
                        "Taunt (嘲讽) forces the bearer's targeting -- negative on the one carrying it"),
                () -> Assertions.assertTrue(new VulnerabilityBuff(1, 0.5).isDebuff(),
                        "Vulnerability (易伤) is the defender-side debuff"),
                () -> Assertions.assertFalse(new ReductionBuff(1, 0.5).isDebuff(),
                        "DMG Reduction (减伤) sits on the defender too, but it is a POSITIVE effect -- where a buff sits does not "
                                + "decide its side, the text does"),
                () -> Assertions.assertFalse(new StateBuff("char:1309/concerto", 2).isDebuff(),
                        "a named state's side belongs to the rule that applied it (【char:1309/concerto】 (Concerto) vs 【失重】), so the safe "
                                + "default is 'not a debuff' and APPLY_BUFF gains the side when content needs it"));
    }

    /**
     * A stat modifier answers by its {@code sourceRole}: the same sign that decided which half of the attribute it
     * landed in.
     */
    @Test
    public void aStatModifierIsNegativeExactlyWhenItsSignWas() {
        Battle battle = battleWith(modifierRule(-0.3), modifierRule(0.5));
        Character owner = battle.characters().getFirst();

        fire(battle, owner);

        List<StatModifierBuff> modifiers = owner.getBuffManager().allBuffsOf(StatModifierBuff.class);
        long debuffs = modifiers.stream().filter(AbstractBuff::isDebuff).count();
        Assertions.assertAll("the sign the rule wrote decides the modifier's side",
                () -> Assertions.assertEquals(2, modifiers.size(), "precondition: one debuff and one buff"),
                () -> Assertions.assertEquals(1, debuffs,
                        "「攻击力 -30%」 (ATK -30%) is a debuff and 「攻击力 +50%」 (ATK +50%) is not, both decided by the sign the rule wrote"));
    }

    // ==================================================================
    // 2. The count, and what a dispel takes
    // ==================================================================

    /**
     * The count and the dispel read the same bookkeeping: {@code debuffCount()} and {@code removeDebuffs(int)}
     * both ask {@code isDebuff()}, so the clause "only negative effects count" and the clause "a dispel takes only
     * negative effects" share one fixture and one observable.
     *
     * <p>L3: the four single-clause cases below are one scenario. Each drives its own battle from the same
     * {@code battleWith(...)} fixture, so the count, the newest-first removal, the untouched buff, the no-op and
     * "two at once" still keep their own fixture while one {@code assertAll} names every clause.
     */
    @Test
    public void theCountAndTheDispelTakeOnlyNegativeEffects() {
        Battle counted = battleWith();
        Character countedOwner = counted.characters().getFirst();
        countedOwner.getBuffManager().addBuff(new DotBuff(countedOwner, DamageElement.FIRE, 100, 3));
        countedOwner.getBuffManager().addBuff(new VulnerabilityBuff(3, 0.2));
        countedOwner.getBuffManager().addBuff(new StateBuff("char:1309/concerto", 3));
        countedOwner.getBuffManager().addBuff(new ReductionBuff(3, 0.2));
        int count = countedOwner.getBuffManager().debuffCount();

        Battle oneTaken = battleWith(dispelRule(1));
        Character oneTakenOwner = oneTaken.characters().getFirst();
        oneTakenOwner.getBuffManager().addBuff(new StateBuff("char:1309/concerto", 3));
        oneTakenOwner.getBuffManager().addBuff(new DotBuff(oneTakenOwner, DamageElement.FIRE, 100, 3));
        oneTakenOwner.getBuffManager().addBuff(new VulnerabilityBuff(3, 0.2));
        fire(oneTaken, oneTakenOwner);

        Battle nothingToTake = battleWith(dispelRule(1));
        Character nothingToTakeOwner = nothingToTake.characters().getFirst();
        nothingToTakeOwner.getBuffManager().addBuff(new StateBuff("char:1309/concerto", 3));
        int fired = fire(nothingToTake, nothingToTakeOwner);

        Battle twoTaken = battleWith(dispelRule(2));
        Character twoTakenOwner = twoTaken.characters().getFirst();
        twoTakenOwner.getBuffManager().addBuff(new DotBuff(twoTakenOwner, DamageElement.FIRE, 100, 3));
        twoTakenOwner.getBuffManager().addBuff(new VulnerabilityBuff(3, 0.2));
        fire(twoTaken, twoTakenOwner);

        Assertions.assertAll("the count and the dispel take debuffs only",
                () -> Assertions.assertEquals(2, count,
                        "the DOT and the vulnerability count; the named state and the reduction do not"),
                () -> Assertions.assertEquals(1, oneTakenOwner.getBuffManager().debuffCount(),
                        "one debuff came off (the newest)"),
                () -> Assertions.assertTrue(oneTakenOwner.getBuffManager().hasBuff(DotBuff.class),
                        "the older debuff is still there -- one dispel removes one"),
                () -> Assertions.assertTrue(oneTakenOwner.getBuffManager().hasBuff(StateBuff.class),
                        "and the buff is untouched: Concerto (char:1309/concerto) is not a negative effect"),
                () -> Assertions.assertEquals(1, fired, "the rule fires; there is simply nothing negative"),
                () -> Assertions.assertTrue(nothingToTakeOwner.getBuffManager().hasBuff(StateBuff.class),
                        "the only buff it carries is still there after the no-op dispel"),
                () -> Assertions.assertEquals(0, twoTakenOwner.getBuffManager().debuffCount(),
                        "dispelling two at once takes both"));
    }

    // ==================================================================
    // 3. The condition
    // ==================================================================

    /**
     * The count is the <b>subject's</b>: the event's target supplies it, an event with no subject reads NaN (which
     * fails every comparison instead of passing it), and the rule's owner having debuffs of its own must not
     * satisfy it.
     *
     * <p>L3: both clauses share the fixture ({@code withAlly(...)} plus a {@code target_debuff_count} gate) and the
     * observable (what {@code fire} returns), so they are one scenario.
     */
    @Test
    public void theDebuffCountConditionReadsTheEventSubject() {
        Battle subject = withAlly(TriggerSpecs.rule("ALLY_ATTACK", List.of("target_debuff_count >= 2"), gain(1)));
        Character actor = subject.characters().getFirst();
        Enemy subjectEnemy = subject.enemyUnits().getFirst();
        subjectEnemy.getBuffManager().addBuff(new DotBuff(actor, DamageElement.FIRE, 100, 3));
        subjectEnemy.getBuffManager().addBuff(new VulnerabilityBuff(3, 0.2));
        int withTwo = subject.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(
                TriggerEvent.ALLY_ATTACK, actor, subjectEnemy, 1, 0));

        subjectEnemy.getBuffManager().removeOneBuff(VulnerabilityBuff.class);
        int withOne = subject.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(
                TriggerEvent.ALLY_ATTACK, actor, subjectEnemy, 1, 0));

        int withoutSubject = subject.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(
                TriggerEvent.ALLY_ATTACK, actor, null, 1, 0));

        Battle ownerSide = withAlly(TriggerSpecs.rule("ALLY_ATTACK", List.of("target_debuff_count >= 1"), gain(1)));
        Character ownerSideActor = ownerSide.characters().getFirst();
        Enemy ownerSideEnemy = ownerSide.enemyUnits().getFirst();
        ownerSideActor.getBuffManager().addBuff(new DotBuff(ownerSideActor, DamageElement.FIRE, 100, 3));
        int fromOwnDebuffs = ownerSide.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(
                TriggerEvent.ALLY_ATTACK, ownerSideActor, ownerSideEnemy, 1, 0));

        Assertions.assertAll("the debuff count is the event subject's",
                () -> Assertions.assertEquals(1, withTwo, "the subject carries two debuffs"),
                () -> Assertions.assertEquals(0, withOne, "now it carries one, so 「>= 2」 fails"),
                () -> Assertions.assertEquals(0, withoutSubject,
                        "an event with no subject reads NaN, which fails every comparison instead of passing it"),
                () -> Assertions.assertEquals(0, fromOwnDebuffs, "MY debuffs are not the target's debuffs"));
    }

    // ==================================================================
    // 4. Fail fast at load time
    // ==================================================================

    @Test
    public void aDispelNeedsAPositiveAmountAndNoDuration() {
        IllegalArgumentException zero = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER, List.of(dispelRule(0))));
        Assertions.assertTrue(zero.getMessage().contains("positive"), zero.getMessage());

        EffectSpec withTurns = dispelOp(1);
        TriggerSpecs.set(withTurns, "turns", 2);
        IllegalArgumentException turns = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER, List.of(TriggerSpecs.rule("ALLY_ATTACK", null, withTurns))));
        Assertions.assertTrue(turns.getMessage().contains("turns"), turns.getMessage());
    }

    @Test
    public void anUnknownNumericVariableStillListsTheKnownOnes() {
        IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER, List.of(TriggerSpecs.rule("ALLY_ATTACK",
                        List.of("debuff_count >= 1"), gain(1)))));
        // the message must name the variable that DOES exist, or the author cannot find it
        Assertions.assertTrue(e.getMessage().contains("target_debuff_count"), e.getMessage());
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static Character attacker() {
        return CharacterFactory.create(OWNER, LEVEL);
    }

    private static TriggerSpec dispelRule(double amount) {
        return TriggerSpecs.rule("ALLY_ATTACK", null, dispelOp(amount));
    }

    private static EffectSpec dispelOp(double amount) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "DISPEL");
        TriggerSpecs.set(effect, "amount", amount);
        return effect;
    }

    private static TriggerSpec modifierRule(double percent) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "MODIFY_ATTR");
        TriggerSpecs.set(effect, "attribute", AttributeType.ATTACK.name());
        TriggerSpecs.set(effect, "percent", percent);
        TriggerSpecs.set(effect, "turns", 3);
        return TriggerSpecs.rule("ALLY_ATTACK", null, effect);
    }

    private static EffectSpec gain(double amount) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "GAIN_SKILL_POINT");
        TriggerSpecs.set(effect, "amount", amount);
        return effect;
    }

    private static Battle battleWith(TriggerSpec... specs) {
        Battle battle = BattleFixture.battle(List.of(ownerWith(specs)), List.of(dummy()));
        battle.startBattle();
        return battle;
    }

    private static Battle withAlly(TriggerSpec... specs) {
        Battle battle = new Battle(List.of(ownerWith(specs), CharacterFactory.create(ALLY, LEVEL)),
                List.of(dummy()), BattleFixture.seeded());
        battle.startBattle();
        return battle;
    }

    private static Character ownerWith(TriggerSpec... specs) {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(specs)));
        return owner;
    }

    private static int fire(Battle battle, Character actor) {
        return battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, actor, null, 1, 0));
    }

    private static Enemy dummy() {
        return Enemy.fromAttributes("dummy", 1_000_000, 0, 100, 100);
    }
}
