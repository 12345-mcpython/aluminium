package com.laosun.aluminium.test.content.characters.cerydra;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.buff.StateBuff;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1412 刻律德菈's Eidolon 2, the second half:「场上存在持有【军功】的队友时，刻律德菈造成的伤害提高 160%」.
 *
 * <p>It needs the new {@code any_ally has_state} condition: the sentence asks a question about the party, and there is no
 * second unit to act on. The judge gives the state to another ally before the battle starts, because the condition is read
 * once at {@code BATTLE_START}.
 */
public class CerydraEidolonTwoTest {

    @Test
    public void anAllyHoldingMeritRaisesHerDamage() {
        double with = boost(true);
        double without = boost(false);
        System.out.println("[1412] E2 damage boost: ally has merit " + with + " ; nobody does " + without);
        Assertions.assertEquals(1.6, with - without, 1e-9, "「提高 160%」");
    }

    @Test
    public void belowTheRankThereIsNone() {
        Assertions.assertEquals(0.0, boostAt(1), 1e-9, "the control: nothing at Eidolon 1");
    }

    private static double boost(boolean allyHasMerit) {
        return boostAt(2, allyHasMerit);
    }

    private static double boostAt(int eidolon) {
        return boostAt(eidolon, true);
    }

    private static double boostAt(int eidolon, boolean allyHasMerit) {
        Character her = CharacterFactory.create(1412, 80, true, null, null, eidolon);
        Character ally = CharacterFactory.create(1101, 80);
        if (allyHasMerit) {
            ally.getBuffManager().addBuff(new StateBuff("军功", 1));
        }
        Battle battle = new Battle(new ArrayList<>(List.of(her, ally)),
                new ArrayList<>(List.of(BattleFixture.dummy())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(
                com.laosun.aluminium.enums.TriggerEvent.TURN_START, her, her, 0, 0));
        return her.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
    }
}