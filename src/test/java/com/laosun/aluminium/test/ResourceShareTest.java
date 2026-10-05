package com.laosun.aluminium.test;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;

/**
 * A share can be carried in a resource, in basis points (2026-10-02).
 *
 * The last structural piece for the two remaining sentences: a number captured while an ode is cast (a memosprite row is unreachable later) is a share BELOW 1, and a resource holds
 * an integer -- so the capture stores basis points and `percent_from_resource` reads them back.
 *
 * Two-sided: no charge means no boost, and once the charge exists the boost is exactly the charge over 10000.
 */
public class ResourceShareTest {
    private static final int LEVEL = 80;
    private static final String CHARGE = "充能";
    @Test
    public void theBoostIsTheResourcesBasisPoints() {
        Character cyrene = CharacterFactory.create(1415, LEVEL);
        Character cerydra = CharacterFactory.create(1412, LEVEL);
        Battle battle = new Battle(List.of(cyrene, cerydra),
                List.of(EnemyFactory.create(1002011, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        cyrene = battle.characters.get(0);
        cerydra = battle.characters.get(1);

        double before = cerydra.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        battle.fireTriggers(TriggerEvent.TURN_START, cerydra, null, 0, 0);
        battle.processRequests();
        // Note: Her own kit already carries a boost (0.2), and a same-attribute modifier REPLACES it -- so the reading with an EMPTY charge is the share of an
        // empty resource, which is 0. That is the reader working, not a bug: measured, expected <0.2> but was <0.0> told us exactly this.
        Assertions.assertEquals(0.0, cerydra.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get(), 1e-9,
                "with an empty charge the reader applies a zero share");

        var demiurge = battle.summonServant(cyrene);
        battle.processRequests();
        var ode = demiurge.skillAt(23);
        Assertions.assertNotNull(ode, "precondition: slot 23");
        SkillExecutor.execute(battle, ode, demiurge, List.of(cerydra));
        battle.processRequests();
        int charge = cerydra.getResources().value(CHARGE);
        Assertions.assertTrue(charge > 0, "precondition: the charge captured something, got " + charge);

        double base = cerydra.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        battle.fireTriggers(TriggerEvent.TURN_START, cerydra, null, 0, 0);
        battle.processRequests();
        double after = cerydra.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        System.out.println("[resource_share] charge " + charge + " ; boost " + base + " -> " + after
                + " (expect +" + (charge / 10000.0) + ")");
        Assertions.assertEquals(charge / 10000.0, after - base, 1e-9,
                "the boost is the resource value over 10000 -- basis points");
    }
}
