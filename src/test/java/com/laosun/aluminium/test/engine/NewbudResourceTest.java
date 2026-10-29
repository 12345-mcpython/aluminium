package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.models.entity.Character;
import com.laosun.aluminium.models.damage.Damage;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * [新蕊]: "我方全体每损失 1 点生命值逍蝶获得 1 点[新蕊]", the carrier's own half.
 *
 * <p>Three facts bound the number: the battle knows the resource (a PARTY scope is what puts it in the registry), the hit
 * really lands through `Battle.applyDamage`, and the gain equals the loss.
 */
public class NewbudResourceTest {
    private static final int CASTORICE = 1407;

    /**
     * One point of health lost is one point of Newbud.
     */
    @Test
    public void aPointLostIsAPointOfNewbud() {
        Character castorice = CharacterFactory.create(CASTORICE, 80, false, null, null, 0);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(castorice), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        Assertions.assertNotNull(battle.partyResource("新蕊"),
                "the battle must know 【新蕊】 (does the declaration say scope PARTY?)");
        int before = battle.partyResourceValue("新蕊");
        double hpBefore = castorice.getCurrentHp();
        battle.applyDamage(castorice, new Damage(enemy, castorice, DamageElement.FIRE, DamageType.NORMAL, 300));
        battle.processRequests();
        double lost = hpBefore - castorice.getCurrentHp();
        Assertions.assertTrue(lost > 0, "precondition: the hit landed (" + lost + ")");
        Assertions.assertEquals((int) Math.round(lost), battle.partyResourceValue("新蕊") - before,
                "one point of Newbud per point lost (" + lost + " lost)");
    }
}
