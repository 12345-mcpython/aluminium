package com.laosun.aluminium.test.content.characters.boothill;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1315 波提欧's talent: 「每层【优势口袋】使强化普攻的削韧值提高 50%，最多叠加 3 层」.
 *
 * <p><b>Why per-stack, read live.</b> The sentence says 「每层」 - the CURRENT count - and the count can move between the cast and
 * the settlement. {@code BOOST_TOUGHNESS} therefore names the counter ({@code per_stack} + {@code per_stack_cap}) and
 * {@code ToughnessBoostBuff.effectivePercent} multiplies at settlement time. This judge measures the enemy's toughness loss with
 * 0, 1 and 3 stacks: 1x, 1.5x and 2.5x.
 */
public class BoothillPocketToughnessTest {
    private static final int BOOTHILL = 1315;
    private static final int LEVEL = 80;
    private static final String POCKET = "优势口袋";

    @Test
    public void eachPocketStackRaisesTheEnhancedBasicsToughness() {
        double none = toughnessLoss(0);
        double one = toughnessLoss(1);
        double three = toughnessLoss(3);
        System.out.println("[1315] toughness loss: 0=" + none + " 1=" + one + " 3=" + three);
        Assertions.assertTrue(none > 0, "precondition: the enhanced Basic reduces toughness at all");
        Assertions.assertEquals(1.5, one / none, 1e-6, "one stack is 「提高 50%」: " + one + " vs " + none);
        Assertions.assertEquals(2.5, three / none, 1e-6, "three stacks are +150%: " + three + " vs " + none);
    }

    /** Toughness the enhanced Basic took off, with {@code stacks} of 【优势口袋】 on him. */
    private static double toughnessLoss(int stacks) {
        Character boothill = CharacterFactory.create(BOOTHILL, LEVEL);
        Enemy enemy = BattleFixture.monster();
        enemy.setMaxStance(1_000_000);
        enemy.setStance(1_000_000);
        // A weakness the basic's own element can break, or the reduction is refused (the engine only chips at weaknesses).
        enemy.addWeakness(com.laosun.aluminium.enums.DamageElement.PHYSICAL);
        Battle battle = new Battle(new ArrayList<>(List.of(boothill)), new ArrayList<>(List.of(enemy)),
                BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        battle.processRequests();
        for (int i = 0; i < stacks; i++) {
            battle.fire(new TriggerPayload.Cast(TriggerEvent.KILL, boothill, enemy, 1, 0.0,
                    null, 0, 0, List.of(enemy)));
            battle.processRequests();
        }
        Assertions.assertEquals(stacks, boothill.getBuffManager().stacksOf(POCKET),
                "precondition: " + stacks + " stacks of 【优势口袋】");

        double before = enemy.getStance();
        // The enhanced Basic is a data row of its own (slot 8); casting it directly keeps the criterion about the talent.
        battle.castImmediate(new com.laosun.aluminium.skill.DefaultSkill(BOOTHILL, 8, LEVEL), boothill, List.of(enemy));
        battle.processRequests();
        return before - enemy.getStance();
    }
}
