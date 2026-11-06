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
}
