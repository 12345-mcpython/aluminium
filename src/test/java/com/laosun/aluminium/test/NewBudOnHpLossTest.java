package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 140："我方全体每损失 1 点生命值遐蝶获得 1 点[新蕊]" (2026-10-02).
 *
 * <p>MEASURED, and it took a probe to see it: the enemy never touched her -- "hp 1629.936 -> 1629.936 (max 1629.936)
 * after 40 steps" -- so every earlier draft failed because NO HP LOSS EVER HAPPENED. Damage now goes through the battle's own
 * entry point ({@code Battle.applyTrueDamage}); a bare {@code CanHit.takeDamage} does not reach the battle's HP-loss dispatch.
 *
 * <p>The scale is asserted EXACTLY: 50 HP lost -> 50 buds. The reader is {@code talent_newbud_per_hp_lost}, which uses
 * {@code amountFromEvent: true} -- "give what the event gave". Note: The mutation for this judge must change THAT rule (an
 * earlier attempt mutated a duplicate I had added, and the suite stayed green because the real reader was doing the work).
 */
public class NewBudOnHpLossTest {
    private static final int OWNER = 1407;
    private static final int ALLY = 1002;
    private static final int MONSTER = 1002011;
    private static final String RES = "新蕊";

    /** One bud per point of HP she loses. */
    @Test
    public void losingHpGivesOneBudPerPoint() {
        Character owner = CharacterFactory.create(OWNER, 80);
        Character ally = CharacterFactory.create(ALLY, 80);
        Battle battle = new Battle(List.of(owner, ally),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        Assertions.assertNotNull(battle.partyResource(RES),
                "the new bud must be reachable as a party resource");
        double before = battle.partyResource(RES).value();

        double dealt = battle.applyTrueDamage(battle.enemies.get(0), owner, DamageElement.ICE, 50.0);
        battle.processRequests();
        Assertions.assertEquals(50.0, dealt, "precondition: the battle really took 50 HP off her");

        Assertions.assertEquals(before + 50, battle.partyResource(RES).value(),
                "「每损失 1 点生命值遐蝶获得 1 点【新蕊】」 (before=" + before + ")");
    }
}
