package com.laosun.aluminium.test.content.characters.luka;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1111 卢卡's enhanced basic attack, both directions: 「当持有的【斗志】大于等于 2 层时，普攻【直冲拳】强化为【直冲碎天拳】」 -
 * which also says what happens below two layers, so the swap must be removable.
 *
 * <p>Install and removal both hang on {@code ATTACK_FINISHED}: the layers are gained <i>after</i> the hit settles, so an
 * {@code ALLY_ATTACK} rule would turn the very attack that crosses two layers into the enhanced one (measured before the
 * move).
 */
public class LukaEnhancedBasicCycleTest {

    private static final int LEVEL = 80;
    private static final int LUKA = 1111;
    private static final String FIGHTING_SPIRIT = "斗志";

    /**
     * The row changes one attack AFTER the layers move, and changes back when the enhanced attack spends them.
     */
    @Test
    public void theRowFollowsTheLayers() {
        Fixture f = new Fixture();
        Assertions.assertEquals(1, f.stacks(), "the talent starts with one layer");

        f.castBasic();                                    // 1 -> 2 layers, swap installed after the hit
        Assertions.assertEquals(2, f.stacks(), "the basic grants its layer");
        Assertions.assertEquals(8, f.commonSlot(), "「≥ 2 层时强化」 - the swap is in place for the NEXT attack");

        f.castBasic();                                    // the enhanced row: spends two layers, gains one
        Assertions.assertEquals(1, f.stacks(), "「强化普攻消耗 2 层」 - 2 + 1 - 2 = 1");
        Assertions.assertEquals(1, f.commonSlot(), "「< 2 层」 means the ordinary attack is back");

        f.castBasic();                                    // ordinary again: 1 -> 2
        Assertions.assertEquals(2, f.stacks());
        Assertions.assertEquals(8, f.commonSlot(), "the cycle repeats");
    }

    // ==================================================================

    private static final class Fixture {
        private final Character luka;
        private final Enemy enemy = BattleFixture.monster();
        private final Battle battle;

        private Fixture() {
            luka = CharacterFactory.create(LUKA, LEVEL);
            battle = new Battle(new ArrayList<>(List.of(luka, CharacterFactory.create(1001, LEVEL))),
                    new ArrayList<>(List.of(enemy)), BattleFixture.seeded());
            battle.startBattle();
        }

        private int stacks() {
            return luka.getBuffManager().stacksOf(FIGHTING_SPIRIT);
        }

        private int commonSlot() {
            return luka.getSkills().get(SkillType.COMMON).getSkillSlot();
        }

        private void castBasic() {
            battle.castImmediate(luka.getSkills().get(SkillType.COMMON), luka, List.of(enemy));
        }
    }
}
