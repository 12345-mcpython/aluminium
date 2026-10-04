package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.buff.ControlBuff;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 「获得该角色后，或该角色在队伍中时」 -- the warehouse clause needs no manual registration (2026-10-02).
 *
 * <p>1506 sits in the party and nothing in this test registers her: `Battle.startBattle` finds her `warehouse/1506.json` and listens to
 * it. The third reading is the one that makes the shape right -- she is still IN THE QUEUE, i.e. her own battle table was left alone
 * (replacing it would silently delete her kit the moment she deployed).
 */
public class WarehouseAutoListenerTest {
    private static final int FIGHTER = 1002;
    private static final int WOLF = 1506;
    private static final int MONSTER = 1002011;

    /** The clause fires with no registration, and the owner keeps acting. */
    @Test
    public void aPartyMemberIsHeardWithoutGivingUpHerOwnTable() {
        Character fighter = CharacterFactory.create(FIGHTER, 80, false, null, null, 0);
        Character wolf = CharacterFactory.create(WOLF, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(fighter, wolf),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        battle.beginWave();

        // two real landings: the class the clause asks about is recorded by the landing path, not by a synthesised event
        boolean firstLanded = battle.tryApplyDebuff(battle.enemies.getFirst(), fighter,
                new ControlBuff(com.laosun.aluminium.Constant.CONTROL_EFFECTS.get("FROZEN"), 2), 1.0, "STAT_CTRL_Frozen");
        battle.processRequests();
        boolean firewallOn = fighter.getBuffManager().hasState("防火墙");
        boolean secondLanded = battle.tryApplyDebuff(battle.enemies.getFirst(), fighter,
                new ControlBuff(com.laosun.aluminium.Constant.CONTROL_EFFECTS.get("FROZEN"), 2), 1.0, "STAT_CTRL_Frozen");
        boolean wolfInQueue = battle.queue.snapshot().stream().anyMatch(signal -> signal.getCanHit() == wolf);
        System.out.println("[auto-warehouse] first landed=" + firstLanded + " firewall=" + firewallOn
                + " second landed=" + secondLanded + " owner still in queue=" + wolfInQueue);

        Assertions.assertTrue(firstLanded, "precondition: the triggering control really landed");
        Assertions.assertTrue(firewallOn,
                "「获得该角色后，或该角色在队伍中时」-- nobody registered her by hand, so the battle must have found her warehouse file");
        Assertions.assertFalse(secondLanded,
                "「【防火墙】状态下，我方目标免疫敌方目标施加的控制类负面状态」-- the next control is refused");
        Assertions.assertTrue(wolfInQueue,
                "and she keeps her own battle table: she is still a combatant, which a table swap would have taken away");
    }
}
