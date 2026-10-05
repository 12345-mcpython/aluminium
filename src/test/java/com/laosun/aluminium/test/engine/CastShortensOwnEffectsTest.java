package com.laosun.aluminium.test.engine;


import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Summon;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1415's memosprite skill 03, fourth sentence: "德谬歌施放技能后使自身所有持续效果持续回合数减 1" (2026-10-02).
 *
 * <p>One scene, two halves (`self` in this file is the MASTER): an ally's rule puts a 2-turn mark on `all_allies`, the memosprite uses its skill, and one tick follows.
 * The master's mark went 2 -> 1 and that tick ends it; the memosprite's own is untouched and survives.
 *
 * <p>Note: A one-turn mark cannot discriminate: `extendDuration` is `Math.max(0, remaining + turns)` and a 1-turn buff ticks away on its own either way.
 */
public class CastShortensOwnEffectsTest {
    private static final int LEVEL = 80;
    private static final int CYRENE = 1415;
    private static final int ALLY = 1002;
    private static final int MONSTER = 1002011;
    private static final String MARK = "测试持续效果";

    @Test
    public void theMastersEffectShortensButTheMemospriteOwnDoesNot() {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Battle battle = new Battle(List.of(cyrene, ally),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        cyrene = battle.characters.get(0);
        ally = battle.characters.get(1);

        Summon demiurge = battle.summonServant(cyrene);
        battle.processRequests();
        Assertions.assertNotNull(demiurge, "precondition: the memosprite is out");

        EffectSpec mark = new EffectSpec();
        TriggerSpecs.set(mark, "op", "APPLY_BUFF");
        TriggerSpecs.set(mark, "buff", MARK);
        TriggerSpecs.set(mark, "turns", 2);
        TriggerSpecs.set(mark, "target", "all_allies");
        ally.setTriggerTable(new TriggerTable(ALLY, List.of(
                TriggerSpecs.rule("TURN_START", List.of(), mark))));
        battle.fireTriggers(TriggerEvent.TURN_START);
        Assertions.assertTrue(cyrene.getBuffManager().hasState(MARK), "precondition: the master wears the 2-turn mark");
        Assertions.assertTrue(demiurge.getBuffManager().hasState(MARK), "precondition: so does the memosprite");

        Assertions.assertNotNull(demiurge.skillAt(1), "precondition: slot 1 exists");
        SkillExecutor.execute(battle, demiurge.skillAt(1), demiurge, List.of(battle.enemies.getFirst()));
        battle.processRequests();

        cyrene.afterMove(battle);
        cyrene.getBuffManager().afterMove();
        demiurge.afterMove(battle);
        demiurge.getBuffManager().afterMove();
        boolean masterStill = cyrene.getBuffManager().hasState(MARK);
        boolean spriteStill = demiurge.getBuffManager().hasState(MARK);
        System.out.println("[shortens] after its skill and one tick: master still has the mark = " + masterStill
                + " ; the memosprite still has it = " + spriteStill);

        Assertions.assertFalse(masterStill,
                "「德谬歌施放技能后使自身所有持续效果持续回合数减 1」-- 2 became 1, so one tick ends it");
        Assertions.assertTrue(spriteStill,
                "and the MEMOSPRITE's own mark is untouched -- `self` in this file is the master");
    }
}
