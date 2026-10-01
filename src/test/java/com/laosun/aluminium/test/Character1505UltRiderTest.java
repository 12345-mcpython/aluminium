package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1505 \u7eef\u82f1\u2019s ultimate rider (2026-09-30): \u300c\u5f53\u7eef\u82f1\u6301\u6709\u3010\u597d\u6d3b\u5f53\u8d4f\u3011\u65f6\uff0c\u65bd\u653e\u7ec8\u7ed3\u6280\u53ef\u5bf9\u654c\u65b9\u5168\u4f53\u9020\u6210 12% \u7684\u7269\u7406\u5c5e\u6027\u6b22\u6109\u4f24\u5bb9\u300d.
 *
 * <p>\u2b50 The rider is wired to CAST_SETUP, which fires ONCE per cast: on DEALING_DAMAGE an all-target ultimate would produce one
 * instance per victim and the rider would follow N times. The judge reads the two enemies\u2019 health and pins the shipped rule too,
 * because "more damage than without" alone cannot see a change to the percentage or the type.
 */
public class Character1505UltRiderTest {
    private static final int WEARER = 1505;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final String GIFTS = "\u597d\u6d3b\u5f53\u8d4f";

    private double ultimateDamage(boolean keepTheGifts) {
        Character elation = CharacterFactory.create(WEARER, LEVEL);
        Enemy first = EnemyFactory.create(MONSTER, 90, 1);
        Enemy second = EnemyFactory.create(MONSTER, 90, 2);
        Battle battle = new Battle(List.of(elation, CharacterFactory.create(ALLY, LEVEL)), List.of(first, second),
                new Random(0));
        battle.startBattle();
        if (!keepTheGifts) {
            elation.getResources().spend(GIFTS, elation.getResources().value(GIFTS));
        }
        double before = first.getCurrentHp() + second.getCurrentHp();
        battle.castImmediate(elation.getSkills().get(SkillType.ULTRA), elation, List.of(first, second));
        return before - (first.getCurrentHp() + second.getCurrentHp());
    }

    @Test
    public void theUltimateRiderNeedsTheGifts() {
        double withGifts = ultimateDamage(true);
        double without = ultimateDamage(false);
        System.out.println("[1505-ult] the ultimate took " + withGifts + " in total while holding \u3010\u597d\u6d3b\u5f53\u8d4f\u3011 and "
                + without + " after spending it (difference " + (withGifts - without) + ")");
        Assertions.assertTrue(without > 0, "the ultimate itself deals damage");
        Assertions.assertTrue(withGifts > without, "holding \u3010\u597d\u6d3b\u5f53\u8d4f\u3011 adds the all-enemy Elation rider");
    }

    /** \u2605 The shipped rule, read off the compiled table (discipline 232): the number, the scope and the type. */
    @Test
    public void theShippedUltRiderStatesItsNumberScopeAndType() {
        Character elation = CharacterFactory.create(WEARER, LEVEL);
        var rules = elation.getTriggerTable().rulesFor(com.laosun.aluminium.enums.TriggerEvent.CAST_SETUP)
                .stream().filter(rule -> rule.id().startsWith("p1505_")).toList();
        Assertions.assertEquals(2, rules.size(),
                "both ultimate riders (the 12% all-enemy one and the 14% random one) are CAST_SETUP rules");
        var effect = rules.getFirst().effects().getFirst();
        System.out.println("[1505-ult] spec " + rules.getFirst().id() + " percent=" + effect.getPercent()
                + " target=" + effect.getTarget() + " damage_type=" + effect.getDamageType());
        Assertions.assertEquals(0.12, effect.getPercent(), 1e-9, "12% of her attack");
        Assertions.assertEquals("all_enemies", effect.getTarget(), "over the whole enemy side");
        Assertions.assertEquals("ELATION", effect.getDamageType(), "as \u6b22\u6109\u4f24\u5bb9");
    }
}
