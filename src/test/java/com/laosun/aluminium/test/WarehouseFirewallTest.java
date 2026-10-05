package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.buff.ControlBuff;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1506 Silver Wolf LV.999's warehouse technique "999安全卫士" (999 Security Guard): "若敌方目标对我方施加了控制类负面状态，则使我方全体获得[防火墙]…该效果每个波次最多触发 1 次" (if an enemy applies a control-class negative state to our side, all of our side gains [防火墙], and this effect triggers at most once per wave).
 *
 * <p>This is what three shipped pieces were for: the warehouse LOAD POINT (she is owned but not deployed), the CONTROL-CLASS FILTER
 * ("控制类负面状态", control-class negative states), and the PER-WAVE CAP ("每个波次最多触发 1 次", at most once per wave). The rule itself is read from the real content file
 * `resources/warehouse/1506.json` -- so the readings below are about the file, not about a scene built in this test.
 *
 * <p>Timing is the document's own: the control that TRIGGERS the clause still lands; [防火墙] is what immunises the ones after it.
 */
public class WarehouseFirewallTest {
    private static final int FIGHTER = 1002;
    private static final int WOLF = 1506;
    private static final int MONSTER = 1002011;

    /** The trigger grants [防火墙], and the NEXT control is refused; the wave cap holds it to one grant. */
    @Test
    public void theFirewallAnswersTheNextControl() {
        Character fighter = CharacterFactory.create(FIGHTER, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(fighter),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));

        // owned, not deployed: she is a LISTENER, and her table is the warehouse file rather than her battle kit
        Character wolf = CharacterFactory.create(WOLF, 80, false, null, null, 0);
        wolf.setTriggerTable(TriggerTables.warehouse(WOLF));
        Assertions.assertFalse(wolf.getTriggerTable().isEmpty(),
                "precondition: the warehouse file loads through the loader");
        battle.registerWarehouseListener(wolf);
        battle.startBattle();
        battle.processRequests();
        battle.beginWave();

        // Note: NOT a hand-fired event: the debuff CLASS the clause asks about is recorded by the real landing path
        // (`Battle.tryApplyDebuff`), so a synthesised DEBUFF_APPLIED carries no family and would match nothing.
        boolean firstLanded = battle.tryApplyDebuff(battle.enemies.getFirst(), fighter,
                new ControlBuff(com.laosun.aluminium.Constant.CONTROL_EFFECTS.get("FROZEN"), 2), 1.0, "STAT_CTRL_Frozen");
        battle.processRequests();
        boolean firewallOn = fighter.getBuffManager().hasState("防火墙");

        // and the payoff: the NEXT control on her is refused -- [防火墙] is what answers it
        boolean landedAfter = battle.tryApplyDebuff(battle.enemies.getFirst(), fighter,
                new ControlBuff(com.laosun.aluminium.Constant.CONTROL_EFFECTS.get("FROZEN"), 2), 1.0, "STAT_CTRL_Frozen");
        Assertions.assertTrue(firstLanded, "precondition: the TRIGGERING control really landed");
        System.out.println("[firewall] firewall granted=" + firewallOn + " ; next control landed=" + landedAfter
                + " ; controls on her=" + fighter.getBuffManager().countBuffs(ControlBuff.class));

        Assertions.assertTrue(firewallOn,
                "「则使我方全体获得【防火墙】」-- the warehouse clause fired from a character who is not on the field");
        Assertions.assertFalse(landedAfter,
                "「【防火墙】状态下，我方目标免疫敌方目标施加的控制类负面状态」-- the next control is refused");
    }
}
