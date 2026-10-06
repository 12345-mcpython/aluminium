package com.laosun.aluminium.test.engine;


import com.laosun.aluminium.test.content.lightcones.GoodNightAndSleepWellTest;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Summon;
import com.laosun.aluminium.models.buff.ControlBuff;
import com.laosun.aluminium.models.buff.DotBuff;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.Skill;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1415 Cyrene (昔涟)'s memosprite the Demiurge (德谬歌), memosprite skill 8 "献予'纷争'之诗": "when cast on Mydei, dispel all <b>control-class</b> negative states Mydei is in".
 *
 * <p>Why this needed an engine piece: `DISPEL` removed the newest N debuffs and nothing else, so it could not say "control class". A class is a
 * property of the state itself (`AbstractBuff.debuffClass()`, also what class resistance reads), and the vocabulary already existed as a
 * CONDITION (`debuff_class:control`) and as `RESIST_DEBUFF`'s `"kind"`; this gives the op the same word.
 *
 * <p>The reading is discriminating because the victim carries BOTH kinds: a control and a damage-over-time. Only the control may go --
 * a mutant that names `dot` instead moves the removal to the other one and fails both assertions.
 */
public class DispelByClassTest {
    private static final int LEVEL = 80;
    private static final int CYRENE = 1415;
    private static final int MYDEI = 1404;
    private static final int MONSTER = 1002011;

    /** The control goes, the damage-over-time stays. */
    @Test
    public void onlyTheNamedClassIsDispelled() {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character mydei = CharacterFactory.create(MYDEI, LEVEL);
        Battle battle = new Battle(List.of(cyrene, mydei),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        Summon demiurge = battle.summonServant(cyrene);
        battle.processRequests();

        var control = Constant.CONTROL_EFFECTS.get("IMPRISONED");
        mydei.getBuffManager().addBuff(new ControlBuff(control, 3));
        mydei.getBuffManager().addBuff(new DotBuff(mydei, DamageElement.FIRE, 100, 3));
        // Note: a ControlBuff counts as TWO debuffs (measured; GoodNightAndSleepWellTest says so in the same words), so the pair is 3
        Assertions.assertEquals(3, mydei.getBuffManager().debuffCount(), "precondition: one control (2) and one dot (1)");

        Skill ode = demiurge.skillAt(16);
        Assertions.assertNotNull(ode, "precondition: the memosprite carries its own skill 16");
        SkillExecutor.execute(battle, ode, demiurge, List.of(mydei));
        battle.processRequests();

        int left = mydei.getBuffManager().debuffCount();
        int dots = mydei.getBuffManager().countBuffs(DotBuff.class);
        System.out.println("[dispel] after the ode: debuffs = " + left + " ; dots = " + dots);

        Assertions.assertEquals(1, left, "「dispel all **control-class** negative states Mydei is in」-- one of the two was not that class");
        Assertions.assertEquals(1, dots, "the damage-over-time is NOT control class, so it stays");
        // 2 - 1 = 1 and the survivor is the DOT, so the one that went was the control

    }
}
