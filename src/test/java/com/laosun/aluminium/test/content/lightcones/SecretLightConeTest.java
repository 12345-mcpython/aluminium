package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Light cone 21032 「秘密」 - 「战斗开始时以及当装备者回合开始时，随机生效 1 个效果。该效果生效时，<b>替换上次</b>的效果且<b>本次不会与
 * 上次重复</b>。效果包含：我方全体攻击力 +10%；暴击伤害 +12%；能量恢复效率 +6%。」
 *
 * <p>⚠ The judge reads the THREE effects and asks two things of every firing: <b>exactly one</b> is up (a pick that
 * accumulated would show two, and one that removed nothing would leave a stale value), and it is <b>never the same one
 * twice</b>. A judge that only asked "is an effect up" would pass on a rule that never changed its choice.
 *
 * <p>⚠ Both readings are taken on an <b>ally without a light cone</b>, and its baseline is read <b>in the same battle,
 * before the battle-start pick</b>. Two reasons, both measured: the wearer's own ATTACK carries the cone's flat stats
 * (so its deltas are not the modifier's), and a ratio attribute's {@code baseValue()} is not its unmodified value
 * (measured: an unequipped character's CRIT_ATTACK reads 0.5 with a base of 0).
 *
 * <p>⚠ The two triggers cannot be one rule: {@code BATTLE_START} is fired with no actor, so the wearer's-turn condition
 * cannot also catch the battle's start. They share the pick memory by NAME instead - and the FIRST turn-start pick must
 * already differ from the battle-start one, which is the assertion that fails if the two rules keep separate memories.
 */
public class SecretLightConeTest {
    private static final int CONE = 21032;
    private static final int WEARER = 1210;
    private static final int ALLY = 1204;
    private static final int OTHER_ALLY = 1312;
    private static final int MONSTER = 1002011;
    private static final int LEVEL = 80;
    /** The three effects, as the document states them: name, attribute, magnitude. */
    private static final String[] NAMES = {"秘密-攻击力", "秘密-暴击伤害", "秘密-能量恢复"};
    private static final AttributeType[] ATTRS = {
            AttributeType.ATTACK, AttributeType.CRIT_ATTACK, AttributeType.ENERGY_REGENERATION_RATE};
    private static final double[] SHARES = {0.10, 0.12, 0.06};

    /** Over several seeds: one effect at battle start, and every following pick different from the last. */
    @Test
    public void theEffectIsReplacedAndNeverRepeatsItself() {
        boolean sawAttack = false;
        boolean sawCrit = false;
        boolean sawEnergy = false;
        for (int seed = 0; seed < 12; seed++) {
            Fixture f = fixture(seed, true);
            f.start();
            int first = activeEffect(f);
            Assertions.assertTrue(first >= 0, "seed " + seed + ": the battle start must pick exactly one effect");
            sawAttack |= first == 0;
            sawCrit |= first == 1;
            sawEnergy |= first == 2;

            int previous = first;
            for (int turn = 0; turn < 3; turn++) {
                f.wearerTurn();
                int now = activeEffect(f);
                Assertions.assertTrue(now >= 0,
                        "seed " + seed + " turn " + turn + ": the turn start must pick exactly one effect - two would "
                                + "mean the old one was never replaced, none would mean it was removed and not replaced");
                Assertions.assertNotEquals(previous, now,
                        "seed " + seed + " turn " + turn + ": 「本次不会与上次重复」 - the pick must differ from the last one "
                                + "(and from the battle-start one on the FIRST turn)");
                previous = now;
            }
        }
        System.out.println("[21032] over 12 seeds: attack=" + sawAttack + " crit=" + sawCrit + " energy=" + sawEnergy);
        Assertions.assertTrue(sawAttack && sawCrit && sawEnergy,
                "and the first pick is uniform over all three - a pick that never lands on one of them is not a choice");
    }

    /** An unequipped character has none of it: the rules come from the cone. */
    @Test
    public void withoutTheConeNothingIsRaised() {
        Fixture f = fixture(0, false);
        f.start();
        Assertions.assertEquals(-1, activeEffect(f), "no cone, no effect");
        f.wearerTurn();
        Assertions.assertEquals(-1, activeEffect(f), "and none after a turn either");
    }

    /** 「我方全体」: two allies without cones carry the same effect, and it is the same ONE of the three. */
    @Test
    public void theEffectReachesTheWholeParty() {
        Fixture f = fixture(3, true);
        f.start();
        int active = activeEffect(f);
        Assertions.assertTrue(active >= 0, "precondition: one effect is up");
        Assertions.assertEquals(active, activeEffect(f, f.otherAlly, f.otherBaseline),
                "「我方全体」 is the whole party, and the same one of the three");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /** One battle: the wearer (with or without the cone), two plain allies, and the allies' pre-battle readings. */
    private record Fixture(Battle battle, Character wearer, Character ally, double[] baseline,
                           Character otherAlly, double[] otherBaseline) {
        void start() {
            battle.startBattle();
            battle.processRequests();
        }

        void wearerTurn() {
            battle.fireTriggers(TriggerEvent.TURN_START, wearer, wearer, 0, 0);
            battle.processRequests();
        }
    }

    private static Fixture fixture(int seed, boolean equipped) {
        Weapon cone = equipped ? Weapon.build(CONE, LEVEL) : null;
        Character wearer = CharacterFactory.create(WEARER, LEVEL, true, cone);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Character otherAlly = CharacterFactory.create(OTHER_ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(wearer, ally, otherAlly), List.of(enemy), new Random(seed));
        // Read the baseline BEFORE the battle starts: the battle-start pick is part of startBattle, and by then an effect
        // is already up.
        return new Fixture(battle, wearer, ally, readings(ally), otherAlly, readings(otherAlly));
    }

    private static double[] readings(Character unit) {
        double[] values = new double[ATTRS.length];
        for (int i = 0; i < ATTRS.length; i++) {
            values[i] = unit.getAttribute(ATTRS[i]).get();
        }
        return values;
    }

    private static int activeEffect(Fixture f) {
        return activeEffect(f, f.ally, f.baseline);
    }

    /**
     * Which of the three is up on this unit, or {@code -1} for none - and it refuses TWO, which is the state a missing
     * "replace" leaves behind. Note: The name and the number are BOTH read, so "the state is attached" and "it is worth
     * what the document says" are separate assertions.
     */
    private static int activeEffect(Fixture f, Character unit, double[] baseline) {
        int active = -1;
        int raised = 0;
        for (int i = 0; i < ATTRS.length; i++) {
            double delta = unit.getAttribute(ATTRS[i]).get() - baseline[i];
            boolean named = unit.getBuffManager().hasState(NAMES[i]);
            Assertions.assertEquals(delta > 1e-9, named,
                    NAMES[i] + ": the modifier and its magnitude must agree - delta=" + delta + " named=" + named);
            if (delta > 1e-9) {
                active = i;
                raised++;
            }
        }
        Assertions.assertTrue(raised <= 1, unit.getName() + " has " + raised + " of the three up at once; 「替换上次」 allows one");
        if (active >= 0) {
            double expected = active == 0
                    ? SHARES[0] * unit.getAttribute(AttributeType.ATTACK).baseValue()
                    : SHARES[active];
            Assertions.assertEquals(expected, unit.getAttribute(ATTRS[active]).get() - baseline[active], 1e-9,
                    "the magnitude is the document's: 10% of ATK / 12% crit damage / 6% energy regen");
        }
        return active;
    }
}
