package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The {@code random_enemy} selector (2026-09-30), reader 1505 绯英’s ultimate: 「对终结技<b>随机</b>造成伤容的敌方目标造成 14%…」.
 *
 * <p>⭐ Three readings, all on real battles: the pick is always one of the opponents; the SAME seed picks the same unit twice (so the
 * engine is reproducible); and across many seeds the pick is not always the same one (so it really is a roll, not a constant).
 */
public class RandomEnemySelectorTest {
    private static final int WEARER = 1505;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** Sums the health a fixed-seed ultimate takes off the enemies, and reports which one lost more (the rider’s pick). */
    private List<Double> losses(long seed) {
        Character elation = CharacterFactory.create(WEARER, LEVEL);
        Enemy first = EnemyFactory.create(MONSTER, 90, 1);
        Enemy second = EnemyFactory.create(MONSTER, 90, 2);
        Battle battle = new Battle(List.of(elation, CharacterFactory.create(ALLY, LEVEL)), List.of(first, second),
                new Random(seed));
        battle.startBattle();
        double firstBefore = first.getCurrentHp();
        double secondBefore = second.getCurrentHp();
        battle.castImmediate(elation.getSkills().get(SkillType.ULTRA), elation, List.of(first, second));
        List<Double> losses = new ArrayList<>();
        losses.add(firstBefore - first.getCurrentHp());
        losses.add(secondBefore - second.getCurrentHp());
        return losses;
    }

    @Test
    public void theSameSeedPicksTheSameEnemyAndDifferentSeedsDoNot() {
        List<Double> a = losses(7);
        List<Double> b = losses(7);
        System.out.println("[random] seed 7 twice: " + a + " / " + b);
        Assertions.assertEquals(a, b, "the same seed reproduces the same battle, pick included");
        Assertions.assertTrue(a.get(0) > 0 && a.get(1) > 0, "the ultimate reaches both enemies");

        int firstBigger = 0;
        for (long seed = 0; seed < 12; seed++) {
            List<Double> picked = losses(seed);
            if (picked.get(0) > picked.get(1)) {
                firstBigger++;
            }
        }
        System.out.println("[random] across 12 seeds the FIRST enemy took the bigger share in " + firstBigger
                + " of them");
        Assertions.assertTrue(firstBigger > 0 && firstBigger < 12,
                "the roll really varies (it favoured the first enemy " + firstBigger + " times out of 12)");
    }

    /** ★ The shipped rule, read off the compiled table (discipline 232). */
    @Test
    public void theShippedRuleNamesTheRandomSelector() {
        Character elation = CharacterFactory.create(WEARER, LEVEL);
        var rules = elation.getTriggerTable().rulesFor(com.laosun.aluminium.enums.TriggerEvent.CAST_SETUP)
                .stream().filter(rule -> rule.id().endsWith("random_rider")).toList();
        Assertions.assertEquals(1, rules.size(), "one rule carries the random half");
        var effect = rules.getFirst().effects().getFirst();
        System.out.println("[random] spec " + rules.getFirst().id() + " percent=" + effect.getPercent()
                + " target=" + effect.getTarget() + " damage_type=" + effect.getDamageType());
        Assertions.assertEquals(0.14, effect.getPercent(), 1e-9, "14% of her attack");
        Assertions.assertEquals("random_enemy", effect.getTarget(), "aimed by the roll");
        Assertions.assertEquals("ELATION", effect.getDamageType(), "as 欢愉伤容");
    }
}
