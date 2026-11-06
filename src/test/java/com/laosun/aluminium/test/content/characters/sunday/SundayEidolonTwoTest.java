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
 * 1313 星期日's 星魂 2:「首次施放终结技后恢复 2 个战技点。【蒙福者】造成的伤害提高 30%」.
 *
 * <p>Two halves: the skill point is a ONE-TIME payment (a marker state spends it), and the damage share rides the Beatified
 * state it is granted with.
 */
public class SundayEidolonTwoTest {
    private static final int SUNDAY = 1313;
    private static final int ALLY = 1204;
    private static final int LEVEL = 80;
    private static final double EPS = 1e-9;

    @Test
    public void theFirstUltimatePaysTwoSkillPointsOnce() {
        Character sunday = CharacterFactory.create(SUNDAY, LEVEL, false, null, null, 2);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Battle battle = new Battle(List.of(sunday, ally), List.of(BattleFixture.dummy()), BattleFixture.seeded());
        battle.startBattle();
        int pool = battle.getSkillPoints();

        battle.castImmediate(sunday.getSkills().get(SkillType.ULTRA), sunday, List.of(ally));
        battle.processRequests();
        int afterFirst = battle.getSkillPoints();
        Assertions.assertEquals(pool + 2, afterFirst, "「首次施放终结技后恢复 2 个战技点」");

        sunday.setCurrentEnergy(sunday.getMaxEnergy());
        battle.castImmediate(sunday.getSkills().get(SkillType.ULTRA), sunday, List.of(ally));
        battle.processRequests();
        Assertions.assertEquals(afterFirst, battle.getSkillPoints(),
                "…and the second Ultimate pays nothing: the marker was spent");
    }

    @Test
    public void theBeatifiedDealThirtyPercentMore() {
        Assertions.assertEquals(0.3, shareAt(2), EPS, "「【蒙福者】造成的伤害提高 30%」");
        Assertions.assertEquals(0.3, shareAt(3), EPS, "…and it does not stack with itself when the Eidolon is higher");
        Assertions.assertEquals(0.0, shareAt(1), "before 星魂 2 there is no such share");
    }

    /** The damage share the Beatified ally carries after his Ultimate, at the given Eidolon rank. */
    private static double shareAt(int eidolon) {
        Character sunday = CharacterFactory.create(SUNDAY, LEVEL, false, null, null, eidolon);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Battle battle = new Battle(List.of(sunday, ally), List.of(BattleFixture.dummy()), BattleFixture.seeded());
        battle.startBattle();
        battle.castImmediate(sunday.getSkills().get(SkillType.ULTRA), sunday, List.of(ally));
        battle.processRequests();
        return ally.getBuffManager().allBuffsOf(com.laosun.aluminium.buff.StatModifierBuff.class).stream()
                .filter(buff -> buff.getAttribute() == AttributeType.ALL_DAMAGE_TYPE_BOOST)
                .mapToDouble(com.laosun.aluminium.buff.StatModifierBuff::getValue).sum();
    }
}
