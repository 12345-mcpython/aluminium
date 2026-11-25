package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.*;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.DoubleValue;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.skill.Skill;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;

/**
 * Scoped DMG boosts: "普攻 / 战技 / 终结技 造成的伤害提高 X%".
 *
 * <p><b>Why this needed engine work at all.</b> A basic attack and a skill are both
 * {@code DamageType.NORMAL}, so the damage <i>type</i> cannot say which one produced an instance - which is
 * why relic set 131's "the DMG dealt by their Skill and Ultimate increases by 18%" is registered as
 * unmodelled. The fact is carried explicitly instead: {@code SkillExecutor} puts the
 * cast's {@link SkillCategory} onto the instance and {@code Battle.assemble} asks
 * {@link SkillCategory#damageBoost()} which attribute to add.
 *
 * <p><b>The property under test is "scoped", not "bigger".</b> Each case therefore checks both halves: the
 * matching cast is raised by exactly the stated percentage, <b>and</b> the other casts are untouched. A
 * wiring that boosted everything would satisfy the first half alone - and would be the silent
 * over-application this project treats as the worst failure mode.
 *
 * <p>Crit is forced to 0 in the harness so the ratio is exact and comparable; the dummy has enough HP that
 * two measurements cannot kill it.
 */
public class DamageScopeBoostTest {
    /**
     * Himeko: a real character whose data carries a damaging basic attack, skill and ultimate.
     */
    private static final int HERO = 1003;
    private static final int LEVEL = 80;
    private static final double EPS = 1e-6;

    // ==================================================================
    // 1. Each cast is raised by its own attribute, and only by it
    // ==================================================================

    /**
     * One scenario over the real cast harness: the three scoped cast boosts are applied in turn to a real
     * cast, so the whole "raised by exactly X%, and the other casts are untouched" matrix is observed on the
     * same fixture.
     */
    @Test
    public void eachScopedCastBoostRaisesItsOwnCastAndNothingElse() {
        double plainBasic = settledDamage(SkillType.COMMON, null, 0);
        double boostedBasic = settledDamage(SkillType.COMMON, AttributeType.BASIC_ATTACK_DAMAGE_BOOST, 0.4);
        double skillUnderBasicBoost = settledDamage(SkillType.SKILL, AttributeType.BASIC_ATTACK_DAMAGE_BOOST, 0.4);
        double plainSkill = settledDamage(SkillType.SKILL, null, 0);
        double boostedSkill = settledDamage(SkillType.SKILL, AttributeType.SKILL_DAMAGE_BOOST, 0.4);
        double commonUnderSkillBoost = settledDamage(SkillType.COMMON, AttributeType.SKILL_DAMAGE_BOOST, 0.4);
        double plainUltimate = settledDamage(SkillType.ULTRA, null, 0);
        double boostedUltimate = settledDamage(SkillType.ULTRA, AttributeType.ULTIMATE_DAMAGE_BOOST, 0.4);
        double ultimateUnderSkillBoost = settledDamage(SkillType.ULTRA, AttributeType.SKILL_DAMAGE_BOOST, 0.4);

        Assertions.assertAll("scoped cast DMG boosts stay inside the cast they name",
                () -> Assertions.assertTrue(plainBasic > 0, "precondition: the basic attack deals damage"),
                () -> Assertions.assertEquals(plainBasic * 1.4, boostedBasic, EPS,
                        "「普攻造成的伤害提高」: +40% basic ATK DMG raises the basic attack by exactly 40%"),
                () -> Assertions.assertEquals(plainSkill, skillUnderBasicBoost, EPS,
                        "「普攻造成的伤害提高」 (DMG dealt by Basic ATKs is raised) must not reach a skill: that is what \"scoped\" means here"),
                () -> Assertions.assertEquals(plainSkill * 1.4, boostedSkill, EPS,
                        "「战技造成的伤害提高」: +40% skill DMG raises the skill by exactly 40%"),
                () -> Assertions.assertEquals(plainBasic, commonUnderSkillBoost, EPS,
                        "and not a basic attack either"),
                () -> Assertions.assertEquals(plainUltimate * 1.4, boostedUltimate, EPS,
                        "「终结技造成的伤害提高」: +40% ultimate DMG raises the ultimate by exactly 40%"),
                () -> Assertions.assertEquals(plainUltimate, ultimateUnderSkillBoost, EPS,
                        "the ultimate is not a skill: ULTRA and BPSKILL are separate categories"));
    }

    // ==================================================================
    // 2. The vocabulary is closed, and the "not a cast" instances stay out of it
    // ==================================================================

    @Test
    public void eachCastCategoryMapsToItsOwnAttribute() {
        List<Executable> checks = new ArrayList<>();
        checks.add(() -> Assertions.assertEquals(AttributeType.BASIC_ATTACK_DAMAGE_BOOST,
                SkillCategory.NORMAL.damageBoost(),
                "NORMAL (普攻) maps to the basic-attack DMG boost"));
        checks.add(() -> Assertions.assertEquals(AttributeType.SKILL_DAMAGE_BOOST,
                SkillCategory.BPSKILL.damageBoost(),
                "BPSKILL (战技) maps to the skill DMG boost"));
        checks.add(() -> Assertions.assertEquals(AttributeType.ULTIMATE_DAMAGE_BOOST,
                SkillCategory.ULTRA.damageBoost(),
                "ULTRA (终结技) maps to the ultimate DMG boost"));

        for (SkillCategory notACast : List.of(SkillCategory.MAZE, SkillCategory.MAZE_NORMAL,
                SkillCategory.ASSIST, SkillCategory.ELATION_DAMAGE, SkillCategory.UNSPECIFIED)) {
            checks.add(() -> Assertions.assertNull(notACast.damageBoost(),
                    notACast + " is not one of the three casts the texts name, so it has no scoped boost"));
        }
        Assertions.assertAll("cast category -> scoped boost attribute", checks);
    }

    @Test
    public void theLegacyConstructorsProduceAnUnscopedInstance() {
        Assertions.assertAll("the legacy Damage constructors default to an unscoped instance",
                () -> Assertions.assertEquals(SkillCategory.UNSPECIFIED,
                        new Damage(null, null, DamageElement.FIRE, DamageType.NORMAL, 100).getCastCategory(),
                        "the 5-arg constructor leaves the cast category unscoped"),
                () -> Assertions.assertEquals(SkillCategory.UNSPECIFIED,
                        new Damage(null, null, DamageElement.FIRE, 100).getCastCategory(),
                        "the 4-arg constructor leaves the cast category unscoped"),
                () -> Assertions.assertEquals(SkillCategory.UNSPECIFIED,
                        new Damage(null, null, DamageElement.FIRE, DamageType.NORMAL, 100, null).getCastCategory(),
                        "a null category is normalised, so no caller has to null-check and assemble cannot NPE"),
                () -> Assertions.assertEquals(SkillCategory.BPSKILL,
                        new Damage(null, null, DamageElement.FIRE, DamageType.NORMAL, 100, SkillCategory.BPSKILL)
                                .getCastCategory(),
                        "an explicit cast category is carried through unchanged"));
    }

    /**
     * A follow-up is not a basic attack: it has {@code FOLLOW_UP_DAMAGE_BOOST} and must not collect a scoped
     * cast boost as well.
     */
    @Test
    public void aFollowUpUsesItsOwnBoostAndNoScopedOne() {
        double plain = additionalDamage(AttributeType.FOLLOW_UP_DAMAGE_BOOST, 0);
        double withFollowUpBoost = additionalDamage(AttributeType.FOLLOW_UP_DAMAGE_BOOST, 0.2);

        Assertions.assertAll("a follow-up attacks with its own boost and stays out of the cast vocabulary",
                () -> Assertions.assertEquals(plain * 1.2, withFollowUpBoost, EPS, "set 115's 2-piece, in one line"),
                () -> Assertions.assertEquals(plain, additionalDamage(AttributeType.BASIC_ATTACK_DAMAGE_BOOST, 0.4), EPS,
                        "follow-up attack (relic:115/follow_up) damage is not Basic ATK (普攻) damage, even when the follow-up came from one"));
    }

    /**
     * A DOT is not a cast either: shock (dot:shock) / burn (dot:burn) damage must not grow because the wearer boosted their basic
     * attacks.
     */
    @Test
    public void aDotIsNotBoostedByAScopedBoost() {
        double plain = dotDamage(AttributeType.BASIC_ATTACK_DAMAGE_BOOST, 0);
        Assertions.assertEquals(plain, dotDamage(AttributeType.BASIC_ATTACK_DAMAGE_BOOST, 0.4), EPS,
                "shock / burn (dot:shock / dot:burn) damage is not a cast, so a basic-attack boost leaves it alone");
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
        Battle battle = new Battle(List.of(hero), List.of(dummy), BattleFixture.seeded());
        battle.startBattle();

        Skill skill = hero.getSkills().get(slot);
        Assertions.assertNotNull(skill, "precondition: the hero carries the " + slot + " slot");
        double before = dummy.getCurrentHp();
        battle.castImmediate(skill, hero, List.of(dummy));
        return before - dummy.getCurrentHp();
    }

    /**
     * The same, for an additional-damage instance (a follow-up), which is settled directly.
     */
    private static double additionalDamage(AttributeType boost, double value) {
        Character hero = boostedHero(boost, value);
        Enemy dummy = dummy();
        Battle battle = new Battle(List.of(hero), List.of(dummy), BattleFixture.seeded());
        battle.startBattle();
        return battle.applyDamage(dummy, new Damage(hero, dummy, DamageElement.FIRE,
                DamageType.ADDITIONAL, 1000));
    }

    private static double dotDamage(AttributeType boost, double value) {
        Character hero = boostedHero(boost, value);
        Enemy dummy = dummy();
        Battle battle = new Battle(List.of(hero), List.of(dummy), BattleFixture.seeded());
        battle.startBattle();
        return battle.applyDamage(dummy, new Damage(hero, dummy, DamageElement.FIRE,
                DamageType.DOT, 1000));
    }

    /**
     * A character with crit forced off and its own typed DMG boosts zeroed.
     *
     * <p><b>Why the typed boosts are zeroed: the boost zone is <i>additive</i>.</b> Himeko's own traces give
     * her +22.4% fire DMG, so granting a +40% scoped boost raises her damage by a factor of
     * {@code (1.224 + 0.4) / 1.224 = 1.32}, not 1.4 - the first version of this test asserted 1.4 and failed
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
