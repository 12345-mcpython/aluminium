package com.laosun.aluminium.test.content.characters.sunday;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1313 星期日's trace 主日渴慕:「施放终结技时，若为目标恢复的能量不足 40 点，恢复的能量提高至 40 点」.
 *
 * <p>A floor stated in POINTS, which is why the engine grew a flat spelling for {@code RAISE_ENERGY_TO} (it only took a
 * share of the maximum before). The ally here has a small enough bar that 20% of it is under 40, so the floor is the only
 * way the reading can reach 40.
 */
public class SundayLentenGiftTest {
    private static final int SUNDAY = 1313;
    private static final int ALLY = 1204;
    private static final int LEVEL = 80;

    @Test
    public void theLentenGiftLiftsTheRestoreToFortyPoints() {
        Character sunday = CharacterFactory.create(SUNDAY, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Battle battle = new Battle(List.of(sunday, ally), List.of(BattleFixture.dummy()), BattleFixture.seeded());
        battle.startBattle();
        ally.setCurrentEnergy(0);
        Assertions.assertTrue(ally.getMaxEnergy() * 0.2 < 40,
                "precondition: 20 percent of the ally maximum is below the floor (" + ally.getMaxEnergy() + ")");

        battle.castImmediate(sunday.getSkills().get(SkillType.ULTRA), sunday, List.of(ally));
        battle.processRequests();

        Assertions.assertEquals(40.0, ally.getCurrentEnergy(), 1e-9,
                "「恢复的能量提高至 40 点」 — 20 percent would have been less than 40");
    }
}
