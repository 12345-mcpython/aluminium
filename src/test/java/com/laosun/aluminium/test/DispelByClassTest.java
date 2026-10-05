package com.laosun.aluminium.test;

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
 * 1415 昔涟's memosprite 德谬歌, 忆灵技能 8 「献予「纷争」之诗」: 「对万敌施放时解除万敌陷入的所有<b>控制类</b>负面状态」 (2026-10-02).
 *
 * <p>⭐ Why this needed an engine piece: `DISPEL` removed the newest N debuffs and nothing else, so it could not say 「控制类」. A class is a
 * property of the state itself (`AbstractBuff.debuffClass()`, also what class resistance reads), and the vocabulary already existed as a
 * CONDITION (`debuff_class:control`) and as `RESIST_DEBUFF`'s `"kind"`; this gives the op the same word.
 *
 * <p>⭐ The reading is discriminating because the victim carries BOTH kinds: a control and a damage-over-time. Only the control may go --
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
        // \u26a0 a ControlBuff counts as TWO debuffs (measured; Cone21001Test says so in the same words), so the pair is 3
        Assertions.assertEquals(3, mydei.getBuffManager().debuffCount(), "precondition: one control (2) and one dot (1)");

        Skill ode = demiurge.skillAt(16);
        Assertions.assertNotNull(ode, "precondition: the memosprite carries its own skill 16");
        SkillExecutor.execute(battle, ode, demiurge, List.of(mydei));
        battle.processRequests();

        int left = mydei.getBuffManager().debuffCount();
        int dots = mydei.getBuffManager().countBuffs(DotBuff.class);
        System.out.println("[dispel] after the ode: debuffs = " + left + " ; dots = " + dots);

        Assertions.assertEquals(1, left, "\u300c\u89e3\u9664\u4e07\u654c\u9677\u5165\u7684\u6240\u6709**\u63a7\u5236\u7c7b**\u8d1f\u9762\u72b6\u6001\u300d-- one of the two was not that class");
        Assertions.assertEquals(1, dots, "the damage-over-time is NOT \u63a7\u5236\u7c7b, so it stays");
        // 2 - 1 = 1 and the survivor is the DOT, so the one that went was the control

    }
}
