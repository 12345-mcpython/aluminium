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
 * Gap witness for 1111 卢卡's enhanced basic attack. The swap <b>is</b> installed (the {@code from_skill_id == 8} rules
 * fire, and the bleed tick adds real damage), but two parts of the sentence are still wrong, and this test pins today's
 * behaviour so a fix turns it red:
 *
 * <ol>
 *   <li><b>Timing.</b> {@code ALLY_ATTACK} fires <i>before</i> the hit settles, while 「获得 1 层【斗志】」 happens
 *       <i>after</i> the cast - so the very attack that crosses two layers resolves as the enhanced row, although the
 *       sentence asks for ≥ 2 layers <i>before</i> it.</li>
 *   <li><b>No way back.</b> The swap is permanent and {@code once_per_battle}, so after the enhanced attack spends its
 *       two layers (「消耗 2 层」) the ordinary attack is still the enhanced row.</li>
 * </ol>
 */
public class LukaEnhancedBasicGapTest {

    private static final int LEVEL = 80;
    private static final int LUKA = 1111;
    private static final String FIGHTING_SPIRIT = "斗志";

    /**
     * Today: the attacking cast itself becomes enhanced even though it started with one layer.
     */
    @Test
    public void theCrossingAttackIsAlreadyEnhanced() {
        Fixture f = new Fixture();
        Assertions.assertEquals(1, f.luka.getBuffManager().stacksOf(FIGHTING_SPIRIT), "the talent starts at one layer");
        f.castBasic();
        Assertions.assertEquals(2, f.luka.getBuffManager().stacksOf(FIGHTING_SPIRIT), "the basic reaches two layers");
        System.out.println("[1111] after the crossing basic: slot=" + f.commonSlot());
        Assertions.assertEquals(8, f.commonSlot(),
                "TODAY the crossing attack is itself resolved as enhanced (the layer is gained on its own event)");
    }

    /**
     * Today: spending the layers does not take the enhancement away.
     */
    @Test
    public void theEnhancedAttackDoesNotSpendTheLayers() {
        Fixture f = new Fixture();
        f.castSkill();                       // two layers
        f.castBasic();
        f.castBasic();
        System.out.println("[1111] after skill + two basics: stacks=" + f.luka.getBuffManager().stacksOf(FIGHTING_SPIRIT)
                + " slot=" + f.commonSlot());
        Assertions.assertEquals(2, f.luka.getBuffManager().stacksOf(FIGHTING_SPIRIT),
                "TODAY the enhanced attack did NOT spend its two layers (「消耗 2 层【斗志】」)");
        Assertions.assertEquals(8, f.commonSlot(),
                "and the swap stays, so 「< 2 层时是普通普攻」 cannot happen either");
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

        private int commonSlot() {
            return luka.getSkills().get(SkillType.COMMON).getSkillSlot();
        }

        private void castSkill() {
            battle.castImmediate(luka.getSkills().get(SkillType.SKILL), luka, List.of(enemy));
        }

        private void castBasic() {
            battle.castImmediate(luka.getSkills().get(SkillType.COMMON), luka, List.of(enemy));
        }
    }
}




