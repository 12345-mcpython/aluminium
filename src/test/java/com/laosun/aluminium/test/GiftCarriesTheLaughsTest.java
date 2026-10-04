package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.buff.StateBuff;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * The gift carries the 【笑点】 count, and 1505 takes half of it (2026-10-02) -- objective ①-a's whole chain.
 *
 * <p>Content only: 1513's skill grants 4 【笑点】, an 【阿哈时刻】 ends, her reward applies the gift with FOUR instances, and ending
 * it pays 1505 half of four. The cap is spelled `max_stacks`, which is the trap this reading was written around: the Java name
 * is accepted by the key guard and then dropped by Gson, leaving one instance.
 */
public class GiftCarriesTheLaughsTest {
    private static final int SPARKLE = 1513;
    private static final int EVANESCIA = 1505;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final String LAUGHS = "\u7b11\u70b9";
    private static final String GIFT = "\u597d\u6d3b\u5f53\u8d4f";
    private static final String MOMENT = "\u963f\u54c8\u65f6\u523b";

    /** Four laughs become four instances, and half of them become hers. */
    @Test
    public void theGiftCarriesTheLaughsAndSheTakesHalf() {
        Character sparkle = CharacterFactory.create(SPARKLE, LEVEL, false, null, null, 0);
        Character evanescia = CharacterFactory.create(EVANESCIA, LEVEL, false, null, null, 0);
        Enemy victim = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(sparkle, evanescia), List.of(victim), new Random(0));
        battle.startBattle();
        battle.processRequests();

        battle.castImmediate(sparkle.getSkills().get(SkillType.SKILL), sparkle, List.of(victim));
        battle.processRequests();
        int laughs = battle.partyResourceValue(LAUGHS);
        System.out.println("[gift-laughs] laughs=" + laughs);
        Assertions.assertEquals(4, laughs, "precondition: the skill grants four");

        sparkle.getBuffManager().addBuff(new StateBuff(MOMENT, 1));
        advance(battle);
        int instances = sparkle.getBuffManager().stacksOf(GIFT);
        System.out.println("[gift-laughs] gift instances=" + instances);
        Assertions.assertEquals(4, instances, "the state carries one instance per laugh");
        // ⭐ 「阿哈行动后会消耗全部笑点」 (glossary 10000026): the count was read FIRST, then the counter was emptied --
        // asserting both in one scene is what makes the ordering part of the reading rather than of the prose.
        Assertions.assertEquals(0, battle.partyResourceValue(LAUGHS),
                "and all the laughs are spent at the same moment, after the gift has taken its count");

        int before = evanescia.getResources().value(GIFT);
        sparkle.getBuffManager().removeState(GIFT);
        battle.processRequests();
        int after = evanescia.getResources().value(GIFT);
        System.out.println("[gift-laughs] hers " + before + " -> " + after);
        Assertions.assertEquals(before + 2, after, "half of four becomes hers");
    }

    private static void advance(Battle battle) {
        Signal signal = battle.queue.snapshot().stream()
                .filter(candidate -> candidate.getCanHit() == battle.allies.getFirst()).findFirst()
                .orElseThrow(() -> new AssertionError("precondition: a unit is in the queue"));
        battle.currentMove = signal;
        battle.beforeMove();
        battle.afterMove();
        battle.processRequests();
    }
}
