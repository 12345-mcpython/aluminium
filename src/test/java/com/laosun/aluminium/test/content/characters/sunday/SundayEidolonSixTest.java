package com.laosun.aluminium.test.content.characters.sunday;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1313 星期日's 星魂 6, the half that is writable:「天赋的暴击率提高效果可以叠加，最多叠加 3 层，并使天赋的持续时间增加 1 回合」.
 *
 * <p>Below 星魂 6 the talent REPLACES its modifier (one cast, one benefit); from 星魂 6 on it STACKS to three and lasts 4 turns
 * instead of 3. The third sentence of the same Eidolon (overflow crit Rate turned into CRIT DMG) is registered - it needs a
 * modifier derived from another attribute at settlement time, which the engine does not have.
 */
public class SundayEidolonSixTest {
    private static final int SUNDAY = 1313;
    private static final int ALLY = 1204;
    private static final int LEVEL = 80;

    @Test
    public void theTalentStacksThreeTimesFromTheSixthEidolon() {
        Assertions.assertEquals(1, critRateStacks(5, 2), "below 星魂 6 the second cast replaces the first");
        Assertions.assertEquals(2, critRateStacks(6, 2), "「可以叠加」 — two casts, two layers");
        Assertions.assertEquals(3, critRateStacks(6, 4), "「最多叠加 3 层」");
        Assertions.assertEquals(3, critRateStacks(6, 3), "and three casts reach the cap exactly");
    }

    @Test
    public void theTalentLastsFourTurnsFromTheSixthEidolon() {
        Assertions.assertEquals(3, critRateDuration(5), "before 星魂 6: 「持续 3 回合」");
        Assertions.assertEquals(4, critRateDuration(6), "「并使天赋的持续时间增加 1 回合」");
    }

    /** How many CRIT_RATE modifiers the ally carries after {@code casts} Skill casts, at that Eidolon rank. */
    private static long critRateStacks(int eidolon, int casts) {
        Battle battle = scene(eidolon);
        Character sunday = battle.characters().get(0);
        Character ally = battle.characters().get(1);
        for (int i = 0; i < casts; i++) {
            battle.castImmediate(sunday.getSkills().get(SkillType.SKILL), sunday, List.of(ally));
            battle.processRequests();
        }
        return ally.getBuffManager().allBuffsOf(com.laosun.aluminium.buff.StatModifierBuff.class).stream()
                .filter(buff -> buff.getAttribute() == AttributeType.CRIT_CHANCE)
                .count();
    }

    /** The duration of the CRIT_RATE modifier after one Skill cast, at that Eidolon rank. */
    private static int critRateDuration(int eidolon) {
        Battle battle = scene(eidolon);
        Character sunday = battle.characters().get(0);
        Character ally = battle.characters().get(1);
        battle.castImmediate(sunday.getSkills().get(SkillType.SKILL), sunday, List.of(ally));
        battle.processRequests();
        return ally.getBuffManager().allBuffsOf(com.laosun.aluminium.buff.StatModifierBuff.class).stream()
                .filter(buff -> buff.getAttribute() == AttributeType.CRIT_CHANCE)
                .mapToInt(com.laosun.aluminium.buff.AbstractBuff::duration)
                .max().orElse(0);
    }

    private static Battle scene(int eidolon) {
        Character sunday = CharacterFactory.create(SUNDAY, LEVEL, false, null, null, eidolon);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Battle battle = new Battle(List.of(sunday, ally), List.of(BattleFixture.dummy()), BattleFixture.seeded());
        battle.startBattle();
        return battle;
    }

    /**
     * The third sentence:「当目标暴击率超过 100%，每溢出 1% 暴击率提高 2% 暴击伤害」.
     *
     * <p>Measured with the ally crit rate forced over 100%: the CRIT DMG modifier must equal twice the overflow, and with the
     * rate below 100% it must be zero - which is the `max(..., 0)` in the expression doing its job.
     */
    @Test
    public void theOverflowCritRateBecomesCritDamage() {
        // The exact figure is computed inside the helper from the rate the ally actually holds (a snapshot), so these two
        // assertions only say WHICH WAY the clause goes.
        Assertions.assertTrue(overflowCritDamage(1.04) > 0, "over 100% the overflow becomes CRIT DMG");
        Assertions.assertEquals(0.0, overflowCritDamage(0.6), 1e-6, "below 100% nothing is converted");
    }

    /** The CRIT DMG the talent adds when the ally crit rate is pushed to {@code critRate} first. */
    private static double overflowCritDamage(double critRate) {
        Battle battle = scene(6);
        Character sunday = battle.characters().get(0);
        Character ally = battle.characters().get(1);
        // "pure" is the value in the attribute's own units: a percent modifier is a share of the BASE and does not reliably
        // cross 100% (measured: the first version left the ally near 10%, and the clause correctly paid nothing).
        ally.getBuffManager().addBuff(com.laosun.aluminium.buff.StatModifierBuff.of(
                AttributeType.CRIT_CHANCE, "pure", critRate - ally.getAttribute(AttributeType.CRIT_CHANCE).get(),
                "buff", 3, false));
        battle.processRequests();
        double before = ally.getAttribute(AttributeType.CRIT_ATTACK).get();
        battle.castImmediate(sunday.getSkills().get(SkillType.SKILL), sunday, List.of(ally));
        battle.processRequests();
        // The clause is a SNAPSHOT of the crit rate at the moment the talent lands (which already includes the talent own
        // +20%), so the expectation is computed from the rate the ally actually holds afterwards.
        double critAfter = ally.getAttribute(AttributeType.CRIT_CHANCE).get();
        double expected = 2 * Math.max(0, critAfter - 1);
        double measured = ally.getAttribute(AttributeType.CRIT_ATTACK).get() - before;
        Assertions.assertEquals(expected, measured, 1e-6, "2 x the overflow (" + critAfter + ")");
        return measured;
    }
}
