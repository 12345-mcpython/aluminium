package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.buff.AbstractBuff;
import com.laosun.aluminium.models.buff.DotBuff;
import com.laosun.aluminium.models.buff.ReductionBuff;
import com.laosun.aluminium.models.buff.StatModifierBuff;
import com.laosun.aluminium.models.buff.StateBuff;
import com.laosun.aluminium.models.buff.StunBuff;
import com.laosun.aluminium.models.buff.TauntBuff;
import com.laosun.aluminium.models.buff.VulnerabilityBuff;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Negative effects: what counts as one, how many are attached, and how 「解除 N 个负面效果」 removes them.
 *
 * <p><b>Why the classification is the centre of this class.</b> Two ops and one condition all depend on the same
 * question — "is this buff a debuff?" — and getting it wrong is invisible: a dispel that quietly removes a
 * <i>shield</i> instead of a burn looks like a working rule until somebody reads the log. So the answer is
 * decided in exactly one place ({@code AbstractBuff.isDebuff()}), the set of classes that answer "yes" is pinned
 * here as a table, and each entry says why in the game's own terms.
 *
 * <p>The interesting entries are the ones that <i>don't</i> follow from where the buff sits: 减伤
 * ({@code ReductionBuff}) is a positive effect on the defender, 易伤 ({@code VulnerabilityBuff}) is a negative
 * one, 嘲讽 is negative <b>on its bearer</b>, and a named state's side depends on who applied it — which is why a
 * state answers "no" and says so.
 */
public class DebuffTest {
    private static final int OWNER = 1003;
    private static final int ALLY = 1202;
    private static final int LEVEL = 80;

    // ==================================================================
    // 1. The classification
    // ==================================================================

    /** Every buff class that can be built without a rule, and the side the game gives it. */
    @Test
    public void theClassificationIsDecidedPerBuffClass() {
        Assertions.assertTrue(new DotBuff(attacker(), DamageElement.FIRE, 100, 2).isDebuff(),
                "灼烧/触电/… are 持续伤害类负面状态");
        Assertions.assertTrue(new StunBuff(1).isDebuff(), "a control state is negative on its bearer");
        Assertions.assertTrue(new TauntBuff(1).isDebuff(),
                "嘲讽 forces the bearer's targeting -- negative on the one carrying it");
        Assertions.assertTrue(new VulnerabilityBuff(1, 0.5).isDebuff(), "易伤 is the defender-side debuff");

        Assertions.assertFalse(new ReductionBuff(1, 0.5).isDebuff(),
                "减伤 sits on the defender too, but it is a POSITIVE effect -- where a buff sits does not "
                        + "decide its side, the text does");
        Assertions.assertFalse(new StateBuff("协奏", 2).isDebuff(),
                "a named state's side belongs to the rule that applied it (【协奏】 vs 【失重】), so the safe "
                        + "default is 'not a debuff' and APPLY_BUFF gains the side when content needs it");
    }

    /**
     * A stat modifier answers by its {@code sourceRole}: the same sign that decided which half of the attribute it
     * landed in.
     */
    @Test
    public void aStatModifierIsNegativeExactlyWhenItsSignWas() {
        Battle battle = battleWith(modifierRule(-0.3), modifierRule(0.5));
        Character owner = battle.characters.getFirst();

        fire(battle, owner);

        List<StatModifierBuff> modifiers = owner.getBuffManager().allBuffsOf(StatModifierBuff.class);
        Assertions.assertEquals(2, modifiers.size(), "precondition: one debuff and one buff");
        long debuffs = modifiers.stream().filter(AbstractBuff::isDebuff).count();
        Assertions.assertEquals(1, debuffs,
                "「攻击力 −30%」 is a debuff and 「攻击力 +50%」 is not, both decided by the sign the rule wrote");
    }

    // ==================================================================
    // 2. The count, and what a dispel takes
    // ==================================================================

    @Test
    public void theCountIsDebuffsOnly() {
        Battle battle = battleWith();
        Character owner = battle.characters.getFirst();
        owner.getBuffManager().addBuff(new DotBuff(owner, DamageElement.FIRE, 100, 3));
        owner.getBuffManager().addBuff(new VulnerabilityBuff(3, 0.2));
        owner.getBuffManager().addBuff(new StateBuff("协奏", 3));
        owner.getBuffManager().addBuff(new ReductionBuff(3, 0.2));

        Assertions.assertEquals(2, owner.getBuffManager().debuffCount(),
                "the DOT and the vulnerability count; the named state and the reduction do not");
    }

    /**
     * The property that matters: a dispel takes debuffs and <b>never</b> a buff, newest first.
     */
    @Test
    public void aDispelTakesTheNewestDebuffAndLeavesTheBuffsAlone() {
        Battle battle = battleWith(dispelRule(1));
        Character owner = battle.characters.getFirst();
        owner.getBuffManager().addBuff(new StateBuff("协奏", 3));
        owner.getBuffManager().addBuff(new DotBuff(owner, DamageElement.FIRE, 100, 3));
        owner.getBuffManager().addBuff(new VulnerabilityBuff(3, 0.2));

        fire(battle, owner);

        Assertions.assertEquals(1, owner.getBuffManager().debuffCount(), "one debuff came off (the newest)");
        Assertions.assertTrue(owner.getBuffManager().hasBuff(DotBuff.class),
                "the older debuff is still there -- one dispel removes one");
        Assertions.assertTrue(owner.getBuffManager().hasBuff(StateBuff.class),
                "and the buff is untouched: 协奏 is not a negative effect");
    }

    @Test
    public void aDispelWithNothingToRemoveIsANoOp() {
        Battle battle = battleWith(dispelRule(1));
        Character owner = battle.characters.getFirst();
        owner.getBuffManager().addBuff(new StateBuff("协奏", 3));

        Assertions.assertEquals(1, fire(battle, owner), "the rule fires; there is simply nothing negative");
        Assertions.assertTrue(owner.getBuffManager().hasBuff(StateBuff.class));
    }

    @Test
    public void dispellingTwoAtOnceTakesBoth() {
        Battle battle = battleWith(dispelRule(2));
        Character owner = battle.characters.getFirst();
        owner.getBuffManager().addBuff(new DotBuff(owner, DamageElement.FIRE, 100, 3));
        owner.getBuffManager().addBuff(new VulnerabilityBuff(3, 0.2));

        fire(battle, owner);
        Assertions.assertEquals(0, owner.getBuffManager().debuffCount());
    }

    // ==================================================================
    // 3. The condition
    // ==================================================================

    @Test
    public void theDebuffCountConditionReadsTheEventSubject() {
        Battle battle = withAlly(TriggerSpecs.rule("ALLY_ATTACK", List.of("target_debuff_count >= 2"), gain(1)));
        Character actor = battle.characters.getFirst();
        Enemy dummy = battle.enemyUnits().getFirst();
        dummy.getBuffManager().addBuff(new DotBuff(actor, DamageElement.FIRE, 100, 3));
        dummy.getBuffManager().addBuff(new VulnerabilityBuff(3, 0.2));

        Assertions.assertEquals(1, battle.fireTriggers(TriggerEvent.ALLY_ATTACK, actor, dummy, 1, 0),
                "the subject carries two debuffs");

        dummy.getBuffManager().removeOneBuff(VulnerabilityBuff.class);
        Assertions.assertEquals(0, battle.fireTriggers(TriggerEvent.ALLY_ATTACK, actor, dummy, 1, 0),
                "now it carries one, so 「>= 2」 fails");

        Assertions.assertEquals(0, battle.fireTriggers(TriggerEvent.ALLY_ATTACK, actor, null, 1, 0),
                "an event with no subject reads NaN, which fails every comparison instead of passing it");
    }

    /** The count is the <b>subject's</b>: the rule's owner having debuffs of its own must not satisfy it. */
    @Test
    public void theCountDoesNotReadTheRulesOwner() {
        Battle battle = withAlly(TriggerSpecs.rule("ALLY_ATTACK", List.of("target_debuff_count >= 1"), gain(1)));
        Character actor = battle.characters.getFirst();
        Enemy dummy = battle.enemyUnits().getFirst();
        actor.getBuffManager().addBuff(new DotBuff(actor, DamageElement.FIRE, 100, 3));

        Assertions.assertEquals(0, battle.fireTriggers(TriggerEvent.ALLY_ATTACK, actor, dummy, 1, 0),
                "MY debuffs are not the target's debuffs");
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

    private static int fire(Battle battle, Character actor) {
        return battle.fireTriggers(TriggerEvent.ALLY_ATTACK, actor, null, 1, 0);
    }

    private static Enemy dummy() {
        return Enemy.fromAttributes("dummy", 1_000_000, 0, 100, 100);
    }
}
