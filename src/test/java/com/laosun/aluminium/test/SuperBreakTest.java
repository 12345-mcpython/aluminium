package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.models.AbstractBuff;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DefaultSkill;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.event.AttackEvent;
import com.laosun.aluminium.models.buffs.SuperBreakBuff;
import com.laosun.aluminium.utils.AttributeBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * P4-6 acceptance: super break.
 *
 * <p><b>How the toughness reduction value is split</b> (skill nominal toughness reduction
 * {@code S}, enemy remaining toughness {@code T}):
 * <pre>
 *   T &gt; S (not emptied)      break damage none, super break none
 *   S = T (exactly emptied)   break damage uses T, super break none (no excess)
 *   S &gt; T (emptied with excess) break damage uses T, super break uses S−T   ← both chains occur in the same hit
 *   enemy already broken (T = 0) break damage none, super break uses S (the whole hit counts as excess)
 * </pre>
 * The two chains split the same {@code S}, and their sum is always {@code S} — nothing
 * double-counted, nothing missed.
 *
 * <p>All cases use one **high-HP target**: ATK 100, HP 1,000,000, DEF 100, SPD 100, **weak to
 * Fire**, toughness 30. The enemy's toughness can be changed freely without touching the data,
 * and it will not be killed by break damage.
 * Anchor: Himeko 1003's skill (Blast/Fire) = centre {@code single = 60}, multiplier 1.0, Lv80
 * (break base 376.75535).
 */
public class SuperBreakTest {
    private static final double EPS = 1e-6;
    /** Himeko's skill: Fire Blast, centre toughness reduction 60, multiplier 1.0. */
    private static final int HIMEKO_CID = 1003;
    private static final int HIMEKO_SKILL_SLOT = 2;
    private static final double BASE_BREAK_80 = 376.75535;

    /**
     * **Core case**: this hit breaks the enemy and the toughness reduction has an excess
     * (T = 30 &lt; S = 60) → the same hit produces break damage (on 30) and super break damage
     * (on the excess 30) at the same time.
     */
    @Test
    public void breakingHitProducesBothBreakAndSuperBreakDamage() {
        Character himeko = fireAttacker();
        himeko.getBuffManager().addBuff(new SuperBreakBuff(3));
        Enemy dummy = dummy(30);
        Battle battle = newBattle(himeko, dummy);

        double hpBefore = dummy.getCurrentHp();
        battle.castImmediate(new DefaultSkill(HIMEKO_CID, HIMEKO_SKILL_SLOT, 1), himeko, List.of(dummy));

        Assertions.assertTrue(dummy.isBroken(), "60 points of toughness reduction empties 30 toughness → broken");
        // skill 100×1.0 + break (actual 30) + super break (excess 30, multiplied by an extra 1.4); all three share the defence zone and resistance zone
        double expected = base(100 * 1.0) + breakBase(BASE_BREAK_80 * 30) + breakBase(BASE_BREAK_80 * 30 * (1 + Constant.SUPER_BREAK_BOOST));
        Assertions.assertEquals(expected, hpBefore - dummy.getCurrentHp(), EPS,
                "skill damage + 30 break damage + 30 super break damage");
    }

    /**
     * The enemy is **already in the broken state** (T = 0) → the whole hit's nominal toughness
     * reduction counts as excess, and super break uses S = 60.
     * Used to verify the formula itself {@code break base × (1+break effect) × toughness reduction value × (1+super break boost)}.
     */
    @Test
    public void superBreakUsesTheWholeStanceValueWhenTheEnemyIsAlreadyBroken() {
        Character himeko = fireAttacker();
        himeko.getBuffManager().addBuff(new SuperBreakBuff(3));
        Enemy dummy = dummy(1);                                 // 1 point of toughness, one hit empties it
        Battle battle = newBattle(himeko, dummy);

        battle.castImmediate(new DefaultSkill(HIMEKO_CID, HIMEKO_SKILL_SLOT, 1), himeko, List.of(dummy));
        Assertions.assertTrue(dummy.isBroken(), "emptying the toughness enters the broken state");

        double hpBefore = dummy.getCurrentHp();
        battle.castImmediate(new DefaultSkill(HIMEKO_CID, HIMEKO_SKILL_SLOT, 1), himeko, List.of(dummy));

        // this hit: skill 100×1.0 + super break 376.75535×60×1.4 (all 60 counts as excess)
        double expected = base(100 * 1.0) + breakBase(BASE_BREAK_80 * 60 * (1 + Constant.SUPER_BREAK_BOOST));
        Assertions.assertEquals(expected, hpBefore - dummy.getCurrentHp(), EPS,
                "already broken → all 60 points of toughness reduction convert to super break");
    }

    /**
     * Exactly emptied (S = T = 60) → there is break damage, but **no excess**, so there is no
     * super break segment.
     */
    @Test
    public void noSuperBreakWhenTheStanceIsExactlyEmptied() {
        Character himeko = fireAttacker();
        himeko.getBuffManager().addBuff(new SuperBreakBuff(3));
        Enemy dummy = dummy(60);
        Battle battle = newBattle(himeko, dummy);

        double hpBefore = dummy.getCurrentHp();
        battle.castImmediate(new DefaultSkill(HIMEKO_CID, HIMEKO_SKILL_SLOT, 1), himeko, List.of(dummy));

        Assertions.assertTrue(dummy.isBroken());
        double expected = base(100 * 1.0) + breakBase(BASE_BREAK_80 * 60);   // break damage only, no super break
        Assertions.assertEquals(expected, hpBefore - dummy.getCurrentHp(), EPS, "S == T → the excess is 0");
    }

    /**
     * Not emptied (T &gt; S) → super break does not trigger (the enemy was not broken).
     */
    @Test
    public void superBreakDoesNotTriggerWhenTheHitDoesNotBreak() {
        Character himeko = fireAttacker();
        himeko.getBuffManager().addBuff(new SuperBreakBuff(3));
        Enemy dummy = dummy(90);
        Battle battle = newBattle(himeko, dummy);

        double hpBefore = dummy.getCurrentHp();
        battle.castImmediate(new DefaultSkill(HIMEKO_CID, HIMEKO_SKILL_SLOT, 1), himeko, List.of(dummy));

        Assertions.assertFalse(dummy.isBroken());
        Assertions.assertEquals(base(100 * 1.0), hpBefore - dummy.getCurrentHp(), EPS, "did not break → no super break segment");
    }

    /**
     * No buff attached: no super break segment is produced, and the excess is simply wasted
     * (break damage only).
     */
    @Test
    public void withoutTheBuffTheOverkillStanceIsWasted() {
        Character himeko = fireAttacker();                       // deliberately no SuperBreakBuff attached
        Enemy dummy = dummy(30);
        Battle battle = newBattle(himeko, dummy);

        double hpBefore = dummy.getCurrentHp();
        battle.castImmediate(new DefaultSkill(HIMEKO_CID, HIMEKO_SKILL_SLOT, 1), himeko, List.of(dummy));

        Assertions.assertTrue(dummy.isBroken());
        double expected = base(100 * 1.0) + breakBase(BASE_BREAK_80 * 30);
        Assertions.assertEquals(expected, hpBefore - dummy.getCurrentHp(), EPS,
                "break damage only (on the 30 actually shaved off); the excess 30 is not converted");
    }

    /**
     * **A non-weakness attack on an already-broken enemy still produces super break**.
     *
     * <p>The official wording's condition is "after attacking an enemy target **that is in the
     * weakness-broken state**, converts this attack's toughness reduction value into 1 instance
     * of super break damage" — the condition contains only "the enemy is already in the broken
     * state", with **no element restriction**. So an Ice attack on an already-broken
     * Fire-weakness target: the skill itself reduces no toughness (non-weakness), but the whole
     * hit's nominal toughness reduction value still converts into super break damage.
     *
     * <p>(Contrast: when not broken, a non-weakness attack produces nothing, see
     * {@link #nonWeaknessHitOnAnUnbrokenEnemyProducesNoSuperBreak}.)
     */
    @Test
    public void nonWeaknessHitOnABrokenEnemyStillProducesSuperBreak() {
        // break the target first: use Fire (which is a weakness), 30 toughness exactly emptied by 60 toughness reduction
        Character himeko = fireAttacker();
        Enemy dummy = dummy(30);
        Battle battle = newBattle(himeko, dummy);
        battle.castImmediate(new DefaultSkill(HIMEKO_CID, HIMEKO_SKILL_SLOT, 1), himeko, List.of(dummy));
        Assertions.assertTrue(dummy.isBroken());

        // switch to an **Ice** attacker (Ice is not this target's weakness) and attach the super break buff to him
        Character mar7th = Character.fromAttributes("mar7th", 100_000, 100, 100, 100);
        mar7th.setAttribute(AttributeType.ICE_DAMAGE_BOOST, new DoubleValue(0.2));
        mar7th.getBuffManager().addBuff(new SuperBreakBuff(3));

        double hpBefore = dummy.getCurrentHp();
        // March 7th's basic attack (1001/1) = Ice, multiplier 0.5, toughness reduction 30
        battle.castImmediate(new DefaultSkill(1001, 1, 1), mar7th, List.of(dummy));

        double iceResist = 1 - dummy.getDamageResist().getOrDefault(DamageElement.ICE, 0.0);
        // skill segment 100×0.5 ×(1+0.2 Ice DMG boost) × defence zone × Ice RES
        double skillDamage = 100 * 0.5 * 1.2 * defenceZone() * iceResist;
        // super break segment: all 30 points of toughness reduction count as excess (the enemy is already broken); no DMG boost, only defence zone × Ice RES
        double superBreakDamage = BASE_BREAK_80 * 30 * (1 + Constant.SUPER_BREAK_BOOST) * defenceZone() * iceResist;
        Assertions.assertEquals(skillDamage + superBreakDamage, hpBefore - dummy.getCurrentHp(), EPS,
                "non-weakness hit on an already-broken enemy → super break still occurs (using the whole 30 nominal toughness reduction)");
    }

    /**
     * Contrast case: when **not broken**, a non-weakness attack produces nothing (toughness
     * reduction is strictly "only weakness reduces", so there is no excess and hence no super
     * break).
     */
    @Test
    public void nonWeaknessHitOnAnUnbrokenEnemyProducesNoSuperBreak() {
        Character himeko = fireAttacker();
        Enemy dummy = dummy(30);
        Battle battle = newBattle(himeko, dummy);
        // shave part of the toughness but **do not break it** (Fire basic attack 30 vs the target's 60 toughness, see the dummy below)
        Character mar7th = Character.fromAttributes("mar7th", 100_000, 100, 100, 100);
        mar7th.setAttribute(AttributeType.ICE_DAMAGE_BOOST, new DoubleValue(0.2));
        mar7th.getBuffManager().addBuff(new SuperBreakBuff(3));

        double hpBefore = dummy.getCurrentHp();
        battle.castImmediate(new DefaultSkill(1001, 1, 1), mar7th, List.of(dummy));   // Ice, not a weakness

        Assertions.assertFalse(dummy.isBroken());
        double iceResist = 1 - dummy.getDamageResist().getOrDefault(DamageElement.ICE, 0.0);
        double skillDamage = 100 * 0.5 * 1.2 * defenceZone() * iceResist;
        Assertions.assertEquals(skillDamage, hpBefore - dummy.getCurrentHp(), EPS,
                "not broken + non-weakness → no toughness reduction → no excess → no super break");
    }

    /**
     * **With multiple targets every target triggers super break** (the author's convention).
     *
     * <p>One AOE attack settles separately for each target hit: each uses its own remaining
     * toughness to compute the excess and each produces one super break segment. So both
     * already-broken targets each take one instance of super break damage.
     */
    @Test
    public void multiTargetAttackTriggersSuperBreakOnEveryTarget() {
        Character himeko = fireAttacker();
        himeko.getBuffManager().addBuff(new SuperBreakBuff(3));
        // two targets: 1 point of toughness each, broken simultaneously by one AOE (Himeko's ultimate 1003/3 = AoE/Fire, all = 60, multiplier 1.38)
        Enemy left = dummy(1);
        Enemy right = dummy(1);
        Battle battle = new Battle(List.of(himeko), List.of(left, right), new Random(0));

        battle.castImmediate(new DefaultSkill(HIMEKO_CID, 3, 1), himeko, List.of(left));

        Assertions.assertTrue(left.isBroken() && right.isBroken(), "both targets are broken by this one hit");
        // per target: skill 100×1.38 + break (actual 1) + super break (excess 59)
        double perTarget = base(100 * 1.38)
                + breakBase(BASE_BREAK_80 * 1)
                + breakBase(BASE_BREAK_80 * 59 * (1 + Constant.SUPER_BREAK_BOOST));
        Assertions.assertEquals(dummyHp() - perTarget, left.getCurrentHp(), EPS, "left target");
        Assertions.assertEquals(dummyHp() - perTarget, right.getCurrentHp(), EPS,
                "the right target takes super break too — with multiple targets it triggers per target");
    }

    /**
     * **One attack action produces several damage types, but the action itself counts once**.
     *
     * <p>This pins down the semantics of {@code AttackEvent}'s {@code totalDamage}: one attack
     * contains three types — skill damage + break damage + super break damage — and they all
     * belong to **the same attack**, so the attack-level event's total MUST include **all
     * three** (it cannot count only the skill segment).
     *
     * <p>Conversely: any character effect that counts by "damage type" ("every N instances of
     * damage dealt", "after each of our side's attacks") should hook onto this **attack-level**
     * event instead of requiring one event per damage type.
     */
    @Test
    public void oneAttackActionCarriesEveryDamageTypeInItsTotal() {
        Character himeko = fireAttacker();
        himeko.getBuffManager().addBuff(new SuperBreakBuff(3));
        AttackTotalRecorder recorder = new AttackTotalRecorder();
        himeko.getBuffManager().addBuff(recorder);
        Enemy dummy = dummy(30);
        Battle battle = newBattle(himeko, dummy);

        double hpBefore = dummy.getCurrentHp();
        battle.castImmediate(new DefaultSkill(HIMEKO_CID, HIMEKO_SKILL_SLOT, 1), himeko, List.of(dummy));

        double total = hpBefore - dummy.getCurrentHp();
        Assertions.assertEquals(1, recorder.totals.size(), "one attack → exactly one AttackEvent broadcast");
        Assertions.assertEquals(total, recorder.totals.getFirst(), EPS,
                "the total includes skill damage + 30 break damage + 30 super break damage");
    }

    /** Records the totalDamage of every {@code AttackEvent} (one attack should receive exactly one). */
    private static final class AttackTotalRecorder extends AbstractBuff implements AttackEvent {
        private final List<Double> totals = new ArrayList<>();

        private AttackTotalRecorder() {
            super(1, false);
        }

        @Override
        public boolean canAct() {
            return true;
        }

        @Override
        public void applyEffect(CanHit target) {
        }

        @Override
        public void removeBuff(CanHit target) {
        }

        @Override
        public void tickEffect(CanHit target) {
            decreaseDuration();
        }

        @Override
        public void afterAttack(Battle battle, CanHit attacker, CanHit mainTarget,
                                List<? extends CanHit> hitTargets, double totalDamage) {
            totals.add(totalDamage);
        }
    }

    // ==================================================================
    // Utilities
    // ==================================================================

    /** Himeko: ATK 100 (easy to hand-compute), Fire, Lv80. */
    private static Character fireAttacker() {
        Character c = Character.fromAttributes("himeko", 100_000, 100, 100, 100);
        c.setAttribute(AttributeType.ATTACK, new DoubleValue(100));
        c.setAttribute(AttributeType.FIRE_DAMAGE_BOOST, new DoubleValue(0.2));
        return c;
    }

    /** The target's initial max HP (see {@link #dummy(double)}). */
    private static double dummyHp() {
        return 1_000_000;
    }

    /**
     * High-HP target: HP 1,000,000 / DEF 100 / SPD 100 / **weak to Fire** / Fire RES 0.2 / Ice RES 0.2 / toughness = the parameter.
     */
    private static Enemy dummy(double stance) {
        AttributeBuilder builder = new AttributeBuilder();
        builder.setBase(AttributeType.HEALTH, dummyHp())
                .setBase(AttributeType.DEFENCE, 100)
                .setBase(AttributeType.ATTACK, 100)
                .setBase(AttributeType.SPEED, 100);
        Enemy enemy = new Enemy("dummy", builder.build());
        enemy.setLevel(80);
        enemy.setStanceWeak(Set.of(DamageElement.FIRE));
        enemy.setStance(stance);
        enemy.setMaxStance(stance);
        enemy.setDamageResist(java.util.Map.of(
                DamageElement.FIRE, 0.2,
                DamageElement.ICE, 0.2));
        return enemy;
    }

    /** The level term of the defence zone when the attacker is Lv80. */
    private static double defenceZone() {
        double levelTerm = Constant.DEFENCE_CONST + Constant.DEFENCE_PER_LEVEL * 80;
        return levelTerm / (100 + levelTerm);
    }

    /**
     * Runs a base value through the DMG boost zone, the defence zone and the resistance zone:
     * {@code base × 1.2 × defence zone × resistance zone}.
     *
     * <p>The {@code × 1.2} comes from the attacker's {@code FIRE_DAMAGE_BOOST = 0.2} (DMG boost
     * zone = {@code 1 + Σ}); target level = attacker level = 80 ⇒ defence zone
     * {@code 1000/1100}; Fire RES 0.2 ⇒ resistance zone 0.8. The super break segment **does not
     * take the DMG boost zone** ({@code DamageType.SUPER_BREAK.isBoostable() == false}), so at
     * the call site that segment explicitly does not multiply by 1.2.
     */
    private static double base(double value) {
        return value * 1.2 * defenceZone() * (1 - 0.2);
    }

    /** Break / super break segment: does not take the DMG boost zone, only the defence zone and resistance zone. */
    private static double breakBase(double value) {
        return value * defenceZone() * (1 - 0.2);
    }

    private static Battle newBattle(Character hero, Enemy enemy) {
        return new Battle(List.of(hero), List.of(enemy), new Random(0));
    }
}
