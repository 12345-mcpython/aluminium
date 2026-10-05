package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.buff.ShieldBuff;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * "在<b>战技提供的</b>护盾保护下的我方目标…" - a shield is asked about by <b>which rule created it</b>, not just "is there one".
 *
 * <p><b>Why the DSL needed it.</b> 1001 March 7th's 星魂 6 heals the shielded ally on their turn - but only through the shield
 * her <b>Skill</b> gives. She has two shields of her own (her Skill's and 星魂 2's at battle start), so a condition that
 * asked only "has a shield" - or even "a shield from March 7th" - would also heal through the 星魂 2 one for the three turns it
 * lasts: a wrong number with nothing to report. So the shield remembers the rule that created it
 * ({@code CanHit.getShieldRuleId()}, stamped where every buff already gets its source) and the condition can require it:
 * {@code <who> has_shield from_rule <id>}.
 *
 * <p><b>What this file pins.</b> That the origin really distinguishes the two shields, that a raw grant answers "no"
 * (nothing created it), that plain {@code has_shield} is unchanged, that an id which resolves to nothing - or to a rule
 * that makes no shield - is refused at load, and that her file ships 星魂 6 with that condition.
 */
public class ShieldOriginConditionTest {
    private static final double EPS = 1e-6;

    private static final int MARCH_7TH = 1001;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** The Skill's shield satisfies the condition; the battle-start one (a different rule) does not. */
    @Test
    public void onlyTheNamedRulesShieldCounts() {
        Assertions.assertTrue(healFires(true, "skill_shield"),
                "「战技提供的护盾」 is the shield that rule created");
        Assertions.assertFalse(healFires(true, "eidolon_shield"),
                "…and a different rule of hers is a different shield, even though the giver is the same");
    }

    /** Note: The negative case stated the other way: with ONLY the battle-start shield up, the Skill's condition is false. */
    @Test
    public void theBattleStartShieldDoesNotSatisfyIt() {
        Assertions.assertFalse(healFires(false, "skill_shield"),
                "the ally holds a shield from March 7th, but not from that rule");
    }

    /** Plain {@code has_shield} still means "any living shield", whichever rule made it. */
    @Test
    public void theUnqualifiedConditionIsUnchanged() {
        Assertions.assertTrue(healFires(false, ""), "no qualifier: the battle-start shield is enough");
        Assertions.assertTrue(healFires(true, ""), "…and so is the Skill's");
    }

    /** Note: A shield nothing created (a raw grant) states no rule, so a {@code from_rule} question answers no. */
    @Test
    public void aRawGrantHasNoOrigin() {
        Assertions.assertFalse(healFiresRaw(), "setShield states no rule, and guessing one would be worse");
    }

    /** A raw grant still satisfies the unqualified condition. */
    @Test
    public void aRawGrantStillCountsAsAShield() {
        Assertions.assertTrue(healFiresRawWithPlainCondition());
    }

    /** Note: An id that resolves to nothing is refused at load: a condition that can never hold must not load. */
    @Test
    public void anUnknownRuleIdIsRefusedAtLoad() {
        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> tableOf("target has_shield from_rule no_such_rule", shieldRule("skill_shield")));

        Assertions.assertTrue(refused.getMessage().contains("no_such_rule"), refused.getMessage());
        Assertions.assertTrue(refused.getMessage().contains("skill_shield"), "the message lists the ids it knows");
    }

    /** Note: An id that names a rule which creates no shield is refused too - the condition could never hold. */
    @Test
    public void anIdThatMakesNoShieldIsRefusedAtLoad() {
        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> tableOf("target has_shield from_rule a_buff_rule", buffRule("a_buff_rule")));

        Assertions.assertTrue(refused.getMessage().contains("no SHIELD effect"), refused.getMessage());
    }

    /** An unknown qualifier after {@code has_shield} is refused, naming the one that works. */
    @Test
    public void anUnknownQualifierIsRefusedAtLoad() {
        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> tableOf("target has_shield from self", shieldRule("skill_shield")));

        Assertions.assertTrue(refused.getMessage().contains("from_rule"), refused.getMessage());
    }

    // ==================================================================
    // The shipped content: 1001, 星魂 6
    // ==================================================================

    /**
     * Her file ships the clause, and the two shields really are told apart end to end.
     *
     * <p>The measurement is on her <b>real file</b> and her real HEAL: with the Skill's shield up the ally is healed on
     * their turn; with only the battle-start shield (星魂 2's) up they are not - same battle shape, same eidolon rank.
     */
    @Test
    public void theShippedEidolonHealsOnlyThroughTheSkillsShield() {
        double throughTheSkill = shippedTurnStartHeal(true);
        double throughTheBattleStart = shippedTurnStartHeal(false);

        Assertions.assertTrue(throughTheSkill > 0, "「每回合开始时回复等同于各自4%生命上限+106」");
        Assertions.assertEquals(0, throughTheBattleStart, EPS,
                "only 「战技提供的护盾」 qualifies -- the 星魂 2 shield is hers too, and must not");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /**
     * Whether the condition-gated heal fires for an ally whose shield came from {@code ruleId}.
     *
     * @param fromTheSkill {@code true} to the shield is installed by {@code skill_shield}; {@code false} to by
     *                     {@code eidolon_shield}
     * @param asked        the rule the condition names ({@code ""} = the unqualified spelling)
     */
    private static boolean healFires(boolean fromTheSkill, String asked) {
        Character hero = CharacterFactory.create(MARCH_7TH, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        hero.setTriggerTable(new TriggerTable(MARCH_7TH, List.of(
                healOnTurnStart(asked), shieldRule("skill_shield"), shieldRule("eidolon_shield"))));
        Battle battle = new Battle(List.of(hero, ally), List.of(monster()), fixed());
        battle.startBattle();

        hurt(ally);
        installShield(ally, fromTheSkill ? "skill_shield" : "eidolon_shield");
        return turnStartHeals(battle, hero, ally);
    }

    /** The same, with a shield written straight onto the ally (no rule created it). */
    private static boolean healFiresRaw() {
        return rawCase(false);
    }

    private static boolean healFiresRawWithPlainCondition() {
        return rawCase(true);
    }

    private static boolean rawCase(boolean plainCondition) {
        Character hero = CharacterFactory.create(MARCH_7TH, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        List<TriggerSpec> rules = new java.util.ArrayList<>();
        rules.add(healOnTurnStart(plainCondition ? "" : "skill_shield"));
        if (!plainCondition) {
            // The reference must resolve (load-time check) -- what is missing is the shield it names.
            rules.add(shieldRule("skill_shield"));
        }
        hero.setTriggerTable(new TriggerTable(MARCH_7TH, rules));
        Battle battle = new Battle(List.of(hero, ally), List.of(monster()), fixed());
        battle.startBattle();

        hurt(ally);
        ally.setShield(700);
        return turnStartHeals(battle, hero, ally);
    }

    /** Fires the ally's own turn start and reports whether March 7th's rule healed them. */
    private static boolean turnStartHeals(Battle battle, Character hero, Character ally) {
        double before = ally.getCurrentHp();
        battle.fireTriggers(TriggerEvent.TURN_START, ally, ally, 0, 0);
        return ally.getCurrentHp() > before;
    }

    /** Takes half of a unit's HP, so a heal has something to restore (a heal at full HP restores 0). */
    private static void hurt(Character unit) {
        unit.takeDamage(unit.getMaxHp() * 0.5);
    }

    private static void installShield(Character ally, String ruleId) {
        ShieldBuff shield = new ShieldBuff(CharacterFactory.create(MARCH_7TH, LEVEL), 700, 3);
        shield.setRuleId(ruleId);
        ally.getBuffManager().addBuff(shield);
    }

    /** A rule that gives {@code ruleId}'s shield to its owner's ally - the shape a shield rule has. */
    private static TriggerSpec shieldRule(String ruleId) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "SHIELD");
        TriggerSpecs.set(effect, "amount", 700.0);
        TriggerSpecs.set(effect, "turns", 3);
        TriggerSpecs.set(effect, "target", "target");
        TriggerSpec rule = TriggerSpecs.rule("TURN_START", null, effect);
        TriggerSpecs.set(rule, "id", ruleId);
        return rule;
    }

    /** A rule with no shield in it, for the "the id resolves but creates nothing" case. */
    private static TriggerSpec buffRule(String ruleId) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "MODIFY_ATTR");
        TriggerSpecs.set(effect, "attribute", "ATTACK");
        TriggerSpecs.set(effect, "percent", 0.1);
        TriggerSpecs.set(effect, "permanent", true);
        TriggerSpec rule = TriggerSpecs.rule("BATTLE_START", null, effect);
        TriggerSpecs.set(rule, "id", ruleId);
        return rule;
    }

    /** The heal under test: 4% of the target's own maximum + 106, on their turn start, gated on the shield. */
    private static TriggerSpec healOnTurnStart(String askedRule) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "HEAL");
        TriggerSpecs.set(effect, "scale", "target_max_hp");
        TriggerSpecs.set(effect, "percent", 0.04);
        TriggerSpecs.set(effect, "amount", 106.0);
        TriggerSpecs.set(effect, "target", "target");
        String condition = askedRule.isEmpty() ? "target has_shield" : "target has_shield from_rule " + askedRule;
        return TriggerSpecs.rule("TURN_START", List.of(condition), effect);
    }

    /** Compiles one condition into a table, which is where its cross-rule validation runs. */
    private static TriggerTable tableOf(String condition, TriggerSpec namedRule) {
        Character owner = CharacterFactory.create(MARCH_7TH, LEVEL);
        List<TriggerSpec> rules = List.of(
                TriggerSpecs.rule("TURN_START", List.of(condition), heal()),
                namedRule);
        owner.setTriggerTable(new TriggerTable(MARCH_7TH, rules));
        return owner.getTriggerTable();
    }

    /** A bare heal, so the tables above are about the condition rather than about the amount. */
    private static EffectSpec heal() {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "HEAL");
        TriggerSpecs.set(effect, "amount", 10.0);
        TriggerSpecs.set(effect, "target", "target");
        return effect;
    }

    /** March 7th with her real file, one ally, and 星魂 6: heal on the ally's turn start, through one of the two shields. */
    private static double shippedTurnStartHeal(boolean throughTheSkill) {
        Character hero = CharacterFactory.create(MARCH_7TH, LEVEL, true, null, null, 6);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Battle battle = new Battle(List.of(hero, ally), List.of(monster()), fixed());
        // Note: Hurt the ally BEFORE the battle starts, so 星魂 2's lowest_hp_ally shield really lands on them (at full
        // HP everybody ties and the first in party order wins, which would be March 7th herself).
        hurt(ally);
        ally.heal(ally.getMaxHp());
        ally.takeDamage(ally.getMaxHp() * 0.5);
        battle.startBattle();
        if (throughTheSkill) {
            // The Skill's shield overwrites the battle-start one, and it is the one 星魂 6 names.
            battle.fireTriggers(TriggerEvent.SKILL_CAST, hero, ally, 0, 0);
        }
        double before = ally.getCurrentHp();
        battle.fireTriggers(TriggerEvent.TURN_START, ally, ally, 0, 0);
        return ally.getCurrentHp() - before;
    }

    private static Enemy monster() {
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
