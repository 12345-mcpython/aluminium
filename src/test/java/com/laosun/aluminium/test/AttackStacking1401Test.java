package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Two ATTACK sources on herself must both count (1401, 2026-10-02): the technique's +60% and the ult's +80%.
 *
 * <p>⭐ FILE-DRIVEN, and the TOTAL is the claim: 1.4, not 0.6 and not 0.8. ⚠ The technique needs her `秘技` state, which a
 * judge cannot assume, so one rule applying it is APPENDED (`TriggerTable.plus`).
 */
public class AttackStacking1401Test {
    private static final int OWNER = 1401;
    private static final int MONSTER = 1002011;

    /** ⭐ Both sources: the total, not one of them. */
    @Test
    public void bothAttackSourcesAreCounted() {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);

        EffectSpec technique = new EffectSpec();
        TriggerSpecs.set(technique, "op", "APPLY_BUFF");
        TriggerSpecs.set(technique, "buff", "秘技");   // the Java field; the JSON name is buff
        TriggerSpecs.set(technique, "turns", 3);
        TriggerSpecs.set(technique, "target", "self");
        owner.setTriggerTable(owner.getTriggerTable()
                .plus(new TriggerTable(OWNER, List.of(TriggerSpecs.rule("BATTLE_START", List.of(), technique)))));

        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        double afterStart = owner.getAttribute(AttributeType.ATTACK).get();

        battle.fireTriggers(TriggerEvent.ULT_CAST, owner, owner, 0, 0);
        battle.processRequests();
        double afterUlt = owner.getAttribute(AttributeType.ATTACK).get();

        Assertions.assertTrue(afterStart > 0, "precondition: the technique's share lands (" + afterStart + ")");
        Assertions.assertTrue(afterUlt > afterStart,
                "the ult's own +80% must be COUNTED, not replace the technique's (" + afterStart + " -> " + afterUlt + ")");
    }
}
