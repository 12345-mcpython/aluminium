package com.laosun.aluminium.test.content.characters.danhengimbibitor;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1213 丹恒•饮月's ultimate: 「使自身获得 2 个【逆鳞】。【逆鳞】最多持有 3 个」.
 *
 * <p>One application grants TWO layers ({@code ADD_STACK.amount} is the count - the old note that said otherwise was a
 * measurement from before the op read it), and the cap is the sentence's own 3.
 */
public class DanHengUltScalesTest {

    private static final int LEVEL = 80;
    private static final int DAN_HENG = 1213;
    private static final String SCALE = "逆鳞";

    @Test
    public void theUltimateGrantsTwoScalesUpToThree() {
        Fixture f = new Fixture();
        f.ultimate();
        Assertions.assertEquals(2, f.danHeng.getBuffManager().stacksOf(SCALE), "「获得 2 个【逆鳞】」");

        f.ultimate();
        Assertions.assertEquals(3, f.danHeng.getBuffManager().stacksOf(SCALE), "「最多持有 3 个」 - the cap holds");

        f.ultimate();
        Assertions.assertEquals(3, f.danHeng.getBuffManager().stacksOf(SCALE), "and it never goes past it");
    }

    private static final class Fixture {
        private final Character danHeng;
        private final Battle battle;

        private Fixture() {
            danHeng = CharacterFactory.create(DAN_HENG, LEVEL);
            battle = new Battle(new ArrayList<>(List.of(danHeng, CharacterFactory.create(1001, LEVEL))),
                    new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.seeded());
            battle.startBattle();
        }

        private void ultimate() {
            battle.castImmediate(danHeng.getSkills().get(SkillType.ULTRA), danHeng,
                    List.of(battle.enemies().getFirst()));
        }
    }
}
