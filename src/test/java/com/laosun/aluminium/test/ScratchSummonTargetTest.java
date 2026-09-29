package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.Summon;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

public class ScratchSummonTargetTest {
    @Test
    public void measure() {
        StringBuilder out = new StringBuilder();
        for (String target : new String[] {"summon", "self"}) {
            for (String[] variant : new String[][] {{"TURN_START", "none"}, {"SUMMONED", "summon>=1"}}) {
                Character master = CharacterFactory.create(1413, 80);
                EffectSpec grant = new EffectSpec();
                TriggerSpecs.set(grant, "op", "MODIFY_ATTR");
                TriggerSpecs.set(grant, "attribute", "ALL_DAMAGE_TYPE_BOOST");
                TriggerSpecs.set(grant, "percent", 0.12);
                TriggerSpecs.set(grant, "permanent", true);
                TriggerSpecs.set(grant, "target", target);
                List<String> conditions = "none".equals(variant[1]) ? List.of() : List.of("self_summon_count >= 1");
                master.setTriggerTable(new TriggerTable(9903, List.of(TriggerSpecs.rule(
                        variant[0], conditions, grant))));
                Enemy enemy = EnemyFactory.create(1002011, 90, 1);
                Battle battle = new Battle(List.of(master), List.of(enemy), new Random(0));
                battle.startBattle();
                Summon memosprite = battle.summonMemosprite(master);
                if ("TURN_START".equals(variant[0])) {
                    battle.currentMove = new Signal(master);
                    battle.beforeMove();
                    battle.afterMove();
                }
                out.append("[probe] event=").append(variant[0]).append('/').append(variant[1])
                   .append(" target=").append(target)
                   .append(" onSummon=").append(memosprite.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get())
                   .append(" onMaster=").append(master.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get())
                   .append('\n');
            }
        }
        try {
            java.io.PrintWriter writer = new java.io.PrintWriter("build/round79_probe2.txt", "UTF-8");
            writer.print(out);
            writer.close();
        } catch (Exception ignored) {
            // a probe
        }
    }
}
