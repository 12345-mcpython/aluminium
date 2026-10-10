package com.laosun.aluminium.test.engine;


import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.buff.StateBuff;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * {@code holder_attr:<STATE>:<ATTRIBUTE>}: a magnitude that is a share of the attribute of the unit <b>holding a
 * state</b>.
 *
 * <p>The reader is 1503 Pearl (真珠)'s enhanced basic and her Eidolon 6: "攻击后，额外造成 60% 的冰属性欢愉伤害，该次欢愉伤害
 * 使用<b>【美学底本】</b>的属性值计算" - the instance's magnitude is read off the ally her ultimate marked as the Aesthetic
 * Archetype (美学底本). That unit is chosen in combat and is not a slot in the roster, so none of the three spellings
 * that already existed can name it: {@code self_attr:} reads the rule OWNER, {@code summon_attr:} the owner's memosprite,
 * and {@code actor_attr:} whoever the event is about.
 *
 * <p>This judge is deliberately three-way, because the two wrong readings are both plausible and both produce a
 * number: the gain must equal the <b>holder's</b> share, and must not equal the owner's nor the other ally's. The
 * three units have different Defence, so "read the wrong unit" shows up as a different number rather than as a pass -
 * and the control run (nobody holds the state) is asserted separately, because "the holder is missing" is an ERROR
 * here, not a zero.
 */
public class HolderAttrScaleTest {
    private static final int LEVEL = 80;
    private static final int OWNER = 1503;      // Pearl (真珠) herself: the rule owner, who must NOT be read
    private static final int HOLDER = 1002;     // the ally the sentence is about
    private static final int BYSTANDER = 1202;  // the other ally: also not to be read
        private static final String MARK = "美学底本";
    private static final double SHARE = 0.6;

    /**
     * The share is of the HOLDER - not of the rule owner, and not of any other ally.
     *
     * <p>Note: The three-way reading is the whole point. A resolver that fell back to the owner (the easy mistake,
     * since every other attribute spelling in this family starts there) would land on a DIFFERENT number here and be
     * caught by the third assertion rather than passing silently.
     */
    @Test
    public void theShareIsOfTheHolderNotTheOwner() {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        Character holder = CharacterFactory.create(HOLDER, LEVEL);
        Character bystander = CharacterFactory.create(BYSTANDER, LEVEL);
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(
                TriggerSpecs.rule("SKILL_CAST", List.of("actor == self"), holderDefenceShare()))));
        Battle battle = new Battle(List.of(owner, holder, bystander),
                List.of(BattleFixture.monster()), new Random(0));
        battle.startBattle();

        double ownerBefore = owner.getAttribute(AttributeType.DEFENCE).get();
        double holderBefore = holder.getAttribute(AttributeType.DEFENCE).get();
        double bystanderBefore = bystander.getAttribute(AttributeType.DEFENCE).get();

        // Precondition, asserted rather than assumed: the three readings have to be DISTINGUISHABLE, or the judge
        // below proves nothing. (This is the "make the mutant die" half of the fixture.)
        Assertions.assertNotEquals(ownerBefore, holderBefore, 1e-6,
                "precondition: the owner's Defence differs from the holder's, so the two readings are distinguishable");
        Assertions.assertNotEquals(bystanderBefore, holderBefore, 1e-6,
                "precondition: the bystander's Defence differs from the holder's too");

        holder.getBuffManager().addBuff(new StateBuff(MARK, 3, false));
        battle.fireTriggers(TriggerEvent.SKILL_CAST, owner, holder, 0, 0);
        battle.processRequests();

        double gained = holder.getAttribute(AttributeType.DEFENCE).get() - holderBefore;
        double fromHolder = SHARE * holderBefore;
        double fromOwner = SHARE * ownerBefore;
        System.out.println("[holder_attr] DEF: owner " + ownerBefore + " | holder " + holderBefore + " | bystander "
                + bystanderBefore + " -> the holder gained " + gained + " ; 60% of the holder = " + fromHolder
                + " ; 60% of the owner = " + fromOwner);

        Assertions.assertEquals(fromHolder, gained, Math.abs(fromHolder) * 1e-6,
                "the raise is 60% of 【" + MARK + "】's own Defence - the unit HOLDING the state");
        Assertions.assertNotEquals(fromOwner, gained, Math.abs(fromHolder) * 1e-6,
                "and not 60% of the rule owner's (the spelling every other member of this family would have read)");
        Assertions.assertEquals(bystanderBefore, bystander.getAttribute(AttributeType.DEFENCE).get(), 1e-6,
                "and the ally who holds nothing is untouched");

        // The target selector and the scale agree about WHO the holder is: the effect landed on the holder, and the
        // number came from the holder. One lookup, two readings.
        Assertions.assertEquals(ownerBefore, owner.getAttribute(AttributeType.DEFENCE).get(), 1e-6,
                "the rule owner is not the holder, so nothing lands on it either");
    }

    /** Nobody holds the state: the scale has no unit to read, and says so instead of quietly reading the owner. */
    @Test
    public void nobodyHoldingTheStateIsAnErrorRatherThanTheOwnersOwnValue() {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        Character bystander = CharacterFactory.create(BYSTANDER, LEVEL);
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(
                TriggerSpecs.rule("SKILL_CAST", List.of("actor == self"), holderDefenceShare()))));
        Battle battle = new Battle(List.of(owner, bystander),
                List.of(BattleFixture.monster()), new Random(0));
        battle.startBattle();
        double ownerBefore = owner.getAttribute(AttributeType.DEFENCE).get();

        IllegalStateException refused = Assertions.assertThrows(IllegalStateException.class,
                () -> battle.fireTriggers(TriggerEvent.SKILL_CAST, owner, bystander, 0, 0),
                "a share of a unit that is not on the field is not a number");

        Assertions.assertTrue(refused.getMessage().contains(MARK), refused.getMessage());
        Assertions.assertEquals(ownerBefore, owner.getAttribute(AttributeType.DEFENCE).get(), 1e-6,
                "and the failure did not leave a half-applied modifier behind");
    }

    /**
     * The SECOND half of the spelling really is read: the same state, a different attribute, a different number.
     *
     * <p>Why this test exists (it was added because a mutation survived without it): with only {@code :DEFENCE}
     * exercised, a reader that discarded the attribute and always used Defence passed every assertion. The two
     * attributes differ by orders of magnitude on the same unit, so "which stat was named" is not a detail here.
     */
    @Test
    public void theNamedAttributeIsTheOneThatIsRead() {
        double onFirst = riderDamageReading(HOLDER, "ATTACK");
        double onSecond = riderDamageReading(BYSTANDER, "ATTACK");
        double firstAttack = CharacterFactory.create(HOLDER, LEVEL).getAttribute(AttributeType.ATTACK).get();
        double secondAttack = CharacterFactory.create(BYSTANDER, LEVEL).getAttribute(AttributeType.ATTACK).get();
        System.out.println("[holder_attr] the named attribute: rider scaled by holder_attr:ATTACK = " + onFirst
                + " (state on " + HOLDER + ", ATK " + firstAttack + ") vs " + onSecond + " (state on " + BYSTANDER
                + ", ATK " + secondAttack + ") ; ratio = " + (onFirst / onSecond) + " , the ATK ratio = "
                + (firstAttack / secondAttack));

        Assertions.assertTrue(onFirst > 0 && onSecond > 0, "the rider fires in both runs");
        Assertions.assertEquals(firstAttack / secondAttack, onFirst / onSecond,
                Math.abs(firstAttack / secondAttack) * 1e-6,
                "the damage ratio tracks the holders' ATTACK ratio, so the attribute half of the spelling was read"
                        + " (a reader stuck on DEFENCE would have produced the DEF ratio instead: measured, the two"
                        + " ratios differ)");
    }

    /** The shape is checked while the FILE is read: the state alone does not say which stat to read. */
    @Test
    public void aStateWithoutAnAttributeIsRefusedAtLoadTime() {
        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER, List.of(
                        TriggerSpecs.rule("SKILL_CAST", List.of("actor == self"),
                                scale("holder_attr:" + MARK, "MODIFY_ATTR", "DEFENCE")))));

        Assertions.assertTrue(refused.getMessage().contains("STATE>:<ATTRIBUTE>"), refused.getMessage());
        Assertions.assertTrue(refused.getMessage().contains(TriggerSpecs.TEST_SOURCE), refused.getMessage());
    }

    /**
     * And it is a DAMAGE scale as well - the reader's own sentence.
     *
     * <p>Why a second test and not a second assertion: the two ops reach the scale through DIFFERENT readers
     * ({@code literalBase} for {@code DAMAGE}, {@code derivedMagnitude} for {@code ADD_DAMAGE}), so a spelling that
     * works for one and throws on the other is exactly the half-wired state this project keeps finding. The rider is
     * measured against its own control (same attack, same monster, the state moved to the other ally), so the damage
     * zones - which do not cancel in an absolute reading - cancel in the comparison only to the extent the two runs
     * are identical, which they are.
     */
    @Test
    public void damageReadsTheHoldersAttributeToo() {
        double onFirst = riderDamage(HOLDER);
        double onSecond = riderDamage(BYSTANDER);
        double firstDefence = CharacterFactory.create(HOLDER, LEVEL).getAttribute(AttributeType.DEFENCE).get();
        double secondDefence = CharacterFactory.create(BYSTANDER, LEVEL).getAttribute(AttributeType.DEFENCE).get();
        System.out.println("[holder_attr] rider damage: with 【" + MARK + "】 on " + HOLDER + " (DEF " + firstDefence
                + ") = " + onFirst + " ; on " + BYSTANDER + " (DEF " + secondDefence + ") = " + onSecond
                + " ; ratio = " + (onFirst / onSecond) + " and the DEF ratio = " + (firstDefence / secondDefence));

        Assertions.assertTrue(onFirst > 0 && onSecond > 0, "the rider fires in both runs");
        // Note: The ratio, not the absolute numbers. The rider is a BASE, so the zones (defence, resistance, level
        // difference) multiply it - and those zones are the SAME in both runs, because the only thing that moves is
        // which ally holds the state. Their own DEF ratio therefore has to survive into the damage ratio; a fixed
        // magnitude, or one read off the attacker, would come out at exactly 1.0 here.
        Assertions.assertEquals(firstDefence / secondDefence, onFirst / onSecond,
                Math.abs(firstDefence / secondDefence) * 1e-6,
                "each run's rider is 60% of THAT run's holder, so the damage ratio is the holders' DEF ratio"
                        + " (measured " + onFirst + " vs " + onSecond + ")");
    }

    /** No holder, a DAMAGE rider: the same loud refusal as the modifier path, rather than a silent zero instance. */
    @Test
    public void aDamageRiderWithNoHolderIsRefused() {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        Character bystander = CharacterFactory.create(BYSTANDER, LEVEL);
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(
                TriggerSpecs.rule("SKILL_CAST", List.of("actor == self"), defensiveRider()))));
        Battle battle = new Battle(List.of(owner, bystander),
                List.of(BattleFixture.monster()), new Random(0));
        battle.startBattle();

        IllegalStateException refused = Assertions.assertThrows(IllegalStateException.class,
                () -> battle.fireTriggers(TriggerEvent.SKILL_CAST, owner, bystander, 0, 0),
                "a DAMAGE scaled by a state-holder reads the same lookup, so an absent holder is the same error");
        Assertions.assertTrue(refused.getMessage().contains(MARK), refused.getMessage());
    }

    /** The rider's damage when the state is on the given ally (the other ally holds nothing), reading DEFENCE. */
    private static double riderDamage(int markedCid) {
        return riderDamageReading(markedCid, "DEFENCE");
    }

    /** The same, for whichever of the holder's attributes the spelling names. */
    private static double riderDamageReading(int markedCid, String attribute) {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        Character first = CharacterFactory.create(HOLDER, LEVEL);
        Character second = CharacterFactory.create(BYSTANDER, LEVEL);
        Character marked = markedCid == HOLDER ? first : second;

        owner.setTriggerTable(new TriggerTable(OWNER, List.of(
                TriggerSpecs.rule("SKILL_CAST", List.of("actor == self"), riderOn(attribute)))));
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(owner, first, second), List.of(enemy), new Random(0));
        battle.startBattle();
        battle.processRequests();

        marked.getBuffManager().addBuff(new StateBuff(MARK, 3, false));
        double before = enemy.getCurrentHp();
        battle.fireTriggers(TriggerEvent.SKILL_CAST, owner, marked, 0, 0);
        battle.processRequests();
        return before - enemy.getCurrentHp();
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /** "使<持有【美学底本】的目标>的防御力提高，等同于其自身防御力的 60%" - the shape the scale is written in. */
    private static EffectSpec holderDefenceShare() {
        EffectSpec effect = withTurns(scale("holder_attr:" + MARK + ":DEFENCE", "MODIFY_ATTR", "DEFENCE"));
        // And the target is the same lookup as the scale: one answer to "who holds it", two readings. (Its own test
        // is BondmateHolderTest; here it keeps the effect from landing on the owner and hiding a wrong-unit read.)
        TriggerSpecs.set(effect, "target", "holder_of:" + MARK);
        return effect;
    }

    /** A literal-ratio DAMAGE whose magnitude is 60% of the state-holder's Defence. */
    private static EffectSpec defensiveRider() {
        return riderOn("DEFENCE");
    }

    /** A literal-ratio DAMAGE whose magnitude is 60% of the named attribute of the state-holder. */
    private static EffectSpec riderOn(String attribute) {
        EffectSpec effect = scale("holder_attr:" + MARK + ":" + attribute, "DAMAGE", null);
        TriggerSpecs.set(effect, "element", "Ice");
        TriggerSpecs.set(effect, "target", "all_enemies");
        return effect;
    }

    /** A modifier with a stated lifetime: {@code MODIFY_ATTR} refuses to load without one. */
    private static EffectSpec withTurns(EffectSpec effect) {
        TriggerSpecs.set(effect, "turns", 3);
        return effect;
    }

    /** One effect with the op, the scale and (for a modifier) the attribute it raises. */
    private static EffectSpec scale(String scale, String op, String attribute) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", op);
        TriggerSpecs.set(effect, "scale", scale);
        TriggerSpecs.set(effect, "percent", SHARE);
        if (attribute != null) {
            TriggerSpecs.set(effect, "attribute", attribute);
        }
        return effect;
    }
}
