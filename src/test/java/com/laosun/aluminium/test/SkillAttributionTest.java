package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.ResourceSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.DefaultSkill;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * "when casting the <b>Skill</b> and breaking an enemy target's weakness" - an event that says <b>who</b>, and now also <b>which skill</b>.
 *
 * <p><b>Why the DSL needed it.</b> {@code BREAK} and {@code KILL} already carried the actor, so "when an enemy target's weakness is broken"
 * (Himeko (姬子)'s talent: anybody's break) was expressible. "when casting the Skill... and breaking a weakness" (her Eidolon (星魂) 4) was not: gating on
 * {@code actor == self} would also pay for a break left by her basic attack or by her talent's follow-up attack, and the
 * text excludes both - a wrong +1 charge (充能) with nothing to report. So the causing instance now rides into the event
 * ({@code Battle.reduceToughness(..., Damage)} to {@code TriggerContext.damage()}) and {@code from_skill SKILL} compares its
 * cast category against {@code SkillCategory.of(SkillType.SKILL)}.
 *
 * <p><b>How the cases are built.</b> The mechanism cases use a hand-made table holding exactly two break rules - the
 * talent's (anybody's break) and the Eidolon's (my Skill's break) - so the numbers are the rules and not the rest of her
 * kit. The shipped-content cases drive her real file, where a skill break now reaches the 3-charge cap and her talent's
 * follow-up attack spends it (which is itself the observable: at Eidolon (星魂) 4 the follow-up happens, at Eidolon (星魂) 3 it does not).
 */
public class SkillAttributionTest {
    private static final int HIMEKO = 1003;
    private static final int LEVEL = 80;
    /** Ice Edge (冰锋): toughness 60, weak to FIRE/THUNDER - one Skill (战技) (stance 60) breaks it. */
    private static final int MONSTER = 1002011;
    private static final int SKILL_SLOT = 2;
    private static final int BASIC_SLOT = 1;
    private static final int ULTIMATE_SLOT = 3;
    private static final String CHARGE = "充能";
    private static final int CAP = 3;

    /** Her Skill (战技) breaks it: both break rules pay, so two charges. */
    @Test
    public void theSkillThatBreaksPaysTheEidolon() {
        Fixture f = new Fixture();

        f.breakWith(new DefaultSkill(HIMEKO, SKILL_SLOT, 1));

        Assertions.assertEquals(2, f.charge(),
                "the talent pays for anybody's break, and 星魂 4 pays a second one for a break caused by her 战技");
    }

    /**
     * Note: The case the condition exists for: her <b>basic attack</b> breaks, and Eidolon (星魂) 4 must not pay.
     *
     * <p>The bar is chipped first through the raw API (which emits no break event), so the 30-point basic attack is what
     * empties it.
     */
    @Test
    public void aBreakFromHerBasicAttackDoesNotPayTheEidolon() {
        Fixture f = new Fixture();
        f.chipToughness(35);

        f.breakWith(new DefaultSkill(HIMEKO, BASIC_SLOT, 1));

        Assertions.assertEquals(1, f.charge(),
                "a 普攻 break is the talent's, not 星魂 4's -- and `actor == self` alone cannot tell them apart");
    }

    /**
     * Note: A different slot is a different slot: her <b>ultimate</b> breaks, and Eidolon (星魂) 4 (which names the Skill (战技)) must not pay.
     *
     * <p>Note: The talent (天赋) branch cannot be exercised at all, and that is a fact about the engine worth stating rather than
     * hiding: a follow-up attack is settled as <b>additional damage</b> ({@code Battle.applyAdditionalDamage}), which
     * never reduces toughness - so her talent's follow-up cannot leave a break here. The contrast that matters is
     * therefore slot against slot, and her ultimate is the other slot that can break (AoE stance 60).
     */
    @Test
    public void aBreakFromHerUltimateDoesNotPayTheEidolon() {
        Fixture f = new Fixture();

        f.breakWith(new DefaultSkill(HIMEKO, ULTIMATE_SLOT, 1));

        Assertions.assertEquals(1, f.charge(), "「施放战技」 is not 「施放终结技」");
    }

    /** The actor gate still matters: a break caused by an ally's Skill (战技) pays the talent but not her Eidolon. */
    @Test
    public void anAlliesSkillDoesNotPayHerEidolon() {
        Fixture f = new Fixture();
        Character ally = CharacterFactory.create(1002, LEVEL);

        f.battle.reduceToughness(ally, f.enemy, DamageElement.FIRE, 60, SkillCategory.BPSKILL);

        Assertions.assertEquals(1, f.charge(), "an ally's skill break is not 「姬子施放战技」");
    }

    /** Note: A break with no causing instance answers false: the event happened, but nothing can say which slot caused it. */
    @Test
    public void aBreakWithNoCausingInstanceIsNotAttributed() {
        Fixture f = new Fixture();

        f.battle.reduceToughness(f.hero, f.enemy, DamageElement.FIRE, 60);

        Assertions.assertEquals(1, f.charge(),
                "the unconditional rule pays (anybody's break), the conditioned one cannot be satisfied without an "
                        + "instance");
    }

    /** Note: A slot that can never be an in-battle cast is refused at load, rather than never matching. */
    @Test
    public void anOutOfBattleSlotIsRefusedAtLoad() {
        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> tableOf("from_skill TECHNIQUE"));

        Assertions.assertTrue(refused.getMessage().contains("TECHNIQUE"), refused.getMessage());
        Assertions.assertTrue(refused.getMessage().contains("in-battle"), refused.getMessage());
    }

    /** A misspelled slot is refused with the list of the ones that work. */
    @Test
    public void anUnknownSlotIsRefusedAtLoad() {
        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> tableOf("from_skill ULTIMATE"));

        Assertions.assertTrue(refused.getMessage().contains("ULTIMATE"), refused.getMessage());
        Assertions.assertTrue(refused.getMessage().contains("ULTRA"), "the message lists the known slots");
    }

    /** Note: An event that carries no instance cannot answer the question, so the condition is refused on it. */
    @Test
    public void anEventWithoutACausingInstanceIsRefusedAtLoad() {
        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> tableOfOn("TURN_START", "from_skill SKILL"));

        Assertions.assertTrue(refused.getMessage().contains("TURN_START"), refused.getMessage());
        Assertions.assertTrue(refused.getMessage().contains("BREAK"), "the message lists the events that work");
    }

    /** A subject is refused: "who caused it" is its own condition. */
    @Test
    public void aSubjectOnFromSkillIsRefusedAtLoad() {
        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> tableOf("self from_skill SKILL"));

        Assertions.assertTrue(refused.getMessage().contains("no subject"), refused.getMessage());
    }

    // ==================================================================
    // The shipped content: 1003 Himeko (姬子) Eidolon (星魂) 4
    // ==================================================================

    /**
     * End to end, through her real file: at Eidolon (星魂) 4 a Skill (战技) break fills the charge (充能) to the cap and her talent's follow-up fires.
     *
     * <p>Note: Why the measurement is "did the follow-up happen" rather than a charge count: her own kit spends the charges
     * as soon as they reach 3, so the follow-up <b>is</b> the observable consequence of the extra charge. At Eidolon (星魂) 3 the
     * same break leaves 2 charges and nothing else happens - the contrast is the Eidolon.
     */
    @Test
    public void theShippedEidolonPushesTheSkillBreakToTheCap() {
        Outcome without = shippedSkillBreak(3);
        Outcome with = shippedSkillBreak(4);

        Assertions.assertEquals(2, without.charge(), "talent +1 only, below the cap");
        Assertions.assertEquals(0, with.charge(), "the extra charge reaches the cap and the follow-up spends all three");
        Assertions.assertTrue(with.enemyHp() < without.enemyHp(),
                "the extra charge is what made her talent's follow-up attack happen");
    }

    /** ...and the shipped rule really states BOTH conditions, rather than only the actor. */
    @Test
    public void theShippedRuleStatesTheSkillCondition() {
        Character owner = CharacterFactory.create(HIMEKO, LEVEL, true, null, null, 4);
        // The context states the cast category, because `matching` evaluates the conditions -- a context without one
        // would filter the very rule this case is about out of the list.
        TriggerTable.CompiledRule rule = TriggerTables.of(HIMEKO)
                .matching(TriggerEvent.BREAK,
                        new TriggerTable.TriggerContext(owner, owner, null, 0, 0, null, null, SkillCategory.BPSKILL))
                .stream()
                .filter(candidate -> candidate.minEidolon() == 4)
                .findFirst()
                .orElseThrow(() -> new AssertionError("星魂 4's rule is gone from characters/1003.json"));

        Assertions.assertEquals(List.of("actor == self", "from_skill SKILL"),
                rule.conditions().stream().map(TriggerTable.Condition::source).toList());
        Assertions.assertEquals("GAIN_RESOURCE", rule.effects().getFirst().getOp());
        Assertions.assertEquals(CHARGE, rule.effects().getFirst().getResource());
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /**
     * The mechanism fixture: her two break rules and nothing else, so a charge count is the rules' own arithmetic.
     *
     * <p>Note: The resource is registered by hand: {@code setTriggerTable} replaces the table, and the declaration that
     * registers a resource is read while the character is <b>built</b> (one reader, in {@code CharacterFactory}).
     */
    private static final class Fixture {
        private final Character hero;
        private final Enemy enemy;
        private final Battle battle;

        /** A fixture with the two break rules; {@code withFollowUp} adds her talent's follow-up attack too. */
        private Fixture() {
            this(false);
        }

        private Fixture(boolean withFollowUp) {
            this.hero = CharacterFactory.create(HIMEKO, LEVEL);
            List<TriggerSpec> rules = new ArrayList<>();
            rules.add(TriggerSpecs.rule("BREAK", null, chargeOne()));
            rules.add(TriggerSpecs.rule("BREAK", List.of("actor == self", "from_skill SKILL"), chargeOne()));
            // Note: No manual registration: her own file declares charge (充能) and `CharacterFactory` registers it while the
            // character is built, and the manager keeps it even though the table is replaced below.
            hero.setTriggerTable(new TriggerTable(HIMEKO, rules));
            this.enemy = breakableEnemy();
            this.battle = new Battle(List.of(hero), List.of(enemy), fixed(0.0));
            battle.startBattle();
        }

        private void breakWith(com.laosun.aluminium.models.skill.Skill skill) {
            battle.castImmediate(skill, hero, List.of(enemy));
        }

        /** Removes toughness <b>without</b> emitting a break event, so a smaller hit can be the one that breaks. */
        private void chipToughness(double amount) {
            enemy.reduceStance(amount);
        }

        private int charge() {
            return hero.getResources().value(CHARGE);
        }
    }

    /** What one skill break did to her charge (充能) and to the enemy, through her real file. */
    private record Outcome(int charge, double enemyHp) {
    }

    private static Outcome shippedSkillBreak(int rank) {
        Character hero = CharacterFactory.create(HIMEKO, LEVEL, true, null, null, rank);
        Enemy enemy = breakableEnemy();
        Battle battle = new Battle(List.of(hero), List.of(enemy), fixed(0.0));
        battle.startBattle();

        battle.castImmediate(new DefaultSkill(HIMEKO, SKILL_SLOT, 1), hero, List.of(enemy));

        return new Outcome(hero.getResources().value(CHARGE), enemy.getCurrentHp());
    }

    private static Enemy breakableEnemy() {
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        enemy.setStanceWeak(Set.of(DamageElement.FIRE));
        enemy.setAttribute(AttributeType.EFFECT_RESISTANCE, new DoubleValue(0));
        return enemy;
    }

    /** A `DAMAGE` from the TALENT slot - what her own file writes for the follow-up attack. */
    private static EffectSpec followUpDamage() {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "DAMAGE");
        TriggerSpecs.set(effect, "skill", "TALENT");
        TriggerSpecs.set(effect, "damageParam", 0);
        TriggerSpecs.set(effect, "damageLevel", 10);
        TriggerSpecs.set(effect, "target", "all_enemies");
        return effect;
    }

    /** One charge (充能), so the cases are about counting rather than about amounts. */
    private static EffectSpec chargeOne() {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "GAIN_RESOURCE");
        TriggerSpecs.set(effect, "resource", CHARGE);
        TriggerSpecs.set(effect, "amount", 1.0);
        return effect;
    }

    /** Compiles one `BREAK` rule carrying the condition, which is where its validation runs. */
    private static TriggerTable tableOf(String condition) {
        return tableOfOn("BREAK", condition);
    }

    private static TriggerTable tableOfOn(String event, String condition) {
        Character owner = CharacterFactory.create(HIMEKO, LEVEL);
        List<TriggerSpec> rules = List.of(TriggerSpecs.rule(event, List.of(condition), chargeOne()));
        owner.setTriggerTable(new TriggerTable(HIMEKO, rules, List.of(new ResourceSpec(CHARGE, CAP, 0))));
        return owner.getTriggerTable();
    }

    private static Random fixed(double value) {
        return new Random() {
            @Override
            public double nextDouble() {
                return value;
            }
        };
    }
}
