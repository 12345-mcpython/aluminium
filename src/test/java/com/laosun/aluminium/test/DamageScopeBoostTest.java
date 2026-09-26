package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.skill.Skill;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Scoped DMG boosts (P10-4): 「普攻 / 战技 / 终结技 造成的伤害提高 X%」.
 *
 * <p><b>Why this needed engine work at all.</b> A basic attack and a skill are both
 * {@code DamageType.NORMAL}, so the damage <i>type</i> cannot say which one produced an instance — which is
 * why relic set 131's "the DMG dealt by their Skill and Ultimate increases by 18%" was registered as
 * unmodelled for exactly this reason. The fact is now carried explicitly: {@code SkillExecutor} puts the
 * cast's {@link SkillCategory} onto the instance and {@code Battle.assemble} asks
 * {@link SkillCategory#damageBoost()} which attribute to add.
 *
 * <p><b>The property under test is "scoped", not "bigger".</b> Each case therefore checks both halves: the
 * matching cast is raised by exactly the stated percentage, <b>and</b> the other casts are untouched. A
 * wiring that boosted everything would satisfy the first half alone — and would be the silent
 * over-application this project treats as the worst failure mode.
 *
 * <p>Crit is forced to 0 in the harness so the ratio is exact and comparable; the dummy has enough HP that
 * two measurements cannot kill it.
 */
public class DamageScopeBoostTest {
    /** Himeko: a real character whose data carries a damaging basic attack, skill and ultimate. */
    private static final int HERO = 1003;
    private static final int LEVEL = 80;
    private static final double EPS = 1e-6;

    // ==================================================================
    // 1. Each cast is raised by its own attribute, and only by it
    // ==================================================================

    @Test
    public void basicAttackBoostRaisesTheBasicAttackByExactlyThatAmount() {
        double plain = settledDamage(SkillType.COMMON, null, 0);
        double boosted = settledDamage(SkillType.COMMON, AttributeType.BASIC_ATTACK_DAMAGE_BOOST, 0.4);

        Assertions.assertTrue(plain > 0, "precondition: the basic attack deals damage");
        Assertions.assertEquals(plain * 1.4, boosted, EPS);
    }

    @Test
    public void basicAttackBoostLeavesASkillAlone() {
        Assertions.assertEquals(settledDamage(SkillType.SKILL, null, 0),
                settledDamage(SkillType.SKILL, AttributeType.BASIC_ATTACK_DAMAGE_BOOST, 0.4), EPS,
                "「普攻造成的伤害提高」 must not reach a skill: that is what \"scoped\" means here");
    }

    @Test
    public void skillBoostRaisesTheSkillAndNothingElse() {
        double plainSkill = settledDamage(SkillType.SKILL, null, 0);
        double boostedSkill = settledDamage(SkillType.SKILL, AttributeType.SKILL_DAMAGE_BOOST, 0.4);
        Assertions.assertEquals(plainSkill * 1.4, boostedSkill, EPS);

        Assertions.assertEquals(settledDamage(SkillType.COMMON, null, 0),
                settledDamage(SkillType.COMMON, AttributeType.SKILL_DAMAGE_BOOST, 0.4), EPS,
                "and not a basic attack either");
    }

    @Test
    public void ultimateBoostRaisesTheUltimateAndNothingElse() {
        double plainUlt = settledDamage(SkillType.ULTRA, null, 0);
        double boostedUlt = settledDamage(SkillType.ULTRA, AttributeType.ULTIMATE_DAMAGE_BOOST, 0.4);
        Assertions.assertEquals(plainUlt * 1.4, boostedUlt, EPS);

        Assertions.assertEquals(plainUlt,
                settledDamage(SkillType.ULTRA, AttributeType.SKILL_DAMAGE_BOOST, 0.4), EPS,
                "the ultimate is not a skill: ULTRA and BPSKILL are separate categories");
    }

    // ==================================================================
    // 2. The vocabulary is closed, and the "not a cast" instances stay out of it
    // ==================================================================

    @Test
    public void eachCastCategoryMapsToItsOwnAttribute() {
        Assertions.assertEquals(AttributeType.BASIC_ATTACK_DAMAGE_BOOST,
                SkillCategory.NORMAL.damageBoost());
        Assertions.assertEquals(AttributeType.SKILL_DAMAGE_BOOST,
                SkillCategory.BPSKILL.damageBoost());
        Assertions.assertEquals(AttributeType.ULTIMATE_DAMAGE_BOOST,
                SkillCategory.ULTRA.damageBoost());

        for (SkillCategory notACast : List.of(SkillCategory.MAZE, SkillCategory.MAZE_NORMAL,
                SkillCategory.ASSIST, SkillCategory.ELATION_DAMAGE, SkillCategory.UNSPECIFIED)) {
            Assertions.assertNull(notACast.damageBoost(),
                    notACast + " is not one of the three casts the texts name, so it has no scoped boost");
        }
    }

    @Test
    public void theLegacyConstructorsProduceAnUnscopedInstance() {
        Assertions.assertEquals(SkillCategory.UNSPECIFIED,
                new Damage(null, null, DamageElement.FIRE, DamageType.NORMAL, 100).getCastCategory());
        Assertions.assertEquals(SkillCategory.UNSPECIFIED,
                new Damage(null, null, DamageElement.FIRE, 100).getCastCategory());
        Assertions.assertEquals(SkillCategory.UNSPECIFIED,
                new Damage(null, null, DamageElement.FIRE, DamageType.NORMAL, 100, null).getCastCategory(),
                "a null category is normalised, so no caller has to null-check and assemble cannot NPE");
        Assertions.assertEquals(SkillCategory.BPSKILL,
                new Damage(null, null, DamageElement.FIRE, DamageType.NORMAL, 100, SkillCategory.BPSKILL)
                        .getCastCategory());
    }

    /**
     * A follow-up is not a basic attack: it has {@code FOLLOW_UP_DAMAGE_BOOST} and must not collect a scoped
     * cast boost as well.
     */
    @Test
    public void aFollowUpUsesItsOwnBoostAndNoScopedOne() {
        double plain = additionalDamage(AttributeType.FOLLOW_UP_DAMAGE_BOOST, 0);
        double withFollowUpBoost = additionalDamage(AttributeType.FOLLOW_UP_DAMAGE_BOOST, 0.2);
        Assertions.assertEquals(plain * 1.2, withFollowUpBoost, EPS, "set 115's 2-piece, in one line");

        Assertions.assertEquals(plain, additionalDamage(AttributeType.BASIC_ATTACK_DAMAGE_BOOST, 0.4), EPS,
                "追加攻击 damage is not 普攻 damage, even when the follow-up came from one");
    }

    /**
     * A DOT is not a cast either: 触电/灼烧 damage must not grow because the wearer boosted their basic
     * attacks.
     */
    @Test
    public void aDotIsNotBoostedByAScopedBoost() {
        double plain = dotDamage(AttributeType.BASIC_ATTACK_DAMAGE_BOOST, 0);
        Assertions.assertEquals(plain, dotDamage(AttributeType.BASIC_ATTACK_DAMAGE_BOOST, 0.4), EPS);
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /**
     * Settles a real cast of {@code slot} on a dummy and returns the HP the dummy lost.
     *
     * <p>Going through {@code castImmediate} rather than building a {@code Damage} by hand is the point: it
     * exercises the plumbing that carries the category from the skill data, so a forgotten line in
     * {@code SkillExecutor.hit} fails here instead of passing unnoticed.
     *
     * @param boost the attribute to grant the caster, or {@code null} for none
     * @param value the boost's value (0.4 = +40%)
     */
    private static double settledDamage(SkillType slot, AttributeType boost, double value) {
        Character hero = boostedHero(boost, value);
        Enemy dummy = dummy();
        Battle battle = new Battle(List.of(hero), List.of(dummy), new Random(0));
        battle.startBattle();

        Skill skill = hero.getSkills().get(slot);
        Assertions.assertNotNull(skill, "precondition: the hero carries the " + slot + " slot");
        double before = dummy.getCurrentHp();
        battle.castImmediate(skill, hero, List.of(dummy));
        return before - dummy.getCurrentHp();
    }

    /** The same, for an additional-damage instance (a follow-up), which is settled directly. */
    private static double additionalDamage(AttributeType boost, double value) {
        Character hero = boostedHero(boost, value);
        Enemy dummy = dummy();
        Battle battle = new Battle(List.of(hero), List.of(dummy), new Random(0));
        battle.startBattle();
        return battle.applyDamage(dummy, new Damage(hero, dummy, DamageElement.FIRE,
                DamageType.ADDITIONAL, 1000));
    }

    private static double dotDamage(AttributeType boost, double value) {
        Character hero = boostedHero(boost, value);
        Enemy dummy = dummy();
        Battle battle = new Battle(List.of(hero), List.of(dummy), new Random(0));
        battle.startBattle();
        return battle.applyDamage(dummy, new Damage(hero, dummy, DamageElement.FIRE,
                DamageType.DOT, 1000));
    }

    /**
     * A character with crit forced off and its own typed DMG boosts zeroed.
     *
     * <p><b>Why the typed boosts are zeroed: the boost zone is <i>additive</i>.</b> Himeko's own traces give
     * her +22.4% fire DMG, so granting a +40% scoped boost raises her damage by a factor of
     * {@code (1.224 + 0.4) / 1.224 = 1.327}, not 1.4 — the first version of this test asserted 1.4 and failed
     * with exactly that number, which is how the additive behaviour got confirmed rather than assumed. The
     * test is about the <b>scope</b>, so the harness removes the unrelated baseline instead of encoding it.
     * (The additivity itself is real and intended: a scoped boost stacks with an element boost the same way
     * the game does.)
     *
     * <p>Crit is zeroed because {@code assemble} rolls it from the sheet, and two measurements in the same
     * harness must be comparable to the last bit.
     */
    private static Character boostedHero(AttributeType boost, double value) {
        Character hero = CharacterFactory.create(HERO, LEVEL);
        hero.setAttribute(AttributeType.CRIT_CHANCE, new DoubleValue(0));
        hero.setAttribute(AttributeType.FIRE_DAMAGE_BOOST, new DoubleValue(0));
        hero.setAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST, new DoubleValue(0));
        if (boost != null) {
            hero.getAttribute(boost).addModifier(
                    DoubleValue.Modifier.pure(value, DoubleValue.Modifier.ModifierSource.BUFF, 1));
        }
        return hero;
    }

    private static Enemy dummy() {
        return Enemy.fromAttributes("dummy", 1_000_000, 100, 100, 100);
    }
}
