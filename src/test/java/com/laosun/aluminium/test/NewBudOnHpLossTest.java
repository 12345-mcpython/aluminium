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
 * 1407：「我方全体每损失 1 点生命值遐蝶获得 1 点【新蕊】」 (2026-10-02).
 *
 * <p>⭐ MEASURED, and it took a probe to see it (2026-10-02): the enemy never touched her -- "hp 1629.936 -> 1629.936
 * (max 1629.936) after 40 steps" -- so every earlier draft failed because NO HP LOSS EVER HAPPENED. Damage now goes through
 * the battle's own entry point ({@code Battle.applyTrueDamage}); a bare {@code CanHit.takeDamage} does not reach the battle's
 * HP-loss dispatch.
 *
 * <p>⭐ The scale is asserted EXACTLY, not loosely: 50 HP lost -> 50 buds. The rule says {@code amount: 0} because the engine
 * already grants the points lost by itself and adds the literal amount on top -- measured both ways (100 -> 101 and 50 -> 51
 * with {@code amount: 1}).
 */
public class NewBudOnHpLossTest {
    private static final int OWNER = 1407;
    private static final int ALLY = 1002;
    private static final int MONSTER = 1002011;
    private static final String RES = "新蕊";

    /** ⭐ Losing HP gives her the new bud. */
    @Test
    public void losingHpGivesTheNewBud() {
        Character owner = CharacterFactory.create(OWNER, 80);
        Character ally = CharacterFactory.create(ALLY, 80);
        Battle battle = new Battle(List.of(owner, ally),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        double before = owner.getResources().has(RES) ? owner.getResources().value(RES) : 0;
        // ⭐ 2026-10-02, measured twice over: 【新蕊】 is declared with `scope: "PARTY"`, so it is NOT in `owner.getResources()` --
        // that is precisely what the first split assertion reported ("must be in the battle's resource table" was false).
        // The declaration's own note names the way in: `partyResource(name)`, which returns null when the scope is missing.
        com.laosun.aluminium.models.Resource bud = battle.partyResource(RES);
        Assertions.assertNotNull(bud, "the new bud must be reachable as a party resource");
        before = bud.value();

        // ⭐ 2026-10-02, MEASURED (a one-off probe reported "hp 1629.936 -> 1629.936 (max 1629.936) after 40 steps"):
        // the enemy never touched her, so EVERY earlier draft failed for one trivial reason -- no HP loss ever happened.
        // ⚠ And a BARE `CanHit.takeDamage(double)` does not reach `Battle`'s HP-loss dispatch either (`Battle:2085` lives
        // inside the battle's own damage path). So damage her through the battle's own entry point:
        double dealt = battle.applyTrueDamage(battle.enemies.get(0), owner, DamageElement.ICE, 50.0);
        battle.processRequests();
        Assertions.assertEquals(50.0, dealt, "precondition: the battle really took 50 HP off her");

        // ⭐ And the engine honours "per point" by itself: 50 HP lost -> exactly 50 buds, from an `amount: 1` rule.
        Assertions.assertEquals(before + 50, battle.partyResource(RES).value(),
                "「每损失 1 点生命值遐蝶获得 1 点【新蕊】」 (before=" + before + ")");
    }
}
