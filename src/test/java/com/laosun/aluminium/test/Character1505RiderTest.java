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
 * 1505 绯英’s skill rider (2026-09-30): 「当绯英持有【好活当赏】时，施放战技可对受到攻击的敌方目标造成 8% 的物理属性欢愉伤容」.
 *
 * <p>⭐ Two-sided on the SAME cast: with the resource her skill costs the enemy extra health, and after spending it the same cast
 * deals strictly less. That is what 「持有【好活当赏】时」 means, read on real numbers.
 */
public class Character1505RiderTest {
    private static final int WEARER = 1505;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final String GIFTS = "好活当赏";

    private double skillDamage(boolean keepTheGifts) {
        Character elation = CharacterFactory.create(WEARER, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(elation, CharacterFactory.create(ALLY, LEVEL)), List.of(enemy),
                new Random(0));
        battle.startBattle();
        if (!keepTheGifts) {
            elation.getResources().spend(GIFTS, elation.getResources().value(GIFTS));
        }
        double before = enemy.getCurrentHp();
        battle.castImmediate(elation.getSkills().get(SkillType.SKILL), elation, List.of(enemy));
        return before - enemy.getCurrentHp();
    }

    @Test
    public void theRiderNeedsTheGifts() {
        double withGifts = skillDamage(true);
        double without = skillDamage(false);
        System.out.println("[1505-rider] the skill took " + withGifts + " while holding 【好活当赏】 and "
                + without + " after spending it (difference " + (withGifts - without) + ")");
        Assertions.assertTrue(without > 0, "the skill itself deals damage");
        Assertions.assertTrue(withGifts > without,
                "and holding 【好活当赏】 adds the 8% Elation rider on top");
    }

    /**
     * ★ The shipped numbers and the type, read off the compiled rule (2026-09-30). The behavioural test above only asks
     * whether holding 【好活当赏】 adds damage, so a change to `percent` (8 -> 4) or to `damageType` would still satisfy it --
     * measured: both mutations came back with 0 red before this test existed (discipline 232).
     */
    @Test
    public void theShippedRiderStatesItsNumberAndType() {
        Character elation = CharacterFactory.create(WEARER, LEVEL);
        var rules = elation.getTriggerTable().rulesFor(com.laosun.aluminium.enums.TriggerEvent.DEALING_DAMAGE)
                .stream().filter(rule -> rule.id().startsWith("p1505_")).toList();
        Assertions.assertEquals(1, rules.size(), "the skill rider is one rule on DEALING_DAMAGE");
        var effect = rules.getFirst().effects().getFirst();
        System.out.println("[1505-rider] spec " + rules.getFirst().id() + " scale=" + effect.getScale()
                + " percent=" + effect.getPercent() + " element=" + effect.getElement()
                + " damageType=" + effect.getDamageType());
        Assertions.assertEquals(0.08, effect.getPercent(), 1e-9, "8% of the derived scale");
        Assertions.assertEquals("self_attr:ATTACK", effect.getScale(), "which is her own attack");
        Assertions.assertEquals("physical", effect.getElement(), "the element her text names");
        Assertions.assertEquals("ELATION", effect.getDamageType(), "and the type it calls 欢愉伤容");
    }
}
