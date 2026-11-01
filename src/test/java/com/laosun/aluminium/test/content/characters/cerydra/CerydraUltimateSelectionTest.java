package com.laosun.aluminium.test.content.characters.cerydra;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.buff.StateBuff;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1412 刻律德菈's Ultimate:「若场上不存在持有【军功】的角色，优先使当前队伍中的第一位角色获得【军功】」.
 *
 * <p>Two directions: with nobody holding it the Ultimate hands it to another ally; with a holder already there it leaves
 * things alone. ⚠ It targets {@code next_ally}, not {@code party_first}: the latter picks Cerydra herself, and measured,
 * that pushed {@code OdeToLawChargeTest}'s charge from 6 to 8.
 */
public class CerydraUltimateSelectionTest {

    @Test
    public void theUltimateGivesTheMeritWhenNobodyHasIt() {
        Character her = CharacterFactory.create(1412, 80);
        Character ally = CharacterFactory.create(1101, 80);
        Battle battle = new Battle(new ArrayList<>(List.of(her, ally)),
                new ArrayList<>(List.of(BattleFixture.dummy())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        her.setCurrentEnergy(her.getMaxEnergy());
        battle.castImmediate(her.getSkills().get(SkillType.ULTRA), her, List.of(battle.enemyUnits().getFirst()));
        battle.processRequests();
        System.out.println("[1412] ult: ally merit=" + ally.getBuffManager().hasState("军功")
                + " ; her merit=" + her.getBuffManager().hasState("军功"));
        Assertions.assertTrue(ally.getBuffManager().hasState("军功"), "「使…第一位角色获得【军功】」");
    }

    @Test
    public void withAHolderAlreadyTheUltimateLeavesItAlone() {
        Character her = CharacterFactory.create(1412, 80);
        Character ally = CharacterFactory.create(1101, 80);
        Character other = CharacterFactory.create(1102, 80);
        ally.getBuffManager().addBuff(new StateBuff("军功", 1));
        Battle battle = new Battle(new ArrayList<>(List.of(her, ally, other)),
                new ArrayList<>(List.of(BattleFixture.dummy())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        her.setCurrentEnergy(her.getMaxEnergy());
        battle.castImmediate(her.getSkills().get(SkillType.ULTRA), her, List.of(battle.enemyUnits().getFirst()));
        battle.processRequests();
        Assertions.assertTrue(ally.getBuffManager().hasState("军功"), "the existing holder keeps it");
        Assertions.assertFalse(other.getBuffManager().hasState("军功"), "「若场上不存在…」: nobody else gets one");
    }
}