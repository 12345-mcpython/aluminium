package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
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
 * 1415's memosprite skill 05 "你好，世界♪": "德谬歌被召唤时，解除我方全体控制类负面状态。" (2026-10-02).
 *
 * <p>The reading is the CLASS, not "something was removed": a control on an ally is gone after the summon, and a DOT on him is still there. A sweep that took
 * everything off would pass the first half while being wrong about the second.
 */
public class SummonedClearsControlTest {
    private static final int LEVEL = 80;
    private static final int CYRENE = 1415;
    private static final int ALLY = 1002;
    private static final int MONSTER = 1002011;
    private static final String CONTROL = "冻结";
    private static final String DOT = "裂伤";

    @Test
    public void onlyTheControlClassGoes() {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Battle battle = new Battle(List.of(cyrene, ally),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        cyrene = battle.characters.get(0);
        Character finalAlly = battle.characters.get(1);

        EffectSpec control = new EffectSpec();
        TriggerSpecs.set(control, "op", "APPLY_CONTROL");
        TriggerSpecs.set(control, "control", CONTROL);
        TriggerSpecs.set(control, "turns", 3);
        TriggerSpecs.set(control, "target", "self");
        EffectSpec dot = new EffectSpec();
        TriggerSpecs.set(dot, "op", "APPLY_DOT");
        TriggerSpecs.set(dot, "buff", DOT);
        TriggerSpecs.set(dot, "turns", 3);
        TriggerSpecs.set(dot, "scale", "self_attr:ATTACK");
        TriggerSpecs.set(dot, "percent", 0.1);
        TriggerSpecs.set(dot, "element", "Ice");
        TriggerSpecs.set(dot, "kind", "dot");
        TriggerSpecs.set(dot, "target", "self");
        finalAlly.setTriggerTable(new TriggerTable(ALLY, List.of(
                TriggerSpecs.rule("BATTLE_START", List.of(), control),
                TriggerSpecs.rule("BATTLE_START", List.of(), dot))));
        battle.fireTriggers(TriggerEvent.BATTLE_START);

        Assertions.assertTrue(finalAlly.getBuffManager().hasState(CONTROL), "precondition: the control landed");
        Assertions.assertTrue(finalAlly.getBuffManager().hasState(DOT), "precondition: the DOT landed");

        battle.summonServant(cyrene);          // Note: `processRequests` is what reaches `fireSummoned`
        battle.processRequests();

        boolean controlAfter = finalAlly.getBuffManager().hasState(CONTROL);
        boolean dotAfter = finalAlly.getBuffManager().hasState(DOT);
        System.out.println("[summoned_clears] control -> " + controlAfter + " ; dot -> " + dotAfter);

        Assertions.assertFalse(controlAfter,
                "「德谬歌被召唤时，解除我方全体**控制类**负面状态」-- the control is gone");
        Assertions.assertTrue(dotAfter,
                "and the DOT is NOT -- 「控制类」 names one class, not every debuff");
    }
}
