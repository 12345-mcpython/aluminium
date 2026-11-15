package com.laosun.aluminium.test.content.characters.blade;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Signal;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.buff.StateBuff;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.trigger.SkillCastability;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 1205 刃 (Blade), the whole kit in one class: the 【地狱变】 state and its three clauses, the enhanced Basic's mixed
 * base, the Talent's charge and its follow-up, the Technique's Max-HP opening, and the three Eidolons measured here.
 *
 * <p><b>Why one class.</b> These were seven files in the same package, each written against one clause. Merging them
 * changes no assertion and no expected value - it puts one character's criteria side by side, in the order the kit is
 * read: the state, the Basic it swaps in, the Talent the state feeds, then the Eidolons that modify each of them.
 *
 * <p><b>【地狱变】</b> 「处于【地狱变】状态时无法施放战技，同时使自身造成的伤害提高 40%，普攻【支离剑】强化为【无间剑树】」.
 * All three clauses hang off one state, and each has its own wrong-but-plausible spelling: the ban is not "the Skill
 * cannot be selected" but {@code blocks: skill} read by the one castability gate; the +40% must be a
 * <b>follow-the-state</b> modifier, not an equal-duration buff, or it survives the state; and the swap must land on the
 * Basic slot (data slot 8), not on the Skill slot. The 3-turn expire is the engine's own timer and is not measured here -
 * what is measured is that the modifier is dropped by the <b>gate</b> the moment the state is gone.
 *
 * <p><b>Eidolon 2</b> 「刃处于[地狱变]状态时，暴击率提高 15%」: the clause is gated on the state, so it pays nothing with
 * no state, and the row's own value is 0.15.
 *
 * <p><b>Enhanced Basic 无间剑树</b> 「对指定敌方单体造成等同于刃 #2% 攻击力 + #4% 生命上限的风属性伤害，同时对其相邻目标
 * 造成等同于刃 #3% 攻击力 + #5% 生命上限的风属性伤害」. The row is {@code [0.1, 0.56, 0.224, 1.4, 0.56]}: the FIRST
 * cell is a HP cost, the centre is the sum of two columns scaled by two different attributes, and the neighbours sum two
 * more. Before {@code base_terms} the engine took cell 1 as the whole centre share and multiplied it by ATK, so both the
 * Max-HP term and the correct columns were lost - a wrong number with no symptom. The criterion re-computes the base from
 * the panel and bounds the settled damage by it.
 *
 * <p><b>【地狱变】 entered, the Technique's opening, and the charge cap</b> 「使用秘技后」 pays 40% of his Max HP, measured
 * against a hand-built 50% in the same pipeline. 「最多叠加5层」 is tested by EXCEEDING it (round 192's lesson): twelve
 * hits must never read more than five. Since the Talent's follow-up landed (2026-11-10) reaching the cap CONSUMES every
 * layer, so the counter is 0 right after the fifth hit, never 5; what still holds is the cap itself, and the fifth layer
 * is what pays the follow-up (the Talent's cases below measure that side).
 *
 * <p><b>Talent follow-up and Eidolon 6</b> The follow-up has no cast of its own: its trigger is 「当充能叠加至上限时」,
 * i.e. a COUNTER reaching its cap, and its four effects (damage / heal / consume-all) hang off that one fact. Nothing else
 * in the suite would notice if the trigger fired one layer early or late, or if the charge was not emptied - the damage
 * number would still look plausible. So the cases below count the layers and the settlements on both sides of the cap, at
 * E5 (cap 5) and E6 (cap 4, 「充能层数上限降至 4 层」). The E6 damage half is measured the same way as Eidolon 1: the
 * follow-up's settled number at E6 minus its settled number at E5 must equal <b>0.5 x Max HP</b> exactly, because the
 * raise is an absolute amount added after every zone.
 *
 * <p><b>Eidolon 4</b> 「当前生命值百分比从大于 50% 降低至小于等于 50% 时，生命上限提高 20%，该效果最多叠加 2 层。」
 * Three readings have to be separated, and each has a plausible wrong number: a CROSSING, not "is below half" - the
 * second hit below the line must pay nothing; two HP-loss CHANNELS - damage ({@code HP_LOST}) and the HP he pays himself
 * ({@code HP_CONSUMED}, the Skill's 30%), and the sentence says 「当前生命值百分比降低」 without naming the cause; and a
 * CAP of two layers, so a third crossing must stop at two. Measured on Max HP: 「+20%」 is a share of <b>base</b> HP (the
 * engine's convention for every percentage attribute bonus, which is also the game's: an HP% bonus scales off base HP,
 * not off the current maximum), so the expectation is {@code max + 0.2 x base}, not {@code 1.2 x max}.
 *
 * <p><b>Eidolon 1</b> 「终结技对<b>指定敌方单体</b>造成的伤害额外提高，提高数值等同于刃 <b>150%</b> 本场战斗中累计的已损失
 * 生命值。」 Two facts decide the number, and each has a wrong reading that looks right: <b>which victims</b> - the
 * Ultimate is a Blast (skills.json 120503), so 「指定敌方单体」 is the centre and the neighbours are the other sentence (a
 * rule that boosted every instance would raise the neighbours too); and <b>where the raise enters</b> - it is an
 * absolute amount on the <b>settled</b> instance, not a percentage and not another crit-rolling hit, so the measure below
 * is the exact delta 1.5 x the tally, with no zone factor. The control is the same cast at E0 with the same tally.
 */
public class BladeTest {
    private static final int BLADE = 1205;
    private static final int LEVEL = 80;
    private static final String STATE = "地狱变";
    private static final String TALLY = "累计损失生命";
    private static final String CHARGE = "char:1205/charge";

    // ==================================================================
    // 【地狱变】 - the state and its three clauses
    // ==================================================================

    /**
     * 「无法施放战技」: the Skill slot is refused while the state holds, and only that slot.
     */
    @Test
    public void theStateForbidsTheSkillSlotAlone() {
        Fixture f = new Fixture();
        Assertions.assertTrue(SkillCastability.allows(f.skill(), f.blade, f.battle),
                "before the state the Skill is castable");

        f.enterHellscape();

        Assertions.assertFalse(SkillCastability.allows(f.skill(), f.blade, f.battle),
                "「处于【地狱变】状态时无法施放战技」");
        Assertions.assertTrue(SkillCastability.allows(f.blade.getSkills().get(SkillType.COMMON), f.blade, f.battle),
                "only the Skill is banned - the Basic still works");
    }

    /**
     * 「使自身造成的伤害提高 40%」 - and it follows the state, not a timer of its own.
     */
    @Test
    public void theDamageBoostLastsOnlyAsLongAsTheState() {
        Fixture f = new Fixture();
        double before = f.boost();
        f.enterHellscape();
        Assertions.assertEquals(0.4, f.boost() - before, 1e-9, "「伤害提高 40%」");

        f.blade.getBuffManager().removeState(STATE);
        f.battle.setCurrentMove(new Signal(f.blade));
        f.battle.beforeMove();

        Assertions.assertEquals(before, f.boost(), 1e-9,
                "the modifier is gated on the state (`while_state`), so the next turn boundary drops it");
    }

    /**
     * 「普攻【支离剑】强化为【无间剑树】」: the Basic slot is swapped to data slot 8.
     */
    @Test
    public void theStateSwapsTheBasicForTheEnhancedRow() {
        Fixture f = new Fixture();
        Assertions.assertEquals(1, f.blade.getSkills().get(SkillType.COMMON).getSkillSlot(),
                "precondition: the ordinary Basic");

        f.enterHellscape();

        Assertions.assertEquals(8, f.blade.getSkills().get(SkillType.COMMON).getSkillSlot(),
                "the Basic slot now carries 【无间剑树】 (skills.json slot 8)");
    }

    // ==================================================================
    // Eidolon 2
    // ==================================================================

    @Test
    public void theSecondEidolonRaisesCritRateOnlyWhileTheStateHolds() {
        double with = gain(true);
        double without = gain(false);
        System.out.println("[1205] withState=" + with + " withoutState=" + without);
        Assertions.assertEquals(0.15, with, 1e-6, "the eidolon row's own value");
        Assertions.assertEquals(0.0, without, 1e-9, "no state, no clause");
    }

    // ==================================================================
    // Enhanced Basic 无间剑树 - the mixed base
    // ==================================================================

    @Test
    public void theCentreIsAnAttackTermPlusAMaxHpTerm() {
        Character blade = CharacterFactory.create(BLADE, LEVEL);
        Enemy left = BattleFixture.monster();
        Enemy centre = BattleFixture.monster();
        Enemy right = BattleFixture.monster();
        for (Enemy enemy : List.of(left, centre, right)) {
            enemy.setMaxStance(100_000);
            enemy.setStance(100_000);
        }
        Battle battle = new Battle(new ArrayList<>(List.of(blade)), new ArrayList<>(List.of(left, centre, right)),
                BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        battle.processRequests();

        // Cast the enhanced Basic directly: its data row is what this criterion is about, and the talent swap that normally
        // equips slot 8 is a separate clause with its own record.
        var enhanced = new com.laosun.aluminium.skill.DefaultSkill(BLADE, 8, LEVEL);
        double attack = blade.getAttribute(AttributeType.ATTACK).get();
        double maxHp = blade.getMaxHp();
        double expectedBase = 0.56 * attack + 1.4 * maxHp;

        double before = centre.getCurrentHp();
        battle.castImmediate(enhanced, blade, List.of(centre));
        battle.processRequests();
        double settled = before - centre.getCurrentHp();
        System.out.println("[1205] atk=" + attack + " maxHp=" + maxHp + " expectedBase=" + expectedBase + " settled=" + settled);

        // Zones shrink the base (DEF/res/break), so the settled number is BELOW it - but it can never exceed it, and the old
        // reading (0.56 x ATK only) would land far below this bound.
        Assertions.assertTrue(settled > 0, "the enhanced Basic dealt damage");
        Assertions.assertTrue(settled < expectedBase, "the settled instance stays under its own base: " + settled + " < " + expectedBase);
        Assertions.assertTrue(settled > 0.05 * expectedBase,
                "and it is not the ATK-only reading (" + 0.56 * attack + "): " + settled);
    }

    // ==================================================================
    // The state entered, the Technique's opening, and the charge cap
    // ==================================================================

    /**
     * Note: The Skill enters the state.
     */
    @Test
    public void theSkillEntersHellscape() {
        Character blade = CharacterFactory.create(BLADE, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(blade), List.of(enemy), fixed());
        battle.startBattle();

        battle.castImmediate(blade.getSkills().get(com.laosun.aluminium.enums.SkillType.SKILL), blade, List.of(enemy));

        Assertions.assertTrue(blade.getBuffManager().hasState("地狱变"),
                "「进入【地狱变】状态」 (enters the Hellscape (【地狱变】) state)");
    }

    /**
     * Note: The technique's 40% of his Max HP, measured against a hand-built 50% in the same pipeline, and the control.
     */
    @Test
    public void theTechniqueDealsFortyPercentOfHisMaxHp() {
        double content = openingLoss(true);
        double reference = referenceLoss();
        double undeclared = openingLoss(false);

        Assertions.assertEquals(0.0, undeclared, 1e-9, "「使用秘技后」 (after using the Technique) -- undeclared, so nothing");
        Assertions.assertTrue(reference > 0, "the reference must deal damage");
        Assertions.assertEquals(0.4 / 0.5, content / reference, 0.05,
                "content " + content + " vs reference " + reference + " (expected " + (0.4 / 0.5) + ")");
    }

    /**
     * Note: The charge cap, tested by exceeding it.
     *
     * <p>⚠ Since the talent's follow-up landed (2026-11-10), reaching the cap CONSUMES every layer - so the counter is
     * 0 right after the fifth hit, never 5. What still holds is the cap itself: the counter must never read more than
     * five, and the fifth layer is what pays the follow-up (the Talent's cases below measure that side).
     */
    @Test
    public void theChargeNeverExceedsFive() {
        Character blade = CharacterFactory.create(BLADE, LEVEL);
        Enemy enemy = BattleFixture.dummy();
        Battle battle = new Battle(List.of(blade), List.of(enemy), fixed());
        battle.startBattle();

        for (int i = 0; i < 12; i++) {
            battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.TAKING_HIT, enemy, blade, 0, 0));
            Assertions.assertTrue(blade.getBuffManager().stacksOf("char:1205/charge") <= 5,
                    "「最多叠加5层」 (stacks at most 5 times) - hit " + (i + 1) + " read "
                            + blade.getBuffManager().stacksOf("char:1205/charge"));
        }
        // And the layers are still being gained: four hits must read four (the follow-up empties them at five).
        Character fresh = CharacterFactory.create(BLADE, LEVEL);
        Battle second = new Battle(List.of(fresh), List.of(BattleFixture.dummy()), fixed());
        second.startBattle();
        for (int i = 0; i < 4; i++) {
            second.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.TAKING_HIT,
                    second.enemies().getFirst(), fresh, 0, 0));
        }
        Assertions.assertEquals(4, fresh.getBuffManager().stacksOf("char:1205/charge"),
                "below the cap every hit still adds a layer");
    }

    // ==================================================================
    // The Talent's follow-up and the two halves of Eidolon 6
    // ==================================================================

    /**
     * E5: four layers are not the cap; the fifth pays one follow-up and empties the counter.
     */
    @Test
    public void theFifthLayerPaysTheFollowupAndConsumesEveryCharge() {
        Run run = new Run(5);
        run.hit(4);
        Assertions.assertEquals(4, run.blade.getBuffManager().stacksOf(CHARGE), "four layers, cap is five");
        Assertions.assertEquals(0.0, run.enemyLoss(), 1e-9, "「当充能叠加至上限时」 - not yet");

        run.hit(1);
        Assertions.assertTrue(run.enemyLoss() > 0, "the fifth layer is the cap, so the follow-up lands");
        Assertions.assertEquals(0, run.blade.getBuffManager().stacksOf(CHARGE),
                "「施放追加攻击后，消耗所有充能」");
    }

    /**
     * E6: 「充能的层数上限降低至 4 层」 - the cap, and the threshold that follows from it, are four.
     */
    @Test
    public void theSixthEidolonLowersTheChargeCapToFour() {
        Run run = new Run(6);
        run.hit(3);
        Assertions.assertEquals(3, run.blade.getBuffManager().stacksOf(CHARGE), "three layers, cap is four");
        Assertions.assertEquals(0.0, run.enemyLoss(), 1e-9, "not at the cap yet");

        run.hit(1);
        Assertions.assertTrue(run.enemyLoss() > 0, "the fourth layer is the cap at E6");
        Assertions.assertEquals(0, run.blade.getBuffManager().stacksOf(CHARGE),
                "and the follow-up still consumes every charge");

        // And the counter cannot hold five any more.
        run.hit(20);
        Assertions.assertTrue(run.blade.getBuffManager().stacksOf(CHARGE) <= 4,
                "「最多叠加 4 层」 - twenty more hits must never read 5");
    }

    /**
     * E6's damage half: 「天赋触发追加攻击造成的伤害额外提高，提高数值等同于刃 50% 的生命上限」.
     */
    @Test
    public void theSixthEidolonAddsHalfHisMaxHpToTheFollowup() {
        Run e5 = new Run(5);
        e5.hit(5);
        Run e6 = new Run(6);
        e6.hit(4);
        double delta = e6.enemyLoss() - e5.enemyLoss();
        // The follow-up is an AoE: ONE instance per victim, and each instance carries the same absolute raise.
        double expected = 0.5 * e6.blade.getMaxHp() * e6.enemies.size();
        System.out.println("[1205-E6] followup E5=" + e5.enemyLoss() + " E6=" + e6.enemyLoss()
                + " delta=" + delta + " expected=" + expected);
        Assertions.assertEquals(expected, delta, 1e-6,
                "the raise is 50% of Max HP per instance, added after the zones - no crit, no defence, no resistance");
    }

    /**
     * 「并回复等同于刃生命上限 25% 的生命值」.
     */
    @Test
    public void theFollowupHealsAQuarterOfHisMaxHp() {
        Run run = new Run(5);
        run.blade.takeDamage(run.blade.getMaxHp() * 0.6);
        double before = run.blade.getCurrentHp();
        run.hit(5);
        double healed = run.blade.getCurrentHp() - before;
        Assertions.assertEquals(0.25 * run.blade.getMaxHp(), healed, 1e-6,
                "the heal is 25% of his own Max HP (skills.json 120504 #3 = 0.25)");
    }

    // ==================================================================
    // Eidolon 4 - the crossing, the two channels and the cap
    // ==================================================================

    /**
     * One crossing raises Max HP by 20% of base; at E3 the same crossing changes nothing.
     */
    @Test
    public void aCrossingRaisesHisMaxHpOnlyAtEidolonFour() {
        double e3 = crossOnce(3, 0.60);
        double e4 = crossOnce(4, 0.60);
        System.out.println("[1205-E4] raise / base HP: E3=" + e3 + " ; E4=" + e4);
        Assertions.assertEquals(0.0, e3, 1e-9, "E3 states no such raise");
        Assertions.assertEquals(0.2, e4, 1e-9, "「生命上限提高 20%」");
    }

    /**
     * Two crossings pay two layers, and a third stops at the cap.
     */
    @Test
    public void theRaiseStacksTwiceAndThenStops() {
        Character blade = CharacterFactory.create(BLADE, LEVEL);
        blade.setEidolonRank(4);
        Enemy enemy = BattleFixture.dummy();
        Battle battle = fixture(blade, enemy);
        double maxHp = blade.getMaxHp();
        double baseHp = baseHp(blade);
        bringTo(blade, 0.60);

        crossByDamage(battle, blade, enemy);
        Assertions.assertEquals(maxHp + 0.2 * baseHp, blade.getMaxHp(), 1e-6, "first layer");

        // A second hit while he is already below half is NOT a crossing.
        battle.applyDamage(blade, damage(enemy, blade, 0.05));
        Assertions.assertEquals(maxHp + 0.2 * baseHp, blade.getMaxHp(), 1e-6,
                "「降到」is the crossing, not the state");

        blade.heal(maxHp * 2);
        bringTo(blade, 0.60);
        crossByDamage(battle, blade, enemy);
        Assertions.assertEquals(maxHp + 0.4 * baseHp, blade.getMaxHp(), 1e-6, "second layer - still additive");

        // A third crossing must stop at the cap.
        bringTo(blade, 0.60);
        crossByDamage(battle, blade, enemy);
        Assertions.assertEquals(maxHp + 0.4 * baseHp, blade.getMaxHp(), 1e-6, "「最多叠加 2 层」");
    }

    /**
     * His own HP cost is the second channel: the Skill pays 30% of Max HP, which can push him across the line.
     */
    @Test
    public void payingHpWithTheSkillAlsoCrossesTheLine() {
        Character blade = CharacterFactory.create(BLADE, LEVEL);
        blade.setEidolonRank(4);
        Enemy enemy = BattleFixture.dummy();
        Battle battle = fixture(blade, enemy);
        double maxHp = blade.getMaxHp();
        double baseHp = baseHp(blade);
        // 60% -> the Skill's 30% cost lands at 30%, i.e. across half.
        blade.takeDamage(maxHp * 0.4);

        battle.fire(TriggerPayload.Simple.of(TriggerEvent.SKILL_CAST, blade, enemy, 0, 0));

        Assertions.assertTrue(blade.getCurrentHp() / blade.getMaxHp() <= 0.5,
                "precondition: the cost took him below half: " + (blade.getCurrentHp() / blade.getMaxHp()));
        Assertions.assertEquals(maxHp + 0.2 * baseHp, blade.getMaxHp(), 1e-6,
                "「消耗生命值」is still 「当前生命值百分比降低」 - the same sentence, the other channel");
    }

    // ==================================================================
    // Eidolon 1 - the Ultimate's designated target and the tally
    // ==================================================================

    @Test
    public void theDesignatedTargetTakesExactlyOneAndAHalfTimesTheTally() {
        int tally = 1000;
        Reading without = cast(0, tally);
        Reading with = cast(1, tally);
        System.out.println("[1205-E1] tally=" + tally
                + " centre " + without.centre + " -> " + with.centre
                + " ; neighbour " + without.neighbour + " -> " + with.neighbour
                + " ; base without tally " + cast(1, 0).centre);

        Assertions.assertEquals(1.5 * tally, with.centre - without.centre, 1e-6,
                "「提高数值等同于刃 150% 本场战斗中累计的已损失生命值」 - the raise is the settled amount's, not a factor");
        Assertions.assertEquals(0.0, with.neighbour - without.neighbour, 1e-9,
                "「指定敌方单体」 - the neighbours are the other sentence");
        Assertions.assertNotEquals(0.0, with.centre - without.centre,
                "if the deltas coincided, the criterion could not tell the two batches apart");
    }

    /**
     * Two-sided: with no accumulated loss the Eidolon pays nothing, and its own share is the <b>extra</b> 150% - the
     * Ultimate's row already carries 100% of the tally as one of its three base terms, which is the other sentence.
     */
    @Test
    public void noTallyMeansNoRaiseAndTheShareIsTheExtraHalf() {
        Assertions.assertEquals(0.0, cast(1, 0).centre - cast(0, 0).centre, 1e-9,
                "「累计已损失生命值」 = 0 gives a raise of 0");
        // Note: E0 with a tally is NOT the same as E0 without one: skills.json 120503's third base term is #5 = 100% of
        // the tally, so the row already reads it. What this Eidolon adds is the 150%, which is exactly the delta measured.
        Assertions.assertEquals(1.5 * 800, cast(1, 800).centre - cast(0, 800).centre, 1e-6,
                "the row's 100% and the Eidolon's 150% are two different terms of the same cast");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static Random fixed() {
        return BattleFixture.alwaysRoll(1.0);
    }

    /**
     * Runs the opening with or without the technique marker.
     */
    private static double openingLoss(boolean declared) {
        Character blade = CharacterFactory.create(BLADE, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(blade), List.of(enemy), fixed());
        if (declared) {
            battle.markTechniqueUsed(blade);
        }
        double before = enemy.getCurrentHp();
        battle.startBattle();
        return before - enemy.getCurrentHp();
    }

    /**
     * A hand-built 50%-Max-HP instance in the same pipeline, as the ratio's denominator.
     */
    private static double referenceLoss() {
        Character blade = CharacterFactory.create(BLADE, LEVEL);
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "DAMAGE");
        TriggerSpecs.set(effect, "scale", "owner_max_hp");
        TriggerSpecs.set(effect, "percent", 0.5);
        TriggerSpecs.set(effect, "element", "Wind");
        TriggerSpecs.set(effect, "target", "all_enemies");
        blade.setTriggerTable(new TriggerTable(BLADE, List.of(TriggerSpecs.rule(
                TriggerEvent.BATTLE_START.name(), List.of("self has_state sys:technique"), effect))));
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(blade), List.of(enemy), fixed());
        battle.markTechniqueUsed(blade);
        double before = enemy.getCurrentHp();
        battle.startBattle();
        return before - enemy.getCurrentHp();
    }

    private static double gain(boolean state) {
        Character unit = CharacterFactory.create(BLADE, LEVEL);
        unit.setEidolonRank(2);   // 星魂 gated: the rule carries min_eidolon
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(unit), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        if (state) {
            unit.getBuffManager().addBuff(new StateBuff(STATE, 3));
        }
        double before = unit.getAttribute(AttributeType.CRIT_CHANCE).get();
        battle.setCurrentMove(new Signal(unit));
        battle.beforeMove();
        return unit.getAttribute(AttributeType.CRIT_CHANCE).get() - before;
    }

    /** How much one crossing raised Max HP, in units of base HP. */
    private static double crossOnce(int eidolon, double startFraction) {
        Character blade = CharacterFactory.create(BLADE, LEVEL);
        blade.setEidolonRank(eidolon);
        Enemy enemy = BattleFixture.dummy();
        Battle battle = fixture(blade, enemy);
        double maxHp = blade.getMaxHp();
        double baseHp = baseHp(blade);
        blade.takeDamage(maxHp * (1 - startFraction));
        crossByDamage(battle, blade, enemy);
        return (blade.getMaxHp() - maxHp) / baseHp;
    }

    private static double baseHp(Character blade) {
        return blade.getAttribute(AttributeType.HEALTH).baseValue();
    }

    /** Full HP, then down to the stated fraction by a RAW hit - the fixture must not run the clause while it is set up. */
    private static void bringTo(Character unit, double fraction) {
        unit.heal(unit.getMaxHp());
        unit.takeDamage(unit.getMaxHp() * (1 - fraction));
    }

    private static void crossByDamage(Battle battle, Character blade, Enemy enemy) {
        battle.applyDamage(blade, damage(enemy, blade, 0.5));
    }

    private static Damage damage(Enemy from, Character to, double maxHpShare) {
        return new Damage(from, to, DamageElement.PHYSICAL, DamageType.NORMAL, to.getMaxHp() * maxHpShare);
    }

    private static Battle fixture(Character blade, Enemy enemy) {
        Battle battle = new Battle(List.of(blade), List.of(enemy), fixed());
        battle.startBattle();
        return battle;
    }

    private record Reading(double centre, double neighbour) {
    }

    /**
     * One cast of the Ultimate onto the middle of three dummies, returning what the centre and one neighbour lost.
     */
    private static Reading cast(int eidolon, int tally) {
        Character blade = CharacterFactory.create(BLADE, LEVEL);
        blade.setEidolonRank(eidolon);
        Enemy left = BattleFixture.dummy();
        Enemy centre = BattleFixture.dummy();
        Enemy right = BattleFixture.dummy();
        Battle battle = new Battle(new ArrayList<>(List.of(blade)),
                new ArrayList<>(List.of(left, centre, right)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        if (tally > 0) {
            blade.getResources().gain(TALLY, tally);
        }
        battle.processRequests();

        var ultimate = blade.getSkills().get(SkillType.ULTRA);
        Assertions.assertNotNull(ultimate, "precondition: Blade has an Ultimate");
        double centreBefore = centre.getCurrentHp();
        double neighbourBefore = left.getCurrentHp();
        battle.castImmediate(ultimate, blade, List.of(centre));
        battle.processRequests();
        return new Reading(centreBefore - centre.getCurrentHp(), neighbourBefore - left.getCurrentHp());
    }

    // ==================================================================
    // Fixtures
    // ==================================================================

    private static final class Fixture {
        private final Character blade = CharacterFactory.create(BLADE, LEVEL);
        private final Enemy enemy = BattleFixture.dummy();
        private final Battle battle = new Battle(List.of(blade), List.of(enemy), fixed());

        Fixture() {
            battle.startBattle();
        }

        com.laosun.aluminium.skill.Skill skill() {
            return blade.getSkills().get(SkillType.SKILL);
        }

        void enterHellscape() {
            battle.castImmediate(skill(), blade, List.of(enemy));
            battle.processRequests();
        }

        double boost() {
            return blade.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        }
    }

    /** One battle: Blade at the named eidolon rank, three dummies, and a way to land N hits on him. */
    private static final class Run {
        private final Character blade;
        private final Enemy first;
        private final List<Enemy> enemies;
        private final Battle battle;
        private final double enemyHpBefore;

        Run(int eidolon) {
            blade = CharacterFactory.create(BLADE, LEVEL);
            blade.setEidolonRank(eidolon);
            first = BattleFixture.dummy();
            Enemy second = BattleFixture.dummy();
            Enemy third = BattleFixture.dummy();
            enemies = List.of(first, second, third);
            battle = new Battle(new ArrayList<>(List.of(blade)), new ArrayList<>(enemies),
                    BattleFixture.alwaysRoll(0.0));
            battle.startBattle();
            battle.processRequests();
            enemyHpBefore = enemyHp();
        }

        void hit(int times) {
            for (int i = 0; i < times; i++) {
                battle.fire(TriggerPayload.Simple.of(TriggerEvent.TAKING_HIT, enemies.getFirst(), blade, 0, 0));
            }
        }

        double enemyLoss() {
            return enemyHpBefore - enemyHp();
        }

        private double enemyHp() {
            double total = 0;
            for (Enemy enemy : enemies) {
                total += enemy.getCurrentHp();
            }
            return total;
        }
    }
}
