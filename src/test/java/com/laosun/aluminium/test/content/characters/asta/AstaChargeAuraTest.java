package com.laosun.aluminium.test.content.characters.asta;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.buff.StackBuff;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1009 艾丝妲's talent: 「每拥有 1 层蓄能，会使我方全体攻击力提高 14.00%，该效果最多叠加 5 层」.
 *
 * <p>Two properties: the aura follows the LIVE count (adding a charge moves every ally's ATK without re-attaching anything),
 * and it stops at five layers even when the counter holds more.
 */
public class AstaChargeAuraTest {

    private static final int LEVEL = 80;
    private static final int ASTA = 1009;
    private static final String CHARGE = "蓄能";

    @Test
    public void everyChargeRaisesEveryonesAttackAndStopsAtFive() {
        Character asta = CharacterFactory.create(ASTA, LEVEL);
        Character ally = CharacterFactory.create(1001, LEVEL);
        Battle battle = new Battle(new ArrayList<>(List.of(asta, ally)),
                new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.seeded());
        battle.startBattle();

        double astaBase = asta.getAttribute(AttributeType.ATTACK).baseValue();
        double allyBase = ally.getAttribute(AttributeType.ATTACK).baseValue();
        double astaStart = asta.getAttribute(AttributeType.ATTACK).get();
        double allyStart = ally.getAttribute(AttributeType.ATTACK).get();

        addCharges(asta, 3);
        Assertions.assertEquals(0.14 * 3 * astaBase, asta.getAttribute(AttributeType.ATTACK).get() - astaStart, 1e-6,
                "3 layers = +42% of the base, on her");
        Assertions.assertEquals(0.14 * 3 * allyBase, ally.getAttribute(AttributeType.ATTACK).get() - allyStart, 1e-6,
                "and on the ally (「我方全体」)");

        addCharges(asta, 4);   // 7 layers, the aura must stop at 5
        double astaAtSeven = asta.getAttribute(AttributeType.ATTACK).get() - astaStart;
        System.out.println("[1009] 7 layers -> " + astaAtSeven + " ; capped = " + (0.14 * 5 * astaBase));
        Assertions.assertEquals(0.14 * 5 * astaBase, astaAtSeven, 1e-6,
                "「该效果最多叠加 5 层」 - seven layers still pay five");
    }

    private static void addCharges(Character asta, int layers) {
        for (int i = 0; i < layers; i++) {
            asta.getBuffManager().addBuff(new StackBuff(CHARGE, 1, true, 99));
        }
    }
}
