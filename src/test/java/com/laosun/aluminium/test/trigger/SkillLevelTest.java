package com.laosun.aluminium.test.trigger;


import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.Skill;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * {@code RAISE_SKILL_LEVEL} and the one level resolver behind it: "战技等级+1""终结技等级+1" (Skill level +1, Ultimate level +1).
 *
 * <p><b>The gap.</b> Every skill in this engine is read at level 1 unless a rule pins the row with
 * {@code damage_level}, because a character had no skill levels at all. So "战技等级+1" (Skill level +1) had nowhere to land: writing
 * the rule again at another level would double the cast instead of raising it.
 *
 * <p><b>The model.</b> A level is <b>base + this battle's raises</b>, resolved in exactly one place
 * ({@code CanHit.skillLevel}) and used by every site that indexes a parameter table: a skill's own execution
 * ({@code SkillExecutor}, both the damaging path and the generated effect tables) and a rule-driven {@code DAMAGE}
 * ({@code TriggerInterpreter.multiplierOf}). A {@code damage_level} on a rule still wins - it is the author stating
 * "the document quotes this row", a different statement from "my skill is level N".
 *
 * <p>Note: The cases below <b>find</b> a slot whose parameter rows really differ between level 1 and level 10 instead of
 * naming one: the first version of this file asserted a raise on Himeko's ultimate and measured <b>the same number
 * twice</b> (her ultimate's own rows are level-independent) - a test that looks like it checks the wiring and does not.
 */
public class SkillLevelTest {
    private static final int HIMEKO = 1003;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final int RAISE = 9;

    /** The resolver itself: base level + this battle's raise, per slot. */
    @Test
    public void theResolverAddsTheRaiseToTheBase() {
        Fixture f = new Fixture(RAISE, SkillType.ULTRA);
        Skill ultimate = f.hero.getSkills().get(SkillType.ULTRA);

        Assertions.assertEquals(ultimate.getLevel() + RAISE, f.hero.skillLevel(ultimate),
                "「终结技等级+9」 (Ultimate Lv. +9) raises what the engine reads");
        Assertions.assertEquals(RAISE, f.hero.skillLevelBonus(SkillType.ULTRA), "…filed for this battle");
        Assertions.assertEquals(0, f.hero.skillLevelBonus(SkillType.SKILL), "…and only that slot");
    }

    /** Her Skill's own cast reads the raised row: the swing grows when the slot's rows differ. */
    @Test
    public void theSkillsOwnCastReadsTheRaise() {
        SkillType slot = aSlotWhoseRowsDiffer();
        double atBase = castDamage(slot, 0);
        double raised = castDamage(slot, RAISE);

        Assertions.assertTrue(raised > atBase * 1.01,
                slot + ": raising it by " + RAISE + " levels must raise its own swing (base " + atBase + " vs raised "
                        + raised + ")");
    }

    /**
     * Note: The rule-driven {@code DAMAGE} reads the raised row - pinned against the <b>data's own ratio</b>, not against
     * the cast's total.
     *
     * <p>Why not "the cast grew by the same factor": a skill's row has several parameters and only one of them is the
     * multiplier this rule reads, so the cast's <i>total</i> does not scale linearly with it (measured on Himeko's SKILL:
     * the cast went 11545 to 12325 (1.068) while its parameter 0 went 1.0 to 2.0). Comparing those two ratios would have
     * been an assertion about the skill's other parameters wearing the label "the resolver agrees with itself".
     */
    @Test
    public void aRuleDrivenDamageReadsTheSameRow() {
        SkillType slot = aSlotWhoseRowsDiffer();
        double ruleBase = ruleDamage(slot, null, 0);
        double ruleRaised = ruleDamage(slot, null, RAISE);
        double dataRatio = rowRatio(slot);

        Assertions.assertEquals(dataRatio, ruleRaised / ruleBase, 0.02,
                slot + ": the rule read row " + (RAISE + 1) + " (data ratio " + dataRatio + ", measured " + ruleBase
                        + "→" + ruleRaised + ")");
    }

    /** How much one slot's first parameter grows between level 1 and level {@code RAISE + 1}, from the data itself. */
    private static double rowRatio(SkillType slot) {
        List<List<Double>> rows = CharacterFactory.create(HIMEKO, LEVEL).getSkills().get(slot).getData().getSkills();
        return rows.get(RAISE).getFirst() / rows.getFirst().getFirst();
    }

    /** An explicit {@code damage_level} still wins: it says which row the document quotes. */
    @Test
    public void anExplicitRowBeatsTheRaise() {
        SkillType slot = aSlotWhoseRowsDiffer();
        Assertions.assertEquals(ruleDamage(slot, 1, 0), ruleDamage(slot, 1, RAISE), 0.5,
                slot + ": a rule stating `damage_level: 1` reads row 1 whether or not the battle raised the slot");
    }

    /** Note: Per battle, not per character: the raise is cleared and re-earned, never stacked by reuse. */
    @Test
    public void theRaiseDoesNotSurviveIntoTheNextBattle() {
        Character hero = CharacterFactory.create(HIMEKO, LEVEL);
        hero.setTriggerTable(new TriggerTable(HIMEKO, List.of(raise(SkillType.ULTRA, RAISE))));
        new Battle(List.of(hero), List.of(enemy()), fixed()).startBattle();
        Assertions.assertEquals(RAISE, hero.skillLevelBonus(SkillType.ULTRA), "the first battle raised it");

        Character fresh = CharacterFactory.create(HIMEKO, LEVEL);
        fresh.setTriggerTable(new TriggerTable(HIMEKO, List.of(raise(SkillType.ULTRA, RAISE))));
        Assertions.assertEquals(0, fresh.skillLevelBonus(SkillType.ULTRA),
                "a fresh combatant has no raises before its battle starts");

        new Battle(List.of(hero), List.of(enemy()), fixed()).startBattle();
        Assertions.assertEquals(RAISE, hero.skillLevelBonus(SkillType.ULTRA),
                "the same object reused: cleared, then raised again by its own BATTLE_START rule — 9, not 18");
    }

    /**
     * Note: <b>The third read site</b>: a skill whose amount comes from the <b>generated effect table</b>
     * ({@code skill_effects.json} + {@code SkillExecutor.effectAmount}), not from its damaging path.
     *
     * <p>Mutation m3 (that call site going back to the raw level) was the one mutation this file did not catch: every
     * other case measures damage. 1105 is the clean subject - it has a {@code healer_max_hp}-scaled heal in its
     * generated table and <b>no character file</b>, so no rule of its own can overwrite what is being measured.
     */
    @Test
    public void aGeneratedEffectReadsTheRaise() {
        double base = generatedHeal(0);
        double raised = generatedHeal(RAISE);

        Assertions.assertTrue(base > 0, "precondition: 1105's Skill really heals through the generated table");
        Assertions.assertTrue(raised > base,
                "the generated effect reads the raised row too (base " + base + " vs raised " + raised + ")");
    }

    /** 1105's Skill, cast on a hurt ally, measured as the HP restored. */
    private static double generatedHeal(int raiseBy) {
        Character healer = CharacterFactory.create(1105, LEVEL);
        Character ally = CharacterFactory.create(1002, LEVEL);
        healer.setTriggerTable(new TriggerTable(1105, raiseBy > 0 ? List.of(raise(SkillType.SKILL, raiseBy)) : List.of()));
        Battle battle = new Battle(List.of(healer, ally), List.of(enemy()), fixed());
        battle.startBattle();
        ally.takeDamage(ally.getMaxHp() * 0.5);
        double before = ally.getCurrentHp();
        battle.castImmediate(healer.getSkills().get(SkillType.SKILL), healer, List.of(ally));
        return ally.getCurrentHp() - before;
    }

    /** The shape is refused at load: no slot, an unknown slot, a zero or fractional amount, a wrong event. */
    @Test
    public void theShapeIsRefusedAtLoad() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> table(ruleSpec(null, 1.0, "BATTLE_START")),
                "no \"skill\"");
        Assertions.assertThrows(IllegalArgumentException.class, () -> table(ruleSpec("NOPE", 1.0, "BATTLE_START")),
                "unknown slot");
        Assertions.assertThrows(IllegalArgumentException.class, () -> table(ruleSpec("ULTRA", 0.0, "BATTLE_START")),
                "0 levels is a rule that provably does nothing");
        Assertions.assertThrows(IllegalArgumentException.class, () -> table(ruleSpec("ULTRA", 1.5, "BATTLE_START")),
                "half a level");
        Assertions.assertThrows(IllegalArgumentException.class, () -> table(ruleSpec("ULTRA", 1.0, "TURN_START")),
                "a raise applied mid-battle is never taken back");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /**
     * A slot of Himeko's whose parameter row really changes between level 1 and level 10.
     *
     * <p>Note: Found, not assumed: her ultimate's rows are level-independent, so a hand-picked slot gave
     * "11841.82 vs 11841.82" and an assertion that could never fail.
     */
    private static SkillType aSlotWhoseRowsDiffer() {
        Character hero = CharacterFactory.create(HIMEKO, LEVEL);
        for (SkillType slot : List.of(SkillType.SKILL, SkillType.ULTRA, SkillType.COMMON, SkillType.TALENT)) {
            Skill skill = hero.getSkills().get(slot);
            if (skill == null || skill.getData() == null || skill.getData().getSkills() == null) {
                continue;
            }
            List<List<Double>> rows = skill.getData().getSkills();
            if (rows.size() > RAISE && !rows.isEmpty() && rows.getFirst() != null && !rows.getFirst().isEmpty()
                    && !rows.getFirst().getFirst().equals(rows.get(RAISE).getFirst())) {
                return slot;
            }
        }
        throw new IllegalStateException(
                "no slot of " + HIMEKO + " has a parameter row that changes between level 1 and level 10, so this "
                        + "file cannot measure the level wiring at all (the data changed, not the engine)");
    }

    /** That slot's own cast, measured as the enemy's HP loss. */
    private static double castDamage(SkillType slot, int raiseBy) {
        Fixture f = new Fixture(raiseBy, slot);
        double before = f.enemy.getCurrentHp();
        f.battle.castImmediate(f.hero.getSkills().get(slot), f.hero, List.of(f.enemy));
        return before - f.enemy.getCurrentHp();
    }

    /** A rule-driven DAMAGE from the same slot, measured the same way. */
    private static double ruleDamage(SkillType slot, Integer level, int raiseBy) {
        Fixture f = new Fixture(raiseBy, slot);
        List<TriggerSpec> rules = new ArrayList<>();
        if (raiseBy > 0) {
            // Note: Only when it is a raise at all: an `amount: 0` RULE is refused at load (correctly), so adding it
            // unconditionally made the "no raise" control case fail before it could measure anything.
            rules.add(raise(slot, raiseBy));
        }
        rules.add(damageRule(slot, level));
        f.hero.setTriggerTable(new TriggerTable(HIMEKO, rules));
        double before = f.enemy.getCurrentHp();
        f.battle.fireTriggers(TriggerEvent.ALLY_ATTACK, f.hero, f.enemy, 0, 0);
        return before - f.enemy.getCurrentHp();
    }

    private static final class Fixture {
        /**
         * Note: The combatant <b>inside the battle</b>, not the object handed to the constructor: the engine may copy a
         * participant into the battle, and a raise filed on the copy is invisible from the original - the first
         * version of this file asserted on the original and read 0 (and cast with it, so the swing did not move
         * either: two failing cases with one cause).
         */
        private final Character hero;
        private final Enemy enemy;
        private final Battle battle;

        private Fixture(int raiseBy, SkillType slot) {
            Character original = CharacterFactory.create(HIMEKO, LEVEL);
            enemy = enemy();
            List<TriggerSpec> rules = new ArrayList<>();
            if (raiseBy > 0) {
                rules.add(raise(slot, raiseBy));
            }
            original.setTriggerTable(new TriggerTable(HIMEKO, rules));
            battle = new Battle(List.of(original), List.of(enemy), fixed());
            battle.startBattle();
            hero = (Character) battle.allies.stream()
                    .filter(unit -> unit instanceof Character)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("the battle has no character of ours"));
        }
    }

    private static TriggerSpec raise(SkillType slot, int amount) {
        return ruleSpec(slot == null ? null : slot.name(), (double) amount, "BATTLE_START");
    }

    private static TriggerSpec ruleSpec(String slot, Double amount, String event) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "RAISE_SKILL_LEVEL");
        TriggerSpecs.set(effect, "skill", slot);
        TriggerSpecs.set(effect, "amount", amount);
        return TriggerSpecs.rule(event, null, effect);
    }

    /** A rule-driven attack reading a slot's parameter row (optionally pinned to a level). */
    private static TriggerSpec damageRule(SkillType slot, Integer level) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "DAMAGE");
        TriggerSpecs.set(effect, "skill", slot.name());
        TriggerSpecs.set(effect, "damageParam", 0);
        TriggerSpecs.set(effect, "damageLevel", level);
        TriggerSpecs.set(effect, "target", "target");
        return TriggerSpecs.rule("ALLY_ATTACK", null, effect);
    }

    private static TriggerTable table(TriggerSpec rule) {
        Character owner = CharacterFactory.create(HIMEKO, LEVEL);
        return new TriggerTable(HIMEKO, List.of(rule));
    }

    private static Enemy enemy() {
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        enemy.setAttribute(AttributeType.EFFECT_RESISTANCE, new DoubleValue(0));
        return enemy;
    }

    private static Random fixed() {
        return new Random() {
            @Override
            public double nextDouble() {
                return 0.0;
            }
        };
    }
}
