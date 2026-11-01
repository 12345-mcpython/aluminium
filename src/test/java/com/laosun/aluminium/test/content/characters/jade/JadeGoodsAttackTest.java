package com.laosun.aluminium.test.content.characters.jade;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1314 翡翠's trace 绝当品: 「每层【当品】额外使翡翠的攻击力提高 0.50%」.
 *
 * <p>The counter lives on HER, and it rises during the battle - so the modifier has to read the count live
 * ({@code per_stack_live}); a snapshot would freeze her ATK at the value it had when the aura landed.
 */
public class JadeGoodsAttackTest {

    private static final int LEVEL = 80;
    private static final int JADE = 1314;

    /**
     * Ten layers are +5% ATK, and the buff follows a later change.
     */
    @Test
    public void everyGoodsLayerRaisesHerAttack() {
        Battle battle = battle();
        Character jade = battle.characters().getFirst();
        double before = jade.getAttribute(AttributeType.ATTACK).get();
        // The share is of the ATTRIBUTE's base, and her panel already sits above it (weapon/relic share), so the DELTA is
        // the honest measurement - a ratio against the panel would read +4.2% for a +5% share.
        double base = jade.getAttribute(AttributeType.ATTACK).baseValue();

        addGoods(jade, 10);
        double ten = jade.getAttribute(AttributeType.ATTACK).get();
        System.out.println("[1314] base=" + base + " before=" + before + " 10 layers=" + ten);
        Assertions.assertEquals(0.005 * 10 * base, ten - before, 1e-6, "「每层【当品】额外使攻击力提高 0.50%」");

        addGoods(jade, 10);
        double twenty = jade.getAttribute(AttributeType.ATTACK).get();
        Assertions.assertEquals(0.005 * 10 * base, twenty - ten, 1e-6, "the LIVE count follows a later change");
    }

    // ==================================================================

    private static Battle battle() {
        Character jade = CharacterFactory.create(JADE, LEVEL);
        Battle battle = new Battle(new ArrayList<>(List.of(jade, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.seeded());
        battle.startBattle();
        return battle;
    }

    private static void addGoods(Character jade, int layers) {
        for (int i = 0; i < layers; i++) {
            jade.getBuffManager().addBuff(new com.laosun.aluminium.buff.StackBuff("当品", 1, true, 50));
        }
    }

    /**
     * A battle where the trace's rule is the only source of ATK change: with no 当品 layers the panel equals a character
     * built without the rule? That cannot be done here, so the check is that the panel does not move when nothing is added.
     */
    private static double offBattle() {
        Battle battle = battle();
        Character jade = battle.characters().getFirst();
        double before = jade.getAttribute(AttributeType.ATTACK).get();
        return jade.getAttribute(AttributeType.ATTACK).get() - before;
    }
}

