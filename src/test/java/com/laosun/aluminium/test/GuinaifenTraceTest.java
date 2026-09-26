package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.buff.DotBuff;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1210 桂乃芬 (Guinaifen): the first character whose file is <b>content</b> for the damage-instance vocabulary.
 *
 * <p>Her 逾锋 is 「对陷入灼烧状态的敌方目标造成的伤害提高20%」 — a bonus that depends on the <i>target's state
 * at the moment of the hit</i>, which is neither a timed buff nor something {@code ALLY_ATTACK} could drive
 * (that event fires after the attack is settled). It is the first shipped rule that uses
 * {@code DEALING_DAMAGE} + {@code BOOST_DAMAGE}, and the first that reads a state name resolving to a DoT
 * ({@code 灼烧} = a Fire {@code DotBuff}) rather than to a {@code StateBuff}.
 *
 * <p>Her 投狭 (advance 25% at battle start) is in the same file, so this also keeps pinning "one character,
 * several mechanics, one table" — the property Robin's file established.
 *
 * <p>Her third trace (缘竿: "a Basic ATK has an 80% base chance to burn") is <b>not</b> here: it needs a chance
 * roll on a trigger rule and an op that applies a DoT, neither of which exists. Two of three is the honest
 * state, and the file says nothing about the third.
 */
public class GuinaifenTraceTest {
    private static final int GUINAIFEN = 1210;
    /** Tingyun: a character with no rule file of her own, used as the "not hers" side. */
    private static final int ALLY = 1202;
    private static final int LEVEL = 80;
    private static final double BASE = 1000;
    private static final double TRACE_BOOST = 0.2;
    private static final double EPS = 1e-9;

    // ==================================================================
    // 1210103 逾锋: 「对陷入灼烧状态的敌方目标造成的伤害提高20%」
    // ==================================================================

    @Test
    public void herDamageIsRaisedAgainstABurningTarget() {
        Battle battle = battleWithHerRules();
        Character hero = battle.characters.getFirst();
        Enemy target = dummy();

        double plain = hit(battle, hero, target);
        burn(target, hero);
        double boosted = hit(battle, hero, target);

        Assertions.assertEquals(plain * (1 + TRACE_BOOST), boosted, EPS,
                "the trace's 20% lands only now that the target is burning");
    }

    @Test
    public void aHitAgainstATargetThatIsNotBurningGetsNothing() {
        Battle battle = battleWithHerRules();
        Character hero = battle.characters.getFirst();
        Enemy target = dummy();

        double plain = hit(battle, hero, target);
        burn(target, hero);
        Assertions.assertEquals(plain * (1 + TRACE_BOOST), hit(battle, hero, target), EPS);

        Assertions.assertTrue(target.getBuffManager().removeOneBuff(DotBuff.class), "the burn is removed");
        Assertions.assertEquals(plain, hit(battle, hero, target), EPS,
                "nothing was attached to anybody, so the very next hit is back to normal");
    }

    /** The trace is about her damage: the same burning target does not raise an <b>ally's</b> hit. */
    @Test
    public void anAllysHitOnTheSameBurningTargetIsUntouched() {
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Battle battle = newBattle(List.of(CharacterFactory.create(GUINAIFEN, LEVEL), ally));
        Character hero = battle.characters.getFirst();
        Enemy target = dummy();

        double allyPlain = hit(battle, ally, target);
        burn(target, hero);
        double allyAfter = hit(battle, ally, target);

        Assertions.assertEquals(allyPlain, allyAfter, EPS,
                "「桂乃芬对…造成的伤害提高」 is her own damage -- 'actor == self' is what keeps it that way");
    }

    // ==================================================================
    // 1210102 投狭: 「战斗开始时，桂乃芬的行动提前25%」
    // ==================================================================

    @Test
    public void herBattleStartAdvancePullsHerForwardByAQuarter() {
        double withTrace = remainingAtBattleStart(true);
        double withoutTrace = remainingAtBattleStart(false);

        Assertions.assertTrue(withoutTrace > 0, "precondition: she has a wait to shorten");
        Assertions.assertEquals(0.75 * withoutTrace, withTrace, 1e-6,
                "the same 25%-of-remaining-wait arithmetic as Robin's 华彩花腔");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /** A battle with her real, shipped table (the file is the thing under test). */
    private static Battle battleWithHerRules() {
        return newBattle(List.of(CharacterFactory.create(GUINAIFEN, LEVEL)));
    }

    /** Builds a battle from a roster, with crit and typed boosts neutralised on everybody. */
    private static Battle newBattle(List<Character> team) {
        team.forEach(GuinaifenTraceTest::neutralise);
        Battle battle = new Battle(team, List.of(dummy()), new Random(0));
        battle.startBattle();
        return battle;
    }

    /** The same character with an emptied table, for the advance comparison. */
    private static double remainingAtBattleStart(boolean keepRules) {
        Character hero = CharacterFactory.create(GUINAIFEN, LEVEL);
        if (!keepRules) {
            hero.setTriggerTable(new TriggerTable(GUINAIFEN, List.of()));
        }
        Battle battle = new Battle(List.of(hero), List.of(dummy()), new Random(0));
        battle.startBattle();
        return battle.queue.snapshot().stream()
                .filter(signal -> signal.getCanHit() == hero)
                .findFirst()
                .map(battle.queue::getTimeRemaining)
                .orElseThrow(() -> new AssertionError("she is not on the action bar"));
    }

    /**
     * Removes crit and her own typed DMG boosts, so the ratio assertions are exact — the boost under test lands
     * in the same additive zone as her fire DMG traces.
     */
    private static void neutralise(Character who) {
        who.setAttribute(AttributeType.CRIT_CHANCE, new DoubleValue(0));
        who.setAttribute(AttributeType.FIRE_DAMAGE_BOOST, new DoubleValue(0));
        who.setAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST, new DoubleValue(0));
    }

    private static double hit(Battle battle, Character attacker, Enemy target) {
        double before = target.getCurrentHp();
        battle.applyDamage(target, new Damage(attacker, target, DamageElement.FIRE, DamageType.NORMAL, BASE));
        return before - target.getCurrentHp();
    }

    private static void burn(Enemy target, Character source) {
        target.getBuffManager().addBuff(new DotBuff(source, DamageElement.FIRE, 500, 3));
    }

    private static Enemy dummy() {
        return Enemy.fromAttributes("dummy", 1_000_000, 0, 100, 100);
    }
}
