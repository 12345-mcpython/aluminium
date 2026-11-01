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
 * 1111 卢卡's ultimate: 「获得 2 层【斗志】」 - ONE application that grants <b>two</b> layers.
 *
 * <p>The file's note says `amount` grants one mark ("measured"), i.e. the clause is registered as unwritable. That reading
 * is stale: {@code ADD_STACK} reads {@code amount} as the number of layers (its own comment cites 1314's 「立即获得 15
 * 层【当品】」). This test pins the truth: the ultimate adds two, not one, and the talent's cap of four still holds.
 */
public class LukaUltStacksTest {

    private static final int LEVEL = 80;
    private static final int LUKA = 1111;
    private static final String FIGHTING_SPIRIT = "斗志";

    /**
     * One ultimate grants two layers on top of the talent's starting one, and the fourth layer is the cap.
     */
    @Test
    public void theUltimateGrantsTwoLayersAndTheCapHolds() {
        Fixture f = new Fixture();
        int before = f.luka.getBuffManager().stacksOf(FIGHTING_SPIRIT);
        f.castUltimate();
        Assertions.assertEquals(before + 2, f.luka.getBuffManager().stacksOf(FIGHTING_SPIRIT),
                "「获得 2 层【斗志】」 - one application, two layers");

        f.castUltimate();
        Assertions.assertEquals(4, f.luka.getBuffManager().stacksOf(FIGHTING_SPIRIT),
                "「最多持有 4 层」 - the second ultimate stops at the cap");
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

        private void castUltimate() {
            battle.castImmediate(luka.getSkills().get(SkillType.ULTRA), luka, List.of(enemy));
        }
    }
}
