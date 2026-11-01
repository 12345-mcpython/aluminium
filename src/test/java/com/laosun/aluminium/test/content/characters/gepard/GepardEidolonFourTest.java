package com.laosun.aluminium.test.content.characters.gepard;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1104 杰帕德's Eidolon 4: 「杰帕德在场时，我方全体的效果抵抗提高 20%」 - the bonus is anchored to HIM, so it leaves with him.
 *
 * <p>Measured as a DELTA against the same ally without the Eidolon: the ally's own panel already carries some resistance.
 */
public class GepardEidolonFourTest {

    private static final int GEPARD = 1104;
    private static final int ALLY = 1001;
    private static final int LEVEL = 80;

    @Test
    public void theResistanceLeavesWhenHeFalls() {
        double bare = allyResistance(0);
        double withEidolon = allyResistance(4);
        Assertions.assertEquals(0.2, withEidolon - bare, 1e-9, "「效果抵抗提高 20%」");

        Character gepard = CharacterFactory.create(GEPARD, LEVEL, true, null, null, 4);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(gepard, ally)),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        // His Talent survives the FIRST lethal blow once per battle, so the judge spends it and then lands the fatal one.
        battle.applyTrueDamage(enemy, gepard, DamageElement.PHYSICAL, gepard.getCurrentHp() * 10);
        battle.processRequests();
        battle.applyTrueDamage(enemy, gepard, DamageElement.PHYSICAL, gepard.getCurrentHp() * 10);
        battle.processRequests();
        Assertions.assertTrue(gepard.isDeath(), "precondition: he is down");
        double after = ally.getAttribute(AttributeType.EFFECT_RESISTANCE).get();
        System.out.println("[1104] bare=" + bare + " with=" + withEidolon + " after his death=" + after);
        Assertions.assertEquals(bare, after, 1e-9, "「在场时」 - the bonus left with him");
    }

    private static double allyResistance(int eidolon) {
        Character gepard = CharacterFactory.create(GEPARD, LEVEL, true, null, null, eidolon);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Battle battle = new Battle(new ArrayList<>(List.of(gepard, ally)),
                new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        return ally.getAttribute(AttributeType.EFFECT_RESISTANCE).get();
    }
}
