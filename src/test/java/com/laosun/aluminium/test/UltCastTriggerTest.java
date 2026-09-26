package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.Skill;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * {@code ULT_CAST} (P8-7 follow-up): the event an ultimate's "after the wearer uses their Ultimate"
 * relic bonuses and talents subscribe to.
 *
 * <p>Before this, {@code ULT_CAST} was declared in {@link TriggerEvent} but had no emitter, so a rule
 * naming it was rejected at load time and no content could use it. It is now fired by
 * {@code SkillExecutor.broadcastSkillCast} — the single place a cast is broadcast — and "is this an
 * ultimate" is read from the <b>parsed skill data</b> ({@code SkillCategory.ULTRA} from
 * {@code skills.json}'s {@code attack_type}), never from a skill's name or slot.
 *
 * <h2>How a trigger firing is observed</h2>
 * A hand-made table gives the event an <b>unmistakable numeric fingerprint</b>: each event's rule
 * grants a different number of skill points, and the battle is drained to 0 points before every cast.
 * {@code castImmediate} and {@code castUltra} do not go through the skill-point policy, so the point
 * count is a pure count of "which rules ran, how many times" — which is exactly what a contract test
 * needs, and it works without touching the engine's internals.
 *
 * <p>The mutual exclusivity of {@code ULT_CAST} and {@code SKILL_CAST} is pinned here rather than
 * only in the relic tests, because it is an engine-level contract: the condition DSL has no variable
 * for "which kind of cast this was" (see {@code TriggerTable}), so "when the wearer uses their Skill"
 * can only avoid also firing on the ultimate if the two events cannot both fire.
 */
public class UltCastTriggerTest {

    /** Himeko: an ordinary character with no trigger file, so the table under test is the only one. */
    private static final int HIMEKO = 1003;
    private static final int ICE_EDGE = 1002011;
    private static final int ENEMY_LEVEL = 90;
    private static final int CHARACTER_LEVEL = 80;

    /**
     * The enemy's HP: enough that one cast cannot kill it (a death would end the battle mid-test and
     * change what is observable).
     */
    private static final double ENEMY_HP = 1_000_000;

    /** The skill-point fingerprint of each event, all different so one number identifies the event. */
    private static final int ULT_CAST_POINTS = 1;
    private static final int SKILL_CAST_POINTS = 2;
    private static final int ALLY_ATTACK_POINTS = 4;

    // ==================================================================
    // 1. The wiring itself
    // ==================================================================

    /** The event is now declared as wired, which is also what lets a data file reference it. */
    @Test
    public void ultCastIsDeclaredWired() {
        Assertions.assertTrue(TriggerEvent.ULT_CAST.isWired(),
                "ULT_CAST must be wired now that SkillExecutor emits it; otherwise every rule naming it "
                        + "is rejected at load time");
    }

    /** One ultimate fires its {@code ULT_CAST} rule — exactly once, not once per hit. */
    @Test
    public void ultCastFiresOncePerUltimate() {
        Battle battle = battleWith(rule("ULT_CAST", null, gainSkillPoint(ULT_CAST_POINTS)));
        drainSkillPoints(battle);

        Assertions.assertTrue(castUltimate(battle), "precondition: the ultimate is castable at full energy");
        Assertions.assertEquals(ULT_CAST_POINTS, battle.getSkillPoints(),
                "one ultimate must fire the ULT_CAST rule exactly once (an AoE ultimate hits 3 targets and "
                        + "must still fire it once)");
    }

    /** A basic attack, a skill and a map attack are not ultimates. */
    @Test
    public void ultCastDoesNotFireForOtherSkillTypes() {
        Battle battle = battleWith(rule("ULT_CAST", null, gainSkillPoint(ULT_CAST_POINTS)));
        Character hero = battle.characters.getFirst();

        drainSkillPoints(battle);
        cast(battle, hero, SkillType.COMMON);
        Assertions.assertEquals(0, battle.getSkillPoints(), "a basic attack is not an ultimate");

        cast(battle, hero, SkillType.SKILL);
        Assertions.assertEquals(0, battle.getSkillPoints(), "a skill is not an ultimate");

        cast(battle, hero, SkillType.MAZE);
        Assertions.assertEquals(0, battle.getSkillPoints(), "the overworld basic attack is not an ultimate");

        cast(battle, hero, SkillType.TALENT);
        Assertions.assertEquals(0, battle.getSkillPoints(),
                "a talent has no attack_type in the data, so it is not an ultimate either");
    }

    // ==================================================================
    // 2. The split from SKILL_CAST
    // ==================================================================

    /**
     * An ultimate is {@code ULT_CAST} and <b>not</b> {@code SKILL_CAST}; every other cast is
     * {@code SKILL_CAST} and not {@code ULT_CAST}.
     *
     * <p>Both rules are in one table with different fingerprints (1 vs 2 points), so a single point
     * count decides which one ran: 1 means only the ultimate rule, 2 means only the skill rule.
     */
    @Test
    public void ultCastAndSkillCastAreMutuallyExclusive() {
        Battle battle = battleWith(
                rule("ULT_CAST", null, gainSkillPoint(ULT_CAST_POINTS)),
                rule("SKILL_CAST", null, gainSkillPoint(SKILL_CAST_POINTS)));
        Character hero = battle.characters.getFirst();

        drainSkillPoints(battle);
        Assertions.assertTrue(castUltimate(battle));
        Assertions.assertEquals(ULT_CAST_POINTS, battle.getSkillPoints(),
                "the ultimate must fire ONLY the ULT_CAST rule; 1 + " + SKILL_CAST_POINTS + " would mean "
                        + "SKILL_CAST fired for an ultimate, which would make 'when the wearer uses their "
                        + "Skill' rules fire on ultimates too");

        drainSkillPoints(battle);
        cast(battle, hero, SkillType.SKILL);
        Assertions.assertEquals(SKILL_CAST_POINTS, battle.getSkillPoints(),
                "a skill must fire ONLY the SKILL_CAST rule");
    }

    /**
     * The split must not swallow the attack: an ultimate that lands is still an ally attack.
     *
     * <p>Robin-style party buffs hang on {@code ALLY_ATTACK}; gating them behind "not an ultimate"
     * would silently drop every damaging ultimate from them.
     */
    @Test
    public void allyAttackStillFiresForUltimates() {
        Battle battle = battleWith(rule("ALLY_ATTACK", List.of("actor == self", "hit_count > 0"),
                gainSkillPoint(ALLY_ATTACK_POINTS)));
        drainSkillPoints(battle);

        Assertions.assertTrue(castUltimate(battle));
        Assertions.assertEquals(ALLY_ATTACK_POINTS, battle.getSkillPoints(),
                "a damaging ultimate is still an attack, so ALLY_ATTACK must fire for it");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /** A battle whose single character carries exactly the given trigger rules. */
    private static Battle battleWith(TriggerSpec... specs) {
        Character hero = CharacterFactory.create(HIMEKO, CHARACTER_LEVEL);
        hero.setTriggerTable(new TriggerTable(HIMEKO, List.of(specs)));
        Enemy enemy = EnemyFactory.create(ICE_EDGE, ENEMY_LEVEL, 1);
        enemy.setAttribute(AttributeType.HEALTH, new DoubleValue(ENEMY_HP));
        enemy.heal(ENEMY_HP);
        Battle battle = new Battle(List.of(hero), List.of(enemy), new Random(0));
        battle.startBattle();
        return battle;
    }

    /** Casts the hero's ultimate for real (through {@code Battle.castUltra}, energy gate included). */
    private static boolean castUltimate(Battle battle) {
        Character hero = battle.characters.getFirst();
        hero.setCurrentEnergy(hero.getMaxEnergy());
        return battle.castUltra(hero, List.of(battle.enemies.getFirst()));
    }

    /** Casts a skill slot directly, bypassing the action bar and the skill-point policy. */
    private static void cast(Battle battle, Character hero, SkillType slot) {
        Skill skill = hero.getSkills().get(slot);
        Assertions.assertNotNull(skill, "precondition: the character carries the " + slot + " slot");
        Assertions.assertNotEquals(SkillCategory.ULTRA, skill.getData().getCategory(),
                "precondition: the " + slot + " slot used in this test is not an ultimate");
        battle.castImmediate(skill, hero, List.of(battle.enemies.getFirst()));
    }

    /** Empties the skill points so the next gain can only come from a rule under test. */
    private static void drainSkillPoints(Battle battle) {
        while (battle.spendSkillPoint()) {
            // drain to zero
        }
        Assertions.assertEquals(0, battle.getSkillPoints(), "precondition: no skill points left");
    }

    private static TriggerSpec rule(String on, List<String> when, EffectSpec effect) {
        TriggerSpec spec = new TriggerSpec();
        set(spec, "on", on);
        if (when != null) {
            set(spec, "when", when);
        }
        set(spec, "doEffects", List.of(effect));
        set(spec, "source", "UltCastTriggerTest");
        return spec;
    }

    private static EffectSpec gainSkillPoint(double amount) {
        EffectSpec effect = new EffectSpec();
        set(effect, "op", "GAIN_SKILL_POINT");
        set(effect, "amount", amount);
        return effect;
    }

    /**
     * Sets a private field reflectively: the trigger beans are Lombok {@code @Getter} only (they are
     * read from JSON), and the project's convention is to build them this way in tests rather than
     * widening the production API for test convenience.
     */
    private static void set(Object target, String field, Object value) {
        try {
            var declared = target.getClass().getDeclaredField(field);
            declared.setAccessible(true);
            declared.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot set " + field, e);
        }
    }
}
