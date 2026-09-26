package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 「无视目标 X% 的防御力」 as data — {@code DEFENCE_IGNORE} reached from a rule for the first time.
 *
 * <p><b>What already existed, and what did not.</b> The attribute has been read by
 * {@code Battle.assemble}'s defence zone since P1-6, and {@code Damage.DefenceArea} clamps it to
 * {@code [0,1]} while shrinking {@code effectiveDefence} — both covered by {@code DamagePipelineTest} and
 * {@code DamageZoneTest}. What had <b>never</b> run is the path in the middle: <b>no shipped rule file
 * granted it</b>, so "a rule can raise this attribute and the hit really gets bigger" was an untested
 * belief. That is the gap this class closes; 翡翠's Eidolon 4 is the first user.
 *
 * <p><b>Why the attribute is the right route here, and where it stops.</b> The text says
 * 「使翡翠造成的伤害无视敌方目标 12% 的防御力，持续 3 回合」 — a property of the wearer's damage <em>for a
 * while</em>, which is exactly an attribute buff. That covers most of the game's 「无视防御」 family
 * (波提欧 / 星期日 / 阮·梅 / 翡翠 all phrase it as a lasting effect on a unit), and it is worth recording
 * because the roadmap had listed "a DEF-ignore op" as the missing capability. It does <b>not</b> cover the
 * minority that scopes the ignore to <em>one attack</em> — 银枝's 「施放终结技时…」, 云璃's
 * 「发动反击造成伤害时…」, relic set 119's break damage — because a turn-based buff would leak onto whatever
 * else that unit does in the same window. Those stay registered and need the instance-scoped form plus a way
 * to ask what kind of damage the pending instance is.
 */
public class DefenceIgnoreTest {
    private static final double EPS = 1e-6;

    /** 翡翠 — no other test uses this id, so a rule file for her cannot invalidate anyone's premise. */
    private static final int JADE = 1314;
    /** 姬子, a plain attacker with no rule file of her own. */
    private static final int ATTACKER = 1003;
    private static final int LEVEL = 80;
    /** 冰锋: a real monster with real defence, so the zone arithmetic is not on a hand-made number. */
    private static final int MONSTER = 1002011;
    private static final int MONSTER_LEVEL = 90;

    // ==================================================================
    // 1. The path: a rule raises the attribute, and the hit gets bigger
    // ==================================================================

    /**
     * A rule granting {@code DEFENCE_IGNORE} really raises the damage a later hit settles.
     *
     * <p>Same character, same target, same base damage — the only difference is whether the rule fired. If
     * {@code assemble} stopped reading the attribute, or {@code MODIFY_ATTR} silently failed to land on a
     * ratio attribute, this is where it shows.
     */
    @Test
    public void aRuleGrantedDefenceIgnoreRaisesTheDamageOfLaterHits() {
        double without = damageWithGrant(null);
        double with = damageWithGrant(0.2);

        Assertions.assertTrue(with > without,
                "ignoring 20% of the target's DEF must raise the settled hit: " + with + " vs " + without);
        // The defence zone is (200 + 10L) / (def + 200 + 10L), so ignoring part of def raises damage by a
        // fraction of it -- more than nothing, well under the full 20%. A tighter bound would just restate
        // the formula, which DamageZoneTest already pins.
        Assertions.assertTrue(with < without * 1.2,
                "and it is a defence-zone effect, not a flat +20% damage: " + with + " vs " + without);
    }

    /**
     * Two grants of the same attribute <b>replace</b> each other unless the rule asks for stacks, and the
     * defence zone clamps at "ignore everything".
     *
     * <p>⚠ <b>The replacement half surprised me and is worth pinning</b>: authoring 波提欧's 16% and
     * 阮·梅's 20% as two plain rules gives <b>20%</b>, not 36% — {@code MODIFY_ATTR} defaults to
     * {@code max_stacks: 1}, i.e. "re-application replaces" (the engine's long-standing convention, stated in
     * {@code TriggerInterpreter}'s op table and pinned for other attributes by
     * {@code StatModifierStackingTest}). A content author who wants sources to add says so with
     * {@code max_stacks}, which the second half here measures — otherwise the symptom is a character who is
     * quietly weaker than the game with nothing to see.
     */
    @Test
    public void sameKindGrantsReplaceByDefaultAndStackOnlyWhenAsked() {
        Assertions.assertEquals(damageWithGrant(0.3), damageWithGrants(1, 0.3, 0.3), EPS,
                "two plain 0.3 grants land on one 0.3, because the second replaces the first");
        Assertions.assertEquals(damageWithGrant(0.6), damageWithGrants(2, 0.3, 0.3), EPS,
                "with max_stacks: 2 the same two grants add up to 0.6");
        Assertions.assertTrue(damageWithGrant(0.6) > damageWithGrant(0.3),
                "…so more ignore really is more damage, and the replacement above is not hiding a no-op");
    }

    /** Everything past "ignore it all" is the same hit: the zone clamps the ratio to 100%. */
    @Test
    public void fullIgnoreIsTheCeiling() {
        Assertions.assertEquals(damageWithGrant(1.0), damageWithGrant(1.5), EPS,
                "1.5 (or any larger number) clamps to 1.0 in the defence zone");
        Assertions.assertTrue(damageWithGrant(1.0) > damageWithGrant(0.9),
                "and full ignore is still more than 90%, so the clamp is not swallowing real values");
    }

    /**
     * It is a <b>timed</b> buff: once its turns run out the bonus is gone.
     *
     * <p>Worth pinning because "ignore DEF" is exactly the kind of effect one is tempted to model as
     * {@code permanent} — 翡翠's text gives a turn count, and a permanent grant would be a silently stronger
     * character with nothing to show for it.
     */
    @Test
    public void theGrantExpiresWithItsTurnCount() {
        Character attacker = attackerWith(defenceIgnoreRule(0.5, 3));
        Enemy target = freshMonster();
        Battle battle = new Battle(List.of(attacker), List.of(target), new Random(0));
        battle.startBattle();
        double whileBuffed = hit(battle, attacker);

        Assertions.assertEquals(0.5, attacker.getAttribute(AttributeType.DEFENCE_IGNORE).get(), EPS,
                "precondition: the 3-turn grant is on");
        for (int turn = 0; turn < 3; turn++) {
            attacker.getBuffManager().beforeMove();
            attacker.getBuffManager().afterMove();
        }

        Assertions.assertEquals(0, attacker.getAttribute(AttributeType.DEFENCE_IGNORE).get(), EPS,
                "after three of its owner's turns the grant is gone");
        Assertions.assertTrue(hit(battle, attacker) < whileBuffed,
                "so the same hit is smaller again");
    }

    // ==================================================================
    // 2. The shipped content
    // ==================================================================

    /**
     * 翡翠's Eidolon 4 is really authored, gated on the rank, and states the numbers from her text.
     *
     * <p>Read back through the loader with a context that satisfies every gate, so a wrong attribute, a wrong
     * percentage or a lost {@code min_eidolon} fails here instead of in a battle nobody measures.
     */
    @Test
    public void herEidolonFourIsAuthoredWithItsOwnNumbers() {
        Character jade = jadeAt(4);
        List<TriggerTable.CompiledRule> rules = jade.getTriggerTable()
                .matching(TriggerEvent.ULT_CAST, new TriggerTable.TriggerContext(jade, jade, null, 0, 0));

        Assertions.assertEquals(1, rules.size(), "her table has exactly the one rule");
        Assertions.assertEquals(4, rules.getFirst().minEidolon(),
                "it IS an Eidolon 4 ability, so the gate sits next to the mechanic");

        EffectSpec effect = rules.getFirst().effects().getFirst();
        Assertions.assertEquals("MODIFY_ATTR", effect.getOp());
        Assertions.assertEquals("DEFENCE_IGNORE", effect.getAttribute());
        Assertions.assertEquals(0.12, effect.getPercent(), EPS, "param #1 is 0.12");
        Assertions.assertEquals(3, effect.getTurns(), "param #2 is 3 turns");
        Assertions.assertNull(effect.getTarget(),
                "the effect is about the damage SHE deals, so it is a buff on herself (absent means self)");
    }

    /** At rank four the Ultimate grants it — through the event, not by reaching into the attribute. */
    @Test
    public void atRankFourTheUltimateGrantsIt() {
        Character jade = jadeAt(4);
        Battle battle = new Battle(List.of(jade), List.of(freshMonster()), new Random(0));
        battle.startBattle();

        battle.fireTriggers(TriggerEvent.ULT_CAST, jade, null, 0, 0);

        Assertions.assertEquals(0.12, jade.getAttribute(AttributeType.DEFENCE_IGNORE).get(), EPS);
    }

    /** Below the rank the rule is silent, which is what makes it an Eidolon ability. */
    @Test
    public void belowRankFourNothingIsGranted() {
        Character jade = jadeAt(3);
        Battle battle = new Battle(List.of(jade), List.of(freshMonster()), new Random(0));
        battle.startBattle();

        battle.fireTriggers(TriggerEvent.ULT_CAST, jade, null, 0, 0);

        Assertions.assertEquals(0, jade.getAttribute(AttributeType.DEFENCE_IGNORE).get(), EPS,
                "at Eidolon 3 the ability does not exist yet");
    }

    /** Someone else's Ultimate does not grant it. */
    @Test
    public void anAllyUltimateDoesNotGrantIt() {
        Character jade = jadeAt(4);
        Character ally = CharacterFactory.create(ATTACKER, LEVEL);
        Battle battle = new Battle(List.of(jade, ally), List.of(freshMonster()), new Random(0));
        battle.startBattle();

        battle.fireTriggers(TriggerEvent.ULT_CAST, ally, null, 0, 0);

        Assertions.assertEquals(0, jade.getAttribute(AttributeType.DEFENCE_IGNORE).get(), EPS,
                "the text says 施放终结技时 -- HER ultimate, so `actor == self` is doing its job");
    }

    /** Her file is found by the ordinary per-character loader (it is data, not a test fixture). */
    @Test
    public void herFileIsTheOneTheLoaderReads() {
        Assertions.assertFalse(TriggerTables.of(JADE).isEmpty(),
                "1314.json must be on the classpath");
        Assertions.assertEquals(1, TriggerTables.of(JADE).ruleCount(TriggerEvent.ULT_CAST));
    }

    // ==================================================================
    // Fixture
    // ==================================================================

    /**
     * The damage one identical hit settles for a character whose battle-start rule grants
     * {@code DEFENCE_IGNORE percent}, or for one whose rule never fires when {@code percent} is null.
     *
     * <p>⚠ A <b>fresh</b> attacker every call, never a reused one: {@code startBattle()} re-fires the
     * battle-start rules, so measuring twice with the same character would stack the grant and compare a
     * number against itself plus one more stack.
     */
    private static double damageWithGrant(Double percent) {
        return damageWithGrants(1, percent == null ? new Double[0] : new Double[]{percent});
    }

    /** The same, with {@code rules} rules each granting every percent in {@code percents}. */
    private static double damageWithGrants(int maxStacks, Double... percents) {
        Character attacker = percents.length == 0
                ? attackerWith(neverFiringRule())
                : attackerWith(java.util.Arrays.stream(percents)
                        .map(p -> defenceIgnoreRule(p, null, maxStacks))
                        .toArray(TriggerSpec[]::new));
        Enemy target = freshMonster();
        Battle battle = new Battle(List.of(attacker), List.of(target), new Random(0));
        battle.startBattle();
        return hit(battle, attacker);
    }

    /**
     * One hit settled through the pipeline's single entry point, and the damage it really had effect with.
     *
     * <p>Uses {@code applyDamage}'s return value rather than a before/after HP diff: the return value is
     * defined as "shield absorbed + HP really lost", which is the same thing for an unshielded monster and
     * the quantity the engine itself calls settled damage.
     */
    private static double hit(Battle battle, Character attacker) {
        Enemy target = battle.enemyUnits().getFirst();
        return battle.applyDamage(target, new Damage(attacker, target, DamageElement.PHYSICAL,
                DamageType.NORMAL, 10_000));
    }

    private static Character jadeAt(int eidolonRank) {
        return CharacterFactory.create(JADE, LEVEL, true, null, null, eidolonRank);
    }

    private static Character attackerWith(TriggerSpec... specs) {
        Character attacker = CharacterFactory.create(ATTACKER, LEVEL);
        attacker.setTriggerTable(new TriggerTable(ATTACKER, List.of(specs)));
        return attacker;
    }

    /**
     * {@code BATTLE_START} -> grant {@code DEFENCE_IGNORE}.
     *
     * <p>A null turn count means "for the rest of the battle": the loader requires exactly one of
     * {@code turns} / {@code permanent}, and there is no default — a rule that said neither would be a
     * permanent buff by accident, which is the sort of silent-forever effect the validation exists to stop.
     */
    private static TriggerSpec defenceIgnoreRule(double percent, Integer turns) {
        return defenceIgnoreRule(percent, turns, 1);
    }

    private static TriggerSpec defenceIgnoreRule(double percent, Integer turns, int maxStacks) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "MODIFY_ATTR");
        TriggerSpecs.set(effect, "attribute", "DEFENCE_IGNORE");
        TriggerSpecs.set(effect, "percent", percent);
        TriggerSpecs.set(effect, "turns", turns);
        TriggerSpecs.set(effect, "maxStacks", maxStacks);
        if (turns == null) {
            TriggerSpecs.set(effect, "permanent", true);
        }
        return TriggerSpecs.rule("BATTLE_START", null, effect);
    }

    /** A rule with the same shape that subscribes to an event this fixture never fires. */
    private static TriggerSpec neverFiringRule() {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "MODIFY_ATTR");
        TriggerSpecs.set(effect, "attribute", "DEFENCE_IGNORE");
        TriggerSpecs.set(effect, "percent", 0.2);
        TriggerSpecs.set(effect, "turns", 2);
        return TriggerSpecs.rule("KILL", null, effect);
    }

    /** A fresh instance every time: HP is state, and two hits must start from the same point. */
    private static Enemy freshMonster() {
        return EnemyFactory.create(MONSTER, MONSTER_LEVEL, 1);
    }
}
