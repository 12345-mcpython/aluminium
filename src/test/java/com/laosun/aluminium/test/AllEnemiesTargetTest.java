package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.TriggerInterpreter;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * {@code "target": "all_enemies"} — the first selector that reaches the <b>other</b> side as a group.
 *
 * <p><b>Why it had to exist.</b> Every group selector until now read {@code Battle.allies}:
 * {@code all_allies} / {@code party} / {@code other_allies} are the party, and {@code target} / {@code attacker}
 * are single units. So 「对敌方全体」 — a phrase in a large part of the corpus — had no spelling at all, and the only
 * way to write it was to hit one enemy, which is a different mechanic. Its first reader is 姬子's Talent
 * (「对敌方全体目标造成等同于姬子140%攻击力的火属性伤害」, {@code HimekoChargeTest}); the same selector is what
 * 云璃's ultimate needs for 「使敌方全体陷入嘲讽状态」.
 *
 * <p>⚠ The failure this guards against is the mirror image: a group selector that read the wrong list would apply
 * 「敌方全体」 to <b>our own team</b>. The battle here has two enemies and our side at full HP for exactly that
 * reason, and the assertion is on both.
 */
public class AllEnemiesTargetTest {
    private static final double EPS = 1e-6;

    /** 姬子: a real character with a damaging Talent (AoEAttack, Fire), which is what {@code DAMAGE} reads. */
    private static final int OWNER = 1003;
    private static final int LEVEL = 80;
    private static final int ICE_EDGE = 1002011;

    /** One effect targeting the whole enemy camp hits every enemy and nobody on our side. */
    @Test
    public void allEnemiesReachesEveryEnemyAndNobodyOnOurSide() {
        Fixture fixture = new Fixture();

        double firstBefore = fixture.first.getCurrentHp();
        double secondBefore = fixture.second.getCurrentHp();
        double ourSideBefore = ourSideHp(fixture.battle);

        Assertions.assertEquals(1, fixture.fire(), "her rule fires once");

        Assertions.assertTrue(fixture.first.getCurrentHp() < firstBefore, "the first enemy was hit");
        Assertions.assertTrue(fixture.second.getCurrentHp() < secondBefore, "and so was the second");
        Assertions.assertEquals(ourSideBefore, ourSideHp(fixture.battle), EPS,
                "「敌方全体」 is the other camp -- our side must not be touched");
    }

    /**
     * Two enemies take the <b>same</b> instance, because 「全体」 is one effect that reaches each of them.
     *
     * <p>Identical monsters in identical state, so equal damage is the observable meaning of "the selector is a
     * list and the op settles one instance per victim" rather than "the first one is the target". ⚠ Crit is switched
     * off for the same reason: with a crit rate above 0 the two instances draw different numbers and the equality
     * would be about luck rather than about the selector.
     */
    @Test
    public void everyEnemyTakesTheSameInstance() {
        Fixture fixture = new Fixture();
        double firstBefore = fixture.first.getCurrentHp();
        double secondBefore = fixture.second.getCurrentHp();

        fixture.fire();

        Assertions.assertEquals(firstBefore - fixture.first.getCurrentHp(),
                secondBefore - fixture.second.getCurrentHp(), EPS,
                "one instance per victim, all with the same numbers");
    }

    /** The selector needs a battlefield: a hand-built context has none, and that fails loudly. */
    @Test
    public void allEnemiesWithoutABattleFailsLoudly() {
        Character owner = characterWith(damageRule(true));
        TriggerTable.TriggerContext ctx = new TriggerTable.TriggerContext(owner, owner, null, 0, 0);
        List<TriggerTable.CompiledRule> rules = owner.getTriggerTable().matching(TriggerEvent.ALLY_ATTACK, ctx);
        Assertions.assertEquals(1, rules.size(), "precondition: the rule matches");

        IllegalStateException rejected = Assertions.assertThrows(IllegalStateException.class,
                () -> TriggerInterpreter.apply(null, rules.getFirst(), ctx));
        Assertions.assertTrue(rejected.getMessage().contains("no battle"),
                "the message must say why: " + rejected.getMessage());
    }

    /**
     * A group selector is still refused by an op that resolves <b>one</b> unit.
     *
     * <p>{@code EXTRA_TURN} takes a single actor; reaching several through it would have to pick one, silently. The
     * closed set of selectors is not the same thing as "every op accepts every selector" — this is the other half.
     */
    @Test
    public void aSingleTargetOpRefusesTheGroupSelector() {
        EffectSpec extraTurn = new EffectSpec();
        TriggerSpecs.set(extraTurn, "op", "EXTRA_TURN");
        TriggerSpecs.set(extraTurn, "target", "all_enemies");
        Fixture fixture = new Fixture(characterWith(TriggerSpecs.rule("ALLY_ATTACK", null, extraTurn)));

        IllegalStateException rejected = Assertions.assertThrows(IllegalStateException.class, fixture::fire);
        Assertions.assertTrue(rejected.getMessage().contains("several"),
                "the message must say the op takes a list, not a selector: " + rejected.getMessage());
    }

    // ==================================================================
    // helpers
    // ==================================================================

    /** One battle with a whole enemy camp: the owner, two enemies, and a rule to fire. */
    private static final class Fixture {
        private final Character owner;
        private final Enemy first;
        private final Enemy second;
        private final Battle battle;

        /** The owner with the group-damage rule. */
        private Fixture() {
            this(characterWith(damageRule(true)));
        }

        private Fixture(Character owner) {
            this.owner = owner;
            this.first = EnemyFactory.create(ICE_EDGE, 90, 1);
            this.second = EnemyFactory.create(ICE_EDGE, 90, 1);
            this.battle = new Battle(List.of(owner), List.of(first, second), new Random(0));
            battle.startBattle();
        }

        private int fire() {
            return battle.fireTriggers(TriggerEvent.ALLY_ATTACK, owner, null, 1, 0);
        }
    }

    /**
     * The owner with a hand-built table, and with crit switched off so a damage number is a fact about the rule
     * rather than about a die roll.
     */
    private static Character characterWith(TriggerSpec spec) {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        owner.setAttribute(AttributeType.CRIT_CHANCE, new DoubleValue(0));
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(spec)));
        return owner;
    }

    /**
     * One {@code DAMAGE} effect out of the owner's own Talent row.
     *
     * @param allEnemies {@code true} for the group selector, {@code false} to leave the target out (the owner only)
     */
    private static TriggerSpec damageRule(boolean allEnemies) {
        return TriggerSpecs.rule("ALLY_ATTACK", null,
                TriggerSpecs.damage("TALENT", 0, null, allEnemies ? "all_enemies" : null));
    }

    private static double ourSideHp(Battle battle) {
        return battle.allies.stream().mapToDouble(CanHit::getCurrentHp).sum();
    }
}
