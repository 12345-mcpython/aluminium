package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Summon;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import com.laosun.aluminium.utils.RelicFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Relic set 125 「烈阳惊雷的女武神」 4-piece: 「当装备者及其忆灵为装备者及其忆灵以外的我方目标提供治疗后，使装备者获得【甘霖】，
 * 每回合最多触发1次，持续2回合。装备者持有【甘霖】时，速度提高6%，我方全体暴击伤害提高15%，该效果无法叠加。」 (param [0.06, 0.15, 2])
 *
 * <p>Every expectation is a DIFFERENCE between two otherwise-identical battles -- one with the heal, one without -- because the wearer's SPD moves
 * for reasons of its own between construction and reading (the set's own 2-piece `SpeedAddedRatio`, and traits that fire at BATTLE_START). A share
 * scales the target's BASE, so the SPD expectation is 6% of `baseValue()` (99) = 5.94.
 */
public class GentleRainTest {
    private static final int WEARER = 1413;
    private static final int ALLY = 1001;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final int RELIC_LEVEL = 15;
    private static final double EPSILON = 1e-6;

    @Test
    public void theWearersOwnHealOfAnotherAllyTriggersIt() {
        Result with = run(true, false, false);
        Result without = run(false, false, false);
        Assertions.assertEquals(0.06 * with.baseSpeed, with.speed - without.speed, EPSILON,
                "6% of the base SPD (" + with.baseSpeed + "): " + without.speed + " -> " + with.speed);
        // \u26a0 CRIT DMG is a RATIO attribute: `percent: 0.15` lands as an absolute addend (fifteen points), unlike SPD where the share scales
        // the base. Measured both ways this round; the ally's `baseValue()` for CRIT DMG reads 0.0, so a base-relative expectation is meaningless.
        Assertions.assertEquals(0.15, with.allyCrit - without.allyCrit, EPSILON,
                "fifteen points of CRIT DMG: " + without.allyCrit + " -> " + with.allyCrit);
    }

    @Test
    public void theMemoSpritesHealTriggersItToo() {
        Result with = run(true, true, false);
        Result without = run(false, true, false);
        Assertions.assertEquals(0.06 * with.baseSpeed, with.speed - without.speed, EPSILON,
                "one rule covers both healers, because `actor is_ally` includes the 忆灵: "
                        + without.speed + " -> " + with.speed);
    }

    @Test
    public void healingTheWearerThemselvesDoesNot() {
        Result with = run(true, false, true);
        Result without = run(false, false, true);
        Assertions.assertEquals(0.0, with.speed - without.speed, EPSILON,
                "「以外的我方目标」 excludes the wearer: " + without.speed + " -> " + with.speed);
    }

    @Test
    public void twiceInATurnStillLeavesOneStack() {
        Result with = run(true, false, false, true);
        Result without = run(false, false, false, false);
        Assertions.assertEquals(0.06 * with.baseSpeed, with.speed - without.speed, EPSILON,
                "per_turn: 1 and max_stacks: 1 -- 「该效果无法叠加」 stays at one stack: "
                        + without.speed + " -> " + with.speed);
    }

    private static Result run(boolean heal, boolean byMemoSprite, boolean healTheWearer) {
        return run(heal, byMemoSprite, healTheWearer, false);
    }

    private static Result run(boolean heal, boolean byMemoSprite, boolean healTheWearer, boolean twice) {
        Character wearer = CharacterFactory.create(WEARER, LEVEL, true, null, RelicFactory.suit(125, 4, RELIC_LEVEL));
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(wearer, ally), List.of(enemy), new Random(0));
        battle.startBattle();
        double baseSpeed = wearer.getAttribute(AttributeType.SPEED).baseValue();
        double baseCrit = ally.getAttribute(AttributeType.CRIT_ATTACK).baseValue();
        ally.takeDamage(ally.getCurrentHp() * 0.5);
        if (heal) {
            CanHit healer = byMemoSprite ? battle.summonMemosprite(wearer) : wearer;
            CanHit healed = healTheWearer ? wearer : ally;
            battle.heal(healer, healed, 300);
            if (twice) {
                battle.heal(healer, healed, 300);
            }
        }
        return new Result(wearer.getAttribute(AttributeType.SPEED).get(),
                ally.getAttribute(AttributeType.CRIT_ATTACK).get(), baseSpeed, baseCrit);
    }

    private record Result(double speed, double allyCrit, double baseSpeed, double baseCrit) {
    }
}
